package com.example.connector;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

/**
 * Custom Camunda 8 Connector for Eclipse Dataspace Components (EDC)
 * This connector handles the full EDC data consumption flow:
 * 1. Catalog Request
 * 2. Contract Negotiation
 * 3. Data Transfer
 * 4. Data Retrieval
 */
@OutboundConnector(
    name = "EDC Data Connector",
    inputVariables = {"edcManagementUrl", "edcApiKey", "providerUrl", "assetId", "authentication"},
    type = "io.camunda:edc-connector:1"
)
public class EdcDataConnector implements OutboundConnectorFunction {

    private static final Logger LOGGER = LoggerFactory.getLogger(EdcDataConnector.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;

    public EdcDataConnector() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    @Override
    public Object execute(OutboundConnectorContext context) throws Exception {
        var input = context.bindVariables(EdcConnectorInput.class);
        
        LOGGER.info("Starting EDC data retrieval for asset: {}", input.getAssetId());
        
        try {
            // Step 1: Request catalog from provider
            String catalogResponse = requestCatalog(input);
            LOGGER.debug("Catalog response: {}", catalogResponse);
            
            // Step 2: Find the specific dataset in the catalog
            Map<String, Object> dataset = findDatasetInCatalog(catalogResponse, input.getAssetId());
            if (dataset == null) {
                throw new RuntimeException("Asset " + input.getAssetId() + " not found in catalog");
            }
            
            // Step 3: Initiate contract negotiation
            String negotiationId = initiateContractNegotiation(input, dataset);
            LOGGER.info("Contract negotiation initiated: {}", negotiationId);
            
            // Step 4: Wait for negotiation to complete
            String agreementId = waitForNegotiationCompletion(input, negotiationId);
            LOGGER.info("Contract agreement reached: {}", agreementId);
            
            // Step 5: Initiate data transfer
            String transferProcessId = initiateTransfer(input, agreementId);
            LOGGER.info("Transfer process initiated: {}", transferProcessId);
            
            // Step 6: Wait for transfer to complete and get data
            String data = waitForTransferAndGetData(input, transferProcessId);
            LOGGER.info("Data retrieved successfully");
            
            // Return result
            EdcConnectorResult result = new EdcConnectorResult();
            result.setAssetId(input.getAssetId());
            result.setAgreementId(agreementId);
            result.setTransferProcessId(transferProcessId);
            result.setData(data);
            result.setSuccess(true);
            
            return result;
            
        } catch (Exception e) {
            LOGGER.error("Error executing EDC connector", e);
            EdcConnectorResult errorResult = new EdcConnectorResult();
            errorResult.setSuccess(false);
            errorResult.setErrorMessage(e.getMessage());
            return errorResult;
        }
    }

    /**
     * Request the catalog from the provider connector
     */
    private String requestCatalog(EdcConnectorInput input) throws Exception {
        String endpoint = input.getEdcManagementUrl() + "/v3/catalog/request";
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("@context", Map.of("@vocab", "https://w3id.org/edc/v0.0.1/ns/"));
        requestBody.put("counterPartyAddress", input.getProviderUrl());
        requestBody.put("protocol", "dataspace-protocol-http");
        
        String jsonBody = objectMapper.writeValueAsString(requestBody);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Content-Type", "application/json")
            .header("X-Api-Key", input.getEdcApiKey())
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();
            
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("Catalog request failed: " + response.statusCode() + " - " + response.body());
        }
        
        return response.body();
    }

    /**
     * Find a specific dataset in the catalog response
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> findDatasetInCatalog(String catalogJson, String assetId) throws Exception {
        Map<String, Object> catalog = objectMapper.readValue(catalogJson, Map.class);
        
        List<Map<String, Object>> datasets = (List<Map<String, Object>>) catalog.get("dcat:dataset");
        if (datasets == null || datasets.isEmpty()) {
            return null;
        }
        
        for (Map<String, Object> dataset : datasets) {
            String datasetId = (String) dataset.get("@id");
            if (assetId.equals(datasetId)) {
                return dataset;
            }
        }
        
        return null;
    }

    /**
     * Initiate contract negotiation
     */
    @SuppressWarnings("unchecked")
    private String initiateContractNegotiation(EdcConnectorInput input, Map<String, Object> dataset) throws Exception {
        String endpoint = input.getEdcManagementUrl() + "/v3/contractnegotiations";
        
        // Extract offer from dataset
        Map<String, Object> offer = (Map<String, Object>) dataset.get("odrl:hasPolicy");
        String offerId = (String) offer.get("@id");
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("@context", Map.of("@vocab", "https://w3id.org/edc/v0.0.1/ns/"));
        requestBody.put("counterPartyAddress", input.getProviderUrl());
        requestBody.put("protocol", "dataspace-protocol-http");
        
        Map<String, Object> offerMap = new HashMap<>();
        offerMap.put("offerId", offerId);
        offerMap.put("assetId", input.getAssetId());
        offerMap.put("policy", offer);
        requestBody.put("offer", offerMap);
        
        String jsonBody = objectMapper.writeValueAsString(requestBody);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Content-Type", "application/json")
            .header("X-Api-Key", input.getEdcApiKey())
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();
            
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("Contract negotiation failed: " + response.statusCode() + " - " + response.body());
        }
        
