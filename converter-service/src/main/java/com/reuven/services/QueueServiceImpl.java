package com.reuven.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

@Service
public class QueueServiceImpl implements QueueService {

    private static final Logger logger = LogManager.getLogger(QueueServiceImpl.class);
    private static final BlockingDeque<BigDecimal> blockingDeque = new LinkedBlockingDeque<>();
    private static final String LOCAL_HOST = "http://localhost:";
    private final RestClient restClient;
    private final Integer aggServerPort;
    private final String aggUrl;

    public QueueServiceImpl(RestClient restClient,
                            @Value("${aggregation.server.port}") Integer aggServerPort,
                            @Value("${aggregation.url}") String aggUrl) {
        this.restClient = restClient;
        this.aggServerPort = aggServerPort;
        this.aggUrl = aggUrl;
    }


    @Override
    public void sendAll() {
        logger.info("try to sendAll queue values to aggregation service");
        while (!blockingDeque.isEmpty()) {
            logger.info("sendAll queue values to aggregation service");
            BigDecimal value = blockingDeque.poll();
            try {
                restClient.get()
                        .uri(LOCAL_HOST + aggServerPort + aggUrl + value)
//                        .retrieve()
//                        .body(Void.class)
                        ;
            } catch (Exception e) {
                logger.error("failed to sent value '{}' to aggregation server", value, e);
            }
        }
    }

    @Override
    public void put(BigDecimal bigDecimal) {
        try {
            blockingDeque.put(bigDecimal);
        } catch (InterruptedException e) {
            throw new RuntimeException(String.format("failed to insert value %s %s", bigDecimal, e));
        }
    }
}
