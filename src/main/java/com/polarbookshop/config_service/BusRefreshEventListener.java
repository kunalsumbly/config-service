package com.polarbookshop.config_service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;



@Component
public class BusRefreshEventListener {

    private static final Logger logger = LoggerFactory.getLogger(BusRefreshEventListener.class);
    private final RestTemplate restTemplate;
    private final int serverPort;
    private final String serverHost;

    public BusRefreshEventListener(
            @Value("${server.port:8888}") int serverPort,
            @Value("${server.host:localhost}") String serverHost) {
        this.restTemplate = new RestTemplate();
        this.serverPort = serverPort;
        this.serverHost = serverHost;
        logger.debug("BusRefreshEventListener initialized with server host: {}, port: {}", serverHost, serverPort);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("Application is ready. Triggering bus refresh event...");
        try {
            // The URL for the busrefresh endpoint
            String busRefreshUrl = "http://" + serverHost + ":" + serverPort + "/actuator/busrefresh";
            logger.debug("Calling busrefresh endpoint at: {}", busRefreshUrl);
            // Set up the request headers and body
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json"); // Add Content-Type header for JSON
            HttpEntity<String> requestEntity = new HttpEntity<>("{}", headers);

            // Make a POST request to the busrefresh endpoint
            ResponseEntity<String> response = restTemplate.postForEntity(busRefreshUrl, requestEntity, String.class);

            // Log the response
            logger.info("Bus refresh event triggered. Response status: {}, body: {}", 
                    response.getStatusCode(), response.getBody());
        } catch (Exception e) {
            logger.error("Error triggering bus refresh event", e);
        }
    }
}