        Map<String, Object> responseBody = objectMapper.readValue(response.body(), Map.class);
        return (String) responseBody.get("@id");
    }

    /**
     * Wait for contract negotiation to complete
     */
    @SuppressWarnings("unchecked")
    private String waitForNegotiationCompletion(EdcConnectorInput input, String negotiationId) throws Exception {
        String endpoint = input.getEdcManagementUrl() + "/v3/contractnegotiations/" + negotiationId;
        
        int maxAttempts = 30;
        int attemptCount = 0;
        
        while (attemptCount < maxAttempts) {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("X-Api-Key", input.getEdcApiKey())
                .GET()
                .build();
                
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                Map<String, Object> negotiation = objectMapper.readValue(response.body(), Map.class);
                String state = (String) negotiation.get("state");
                
                LOGGER.debug("Negotiation state: {}", state);
                
                if ("FINALIZED".equals(state)) {
                    return (String) negotiation.get("contractAgreementId");
                } else if ("TERMINATED".equals(state)) {
                    throw new RuntimeException("Contract negotiation terminated");
                }
            }
            
            Thread.sleep(2000); // Wait 2 seconds before next attempt
            attemptCount++;
        }
        
        throw new RuntimeException("Contract negotiation timeout");
    }

    /**
     * Initiate data transfer
     */
    private String initiateTransfer(EdcConnectorInput input, String agreementId) throws Exception {
        String endpoint = input.getEdcManagementUrl() + "/v3/transferprocesses";
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("@context", Map.of("@vocab", "https://w3id.org/edc/v0.0.1/ns/"));
        requestBody.put("counterPartyAddress", input.getProviderUrl());
        requestBody.put("contractId", agreementId);
        requestBody.put("assetId", input.getAssetId());
        requestBody.put("protocol", "dataspace-protocol-http");
        
        // Configure data destination (HTTP pull)
        Map<String, Object> dataDestination = new HashMap<>();
        dataDestination.put("type", "HttpProxy");
        requestBody.put("dataDestination", dataDestination);
        
        String jsonBody = objectMapper.writeValueAsString(requestBody);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Content-Type", "application/json")
            .header("X-Api-Key", input.getEdcApiKey())
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();
            
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("Transfer initiation failed: " + response.statusCode() + " - " + response.body());
        }
        
        Map<String, Object> responseBody = objectMapper.readValue(response.body(), Map.class);
        return (String) responseBody.get("@id");
    }

    /**
     * Wait for transfer to complete and retrieve data
     */
    @SuppressWarnings("unchecked")
    private String waitForTransferAndGetData(EdcConnectorInput input, String transferProcessId) throws Exception {
        String endpoint = input.getEdcManagementUrl() + "/v3/transferprocesses/" + transferProcessId;
        
        int maxAttempts = 30;
        int attemptCount = 0;
        String dataPlaneUrl = null;
        String authToken = null;
        
        // Wait for transfer to reach STARTED state
        while (attemptCount < maxAttempts) {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("X-Api-Key", input.getEdcApiKey())
                .GET()
                .build();
                
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                Map<String, Object> transferProcess = objectMapper.readValue(response.body(), Map.class);
                String state = (String) transferProcess.get("state");
                
                LOGGER.debug("Transfer state: {}", state);
                
                if ("STARTED".equals(state)) {
                    // Extract data plane URL and auth token
                    Map<String, Object> dataAddress = (Map<String, Object>) transferProcess.get("dataAddress");
                    if (dataAddress != null) {
                        dataPlaneUrl = (String) dataAddress.get("endpoint");
                        authToken = (String) dataAddress.get("authorization");
                    }
                    break;
                } else if ("TERMINATED".equals(state)) {
                    throw new RuntimeException("Transfer process terminated");
                }
            }
            
            Thread.sleep(2000);
            attemptCount++;
        }
        
        if (dataPlaneUrl == null) {
            throw new RuntimeException("Transfer timeout or no data plane URL received");
        }
        
        // Now fetch the actual data from the data plane
        HttpRequest dataRequest = HttpRequest.newBuilder()
            .uri(URI.create(dataPlaneUrl))
            .header("Authorization", authToken)
            .GET()
            .build();
            
        HttpResponse<String> dataResponse = httpClient.send(dataRequest, HttpResponse.BodyHandlers.ofString());
        
        if (dataResponse.statusCode() != 200) {
            throw new RuntimeException("Data retrieval failed: " + dataResponse.statusCode());
        }
        
        return dataResponse.body();
    }
}
