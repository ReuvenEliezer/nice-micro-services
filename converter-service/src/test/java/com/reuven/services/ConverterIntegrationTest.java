package com.reuven.services;

import com.reuven.utils.WsAddressConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.junit.jupiter.EnabledIf;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

//@ActiveProfiles(profiles = "integration-tests") //https://stackoverflow.com/questions/44055969/in-spring-what-is-the-difference-between-profile-and-activeprofiles
@EnabledIf(value = "#{environment.getActiveProfiles()[0] == 'integration-tests'}", loadContext = true)
//@Disabled
@SpringBootTest
class ConverterIntegrationTest {

    private static final Logger logger = LogManager.getLogger(ConverterIntegrationTest.class);

    private static final String localhost = "http://localhost:";
    private static final String stringType = "string";
    private static final String hexType = "hex";
    private static final String fractionType = "fraction";
    private static final String GATEWAY_PORT = "8080";
    private static final String GATEWAY_URI = localhost + GATEWAY_PORT;

    @Autowired
    private RestClient restClient;

    @Autowired
    private Environment environment;

    @Value("${spring.application.name}")
    private String appName;

    @Value("${server.port}")
//    @LocalServerPort
    private int serverPort;

    @Value("${aggregation.server.port}")
    private int aggServerPort;


    @Test
    @Disabled
    void zipkinTest() {
        String[] res = restClient.get().uri(localhost + "9411/zipkin/api/v2/services").retrieve().body(String[].class);
        logger.info("zipkin services: '{}'", Arrays.toString(res));
        assertThat(res).isNotEmpty();
        assertThat(res).containsExactlyInAnyOrder(
//                appName,
                "aggregation-service");
    }

    @Test
    void callAggregateServiceTest() {
        logger.info("callAggregateServiceTest");
        String[] activeProfiles = environment.getActiveProfiles();
        for (String activeProfile : activeProfiles) {
            logger.info("activeProfile: {}", activeProfile);
        }
        BigDecimal result = restClient
                .get()
//                .uri(localhost + serverPort + WsAddressConstants.convertLogicUrl + "call-aggregate-service")
                .uri(GATEWAY_URI + WsAddressConstants.convertLogicUrl + "call-aggregate-service") // call via gateway
                .retrieve()
                .body(BigDecimal.class);
        assertThat(result).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }


    @Test
    void callAggregateServiceWithValueTest() throws InterruptedException {
        logger.info("callAggregateServiceWithValueTest");
        String[] activeProfiles = environment.getActiveProfiles();
        for (String activeProfile : activeProfiles) {
            logger.info("activeProfile: {}", activeProfile);
        }

        BigDecimal value = new BigDecimal(5);
        restClient
                .get()
                .uri(GATEWAY_URI + "/aggregate/" + value)  // call via gateway
//                .uri(localhost + aggServerPort + "/aggregate/" + value)
                .retrieve()
                .body(Void.class);

        Duration sleepTimeDuration = Duration.ofSeconds(3);
        Duration maxTimeToTrying = Duration.ofMinutes(1);
        LocalDateTime startTime = LocalDateTime.now();
        BigDecimal result = null;
        do {
            try {
                result = restClient
                        .get()
//                .uri(localhost + serverPort + WsAddressConstants.convertLogicUrl + "call-aggregate-service")
                        .uri(GATEWAY_URI + WsAddressConstants.convertLogicUrl + "call-aggregate-service")  // call via gateway
                        .retrieve()
                        .body(BigDecimal.class);
            } catch (HttpServerErrorException e) {
                logger.error("failed to execute {} {}", GATEWAY_URI + WsAddressConstants.convertLogicUrl + "call-aggregate-service", e.getMessage());
            }

            logger.info("callAggregateServiceWithValueTest: '{}'", result);
            Thread.sleep(sleepTimeDuration.toMillis());
//        } while (startTime.plus(maxTimeToTrying).isAfter(LocalDateTime.now()) && Objects.equals(result, BigDecimal.ZERO));
// only in case of using one aggregation-service instance, otherwise - we don't have verifying that request sent to the same instance
        } while (startTime.plus(maxTimeToTrying).isAfter(LocalDateTime.now()) && result == null);
//    assertThat(result).isGreaterThanOrEqualTo(value);
        assertThat(result).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    @Test
    void healthByActuatorTest() {
        String res = restClient.
                get()
//                .uri(localhost + serverPort + "/actuator/health")
                .uri(GATEWAY_URI + "/actuator/health")  // call via gateway
                .retrieve()
                .body(String.class);
        assertThat(res).isEqualTo("{\"status\":\"UP\"}");
    }

    @ParameterizedTest()
    @MethodSource({"convertArgumentsProvider"})
    void convertTest(String input, String convertType, int expected) {
        BigDecimal bigDecimal = restClient
                .post()
//                .uri(localhost + serverPort + WsAddressConstants.convertLogicUrl + convertType)
                .uri(GATEWAY_URI + WsAddressConstants.convertLogicUrl + convertType)  // call via gateway
                .body(input)
                .retrieve()
                .body(BigDecimal.class);
        assert bigDecimal != null;
        assertEquals(expected, bigDecimal.intValue());
//        sleep(7000);
        // TODO check in the output of aggregation service - the accumulation value by reading writer type
    }


    @ParameterizedTest()
    @MethodSource({"negativeArgumentsProvider"})
    void negativeTest(String input, String convertType) {
        assertThrows(HttpServerErrorException.InternalServerError.class, () ->
                restClient.post()
                        .uri(GATEWAY_URI + WsAddressConstants.convertLogicUrl + convertType)  // call via gateway
//                        .uri(localhost + serverPort + WsAddressConstants.convertLogicUrl + convertType)
                        .body(input)
                        .retrieve()
                        .body(BigDecimal.class));
    }

    private static Stream<Arguments> convertArgumentsProvider() {
        return Stream.of(
                Arguments.of("F", hexType, 15),
                Arguments.of("abc", stringType, 294),
                Arguments.of("6/3", fractionType, 2)
        );
    }

    private static Stream<Arguments> negativeArgumentsProvider() {
        return Stream.of(
                Arguments.of("-3/6", fractionType),
                Arguments.of("0/1", fractionType),
                Arguments.of("3/0", fractionType),
                Arguments.of("$", hexType),
                Arguments.of("3", stringType),
                Arguments.of("a", fractionType),
                Arguments.of("12/1/1", fractionType)
        );
    }


}
