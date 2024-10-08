package com.reuven.config;

//import brave.baggage.BaggageField;
//import brave.baggage.CorrelationScopeConfig;
//import brave.context.slf4j.MDCScopeDecorator;
//import brave.propagation.CurrentTraceContext;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class WebRestConfig {

    @Bean
    @LoadBalanced
    public RestClient restClient() {
        return RestClient.create();
    }

    // tag::aspect[]
    // To have the @Observed support we need to register this aspect
//    @Bean
//    ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
//        return new ObservedAspect(observationRegistry);
//    }
    // end::aspect[]

}