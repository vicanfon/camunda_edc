package io.camunda.connector.edc.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.edc.model.EdcConnectorRequest;
import io.camunda.connector.edc.model.EdcConnectorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service class that handles EDC operations
 */
public class EdcService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EdcService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;

    public EdcService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Execute the complete EDC workflow:
     * 1. Query catalog for the asset
     * 2. Negotiate contract
     * 3. Initiate transfer
     * 4. Retrieve data
     */
    public EdcConnectorResponse executeEdcWorkflow(EdcConnectorRequest request) throws Exception {
        LOGGER.info("Starting EDC workflow for asset: {}", request.getAssetId());

        // Step 1: Query catalog to find the asset and get offer
        LOGGER.info("Step 1: Querying catalog...");
        JsonNode catalogEntry = queryCatalog(request);
        
        // Step 2: Negotiate contract
        LOGGER.info("Step 2: Negotiating contract...");
        String contractAgreementId = negotiateContract(request, catalogEntry);
        
        // Step 3: Initiate data transfer
        LOGGER.info("Step 3: Initiating data transfer...");
        String transferId = initiateTransfer(request, contractAgreementId);
        
        // Step 4: Wait for transfer completion and retrieve data
        LOGGER.info("Step 4: Waiting for transfer completion...");
        Object data = retrieveTransferData(request, transferId);

        // Build response
        EdcConnectorResponse response = new EdcConnectorResponse(
                request.getAssetId(),
                contractAgreementId,
                transferId,
                data
        );
        
        LOGGER.info("EDC workflow completed successfully");
        return response;
    }

    /**
     * Query the EDC catalog for a specific asset
     */
    private JsonNode queryCatalog(EdcConnectorRequest request) throws Exception {
        String catalogUrl = request.getEdcManagementUrl() + "/v3/catalog/request";
        String counterPartyAddress = request.getProviderUrl() + "/api/dsp";
        String counterPartyId = "did:web:provider-identityhub%3A7083:provider";

        LOGGER.info("Querying catalog at: {}", catalogUrl);
        LOGGER.info("Provider DSP endpoint (counterPartyAddress): {}", counterPartyAddress);

        // Build catalog request body
        Map<String, Object> catalogRequest = new HashMap<>();
        catalogRequest.put("@context", Map.of("@vocab", "https://w3id.org/edc/v0.0.1/ns/"));
        catalogRequest.put("counterPartyAddress", counterPartyAddress);
        catalogRequest.put("counterPartyId", counterPartyId);
        catalogRequest.put("protocol", "dataspace-protocol-http");
        
        // Add query filter for specific asset if needed
        Map<String, Object> querySpec = new HashMap<>();
        querySpec.put("filterExpression", List.of(Map.of(
                "operandLeft", "https://w3id.org/edc/v0.0.1/ns/id",
                "operator", "=",
                "operandRight", request.getAssetId()
        )));
        catalogRequest.put("querySpec", querySpec);

        String requestBody = objectMapper.writeValueAsString(catalogRequest);
        
        HttpRequest httpRequest = buildRequest(
                catalogUrl,
                "POST",
                requestBody,
                request.getAuthentication()
        );

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            String errorMsg = String.format(
                "Failed to query catalog. Status: %d, Body: %s%n" +
                "Configuration used:%n" +
                "  - EDC Management URL: %s%n" +
                "  - Catalog Endpoint: %s%n" +
                "  - Provider DSP Address: %s%n" +
                "Please verify:%n" +
                "  1. EDC Management URL should end with /management (e.g., http://localhost:9193/management)%n" +
                "  2. Provider URL should be base URL only (e.g., http://provider:8080)%n" +
                "  3. EDC connector version is 0.5.0 or higher with DSP support%n" +
                "See TROUBLESHOOTING.md for more details.",
                response.statusCode(),
                response.body(),
                request.getEdcManagementUrl(),
                catalogUrl,
                counterPartyAddress
            );
            throw new RuntimeException(errorMsg);
        }

        JsonNode catalogResponse = objectMapper.readTree(response.body());
        
        // Extract the datasets array
        JsonNode datasets = catalogResponse.get("dcat:dataset");
        if (datasets == null || !datasets.isArray() || datasets.isEmpty()) {
            throw new RuntimeException("Asset not found in catalog: " + request.getAssetId());
        }

        // Find the specific asset
        for (JsonNode dataset : datasets) {
            if (matchesAsset(dataset, request.getAssetId())) {
                LOGGER.info("Found asset in catalog: {}", request.getAssetId());
                return dataset;
            }
        }

        StringBuilder availableAssets = new StringBuilder();
        for (JsonNode dataset : datasets) {
            JsonNode rawId = dataset.get("@id");
            if (rawId != null) {
                availableAssets.append(rawId.asText()).append(", ");
            }
            JsonNode edcId = dataset.get("https://w3id.org/edc/v0.0.1/ns/id");
            if (edcId != null) {
                availableAssets.append(edcId.asText()).append(", ");
            }
        }

        String available = availableAssets.length() > 0
                ? availableAssets.substring(0, availableAssets.length() - 2)
                : "<none>";

        throw new RuntimeException("Asset not found in catalog: " + request.getAssetId()
                + ". Available assets: " + available);
    }

    private boolean matchesAsset(JsonNode dataset, String assetId) {
        if (dataset == null || assetId == null) {
            return false;
        }

        if (matchesValue(dataset.get("@id"), assetId)) {
            return true;
        }

        if (matchesValue(dataset.get("https://w3id.org/edc/v0.0.1/ns/id"), assetId)) {
            return true;
        }

        if (matchesValue(dataset.get("https://w3id.org/edc/v0.0.1/ns/assetId"), assetId)) {
            return true;
        }

        if (matchesValue(dataset.get("dct:identifier"), assetId)) {
            return true;
        }

        JsonNode policies = dataset.get("odrl:hasPolicy");
        if (policies != null) {
            if (policies.isArray()) {
                for (JsonNode policy : policies) {
                    if (assetMatchesPolicy(policy, assetId)) {
                        return true;
                    }
                }
            } else if (assetMatchesPolicy(policies, assetId)) {
                return true;
            }
        }

        return false;
    }

    private boolean assetMatchesPolicy(JsonNode policy, String assetId) {
        if (policy == null || assetId == null) {
            return false;
        }

        if (matchesValue(policy.get("odrl:target"), assetId)) {
            return true;
        }

        if (matchesValue(policy.get("https://w3id.org/edc/v0.0.1/ns/target"), assetId)) {
            return true;
        }

        JsonNode constraints = policy.get("odrl:constraint");
        if (constraints != null) {
            if (constraints.isArray()) {
                for (JsonNode constraint : constraints) {
                    if (constraintMatches(constraint, assetId)) {
                        return true;
                    }
                }
            } else if (constraintMatches(constraints, assetId)) {
                return true;
            }
        }

        return false;
    }

    private boolean constraintMatches(JsonNode constraint, String assetId) {
        if (constraint == null) {
            return false;
        }

        JsonNode leftOperand = constraint.get("odrl:leftOperand");
        if (!matchesValue(leftOperand, "https://w3id.org/edc/v0.0.1/ns/id")
                && !matchesValue(leftOperand, "https://w3id.org/edc/v0.0.1/ns/assetId")) {
            return false;
        }

        return matchesValue(constraint.get("odrl:rightOperand"), assetId)
                || matchesValue(constraint.get("https://w3id.org/edc/v0.0.1/ns/rightOperand"), assetId);
    }

    private boolean matchesValue(JsonNode node, String expected) {
        if (node == null || expected == null) {
            return false;
        }

        if (node.isTextual()) {
            return expected.equals(node.asText());
        }

        if (node.isNumber()) {
            return expected.equals(node.asText());
        }

        if (node.isObject()) {
            JsonNode idNode = node.get("@id");
            if (matchesValue(idNode, expected)) {
                return true;
            }

            for (JsonNode value : node) {
                if (matchesValue(value, expected)) {
                    return true;
                }
            }
        }

        if (node.isArray()) {
            for (JsonNode value : node) {
                if (matchesValue(value, expected)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Negotiate a contract for the asset
     */
    private String negotiateContract(EdcConnectorRequest request, JsonNode catalogEntry) throws Exception {
        String negotiationUrl = request.getEdcManagementUrl() + "/v3/contractnegotiations";
        
        // Extract offer from catalog entry
        JsonNode offers = catalogEntry.get("odrl:hasPolicy");
        if (offers == null || !offers.isArray() || offers.isEmpty()) {
            throw new RuntimeException("No offers found for asset: " + request.getAssetId());
        }

        JsonNode offer = offers.get(0);
        
        // Build negotiation request
        Map<String, Object> negotiationRequest = new HashMap<>();
        negotiationRequest.put("@context", Map.of("@vocab", "https://w3id.org/edc/v0.0.1/ns/"));
        negotiationRequest.put("counterPartyAddress", request.getProviderUrl() + "/api/dsp");
        negotiationRequest.put("protocol", "dataspace-protocol-http");
        
        Map<String, Object> offerMap = new HashMap<>();
        offerMap.put("offerId", offer.get("@id").asText());
        offerMap.put("assetId", request.getAssetId());
        offerMap.put("policy", offer);
        
        negotiationRequest.put("offer", offerMap);

        String requestBody = objectMapper.writeValueAsString(negotiationRequest);
        
        HttpRequest httpRequest = buildRequest(
                negotiationUrl,
                "POST",
                requestBody,
                request.getAuthentication()
        );

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw new RuntimeException("Failed to initiate contract negotiation. Status: " + 
                    response.statusCode() + ", Body: " + response.body());
        }

        JsonNode negotiationResponse = objectMapper.readTree(response.body());
        String negotiationId = negotiationResponse.get("@id").asText();
        
        LOGGER.info("Contract negotiation initiated: {}", negotiationId);

        // Wait for negotiation to complete
        String contractAgreementId = waitForNegotiation(request, negotiationId);
        
        LOGGER.info("Contract negotiation completed. Agreement ID: {}", contractAgreementId);
        return contractAgreementId;
    }

    /**
     * Wait for contract negotiation to reach FINALIZED state
     */
    private String waitForNegotiation(EdcConnectorRequest request, String negotiationId) throws Exception {
        String negotiationStateUrl = request.getEdcManagementUrl() + "/v3/contractnegotiations/" + negotiationId;
        
        int maxAttempts = request.getTimeout();
        int attempt = 0;
        
        while (attempt < maxAttempts) {
            Thread.sleep(1000); // Wait 1 second between checks
            
            HttpRequest httpRequest = buildRequest(
                    negotiationStateUrl,
                    "GET",
                    null,
                    request.getAuthentication()
            );

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode negotiation = objectMapper.readTree(response.body());
                String state = negotiation.get("state").asText();
                
                LOGGER.debug("Negotiation state: {}", state);
                
                if ("FINALIZED".equals(state)) {
                    return negotiation.get("contractAgreementId").asText();
                } else if ("TERMINATED".equals(state) || "ERROR".equals(state)) {
                    throw new RuntimeException("Contract negotiation failed with state: " + state);
                }
            }
            
            attempt++;
        }
        
        throw new RuntimeException("Contract negotiation timeout after " + maxAttempts + " seconds");
    }

    /**
     * Initiate a data transfer
     */
    private String initiateTransfer(EdcConnectorRequest request, String contractAgreementId) throws Exception {
        String transferUrl = request.getEdcManagementUrl() + "/v3/transferprocesses";
        
        // Build transfer request
        Map<String, Object> transferRequest = new HashMap<>();
        transferRequest.put("@context", Map.of("@vocab", "https://w3id.org/edc/v0.0.1/ns/"));
        transferRequest.put("counterPartyAddress", request.getProviderUrl() + "/api/dsp");
        transferRequest.put("contractId", contractAgreementId);
        transferRequest.put("assetId", request.getAssetId());
        transferRequest.put("protocol", "dataspace-protocol-http");
        
        // Configure data destination (HTTP pull)
        Map<String, Object> dataDestination = new HashMap<>();
        dataDestination.put("type", "HttpProxy");
        transferRequest.put("dataDestination", dataDestination);

        String requestBody = objectMapper.writeValueAsString(transferRequest);
        
        HttpRequest httpRequest = buildRequest(
                transferUrl,
                "POST",
                requestBody,
                request.getAuthentication()
        );

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw new RuntimeException("Failed to initiate transfer. Status: " + 
                    response.statusCode() + ", Body: " + response.body());
        }

        JsonNode transferResponse = objectMapper.readTree(response.body());
        String transferId = transferResponse.get("@id").asText();
        
        LOGGER.info("Transfer initiated: {}", transferId);
        return transferId;
    }

    /**
     * Retrieve data from completed transfer
     */
    private Object retrieveTransferData(EdcConnectorRequest request, String transferId) throws Exception {
        String transferStateUrl = request.getEdcManagementUrl() + "/v3/transferprocesses/" + transferId;
        
        int maxAttempts = request.getTimeout();
        int attempt = 0;
        
        while (attempt < maxAttempts) {
            Thread.sleep(1000); // Wait 1 second between checks
            
            HttpRequest httpRequest = buildRequest(
                    transferStateUrl,
                    "GET",
                    null,
                    request.getAuthentication()
            );

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode transfer = objectMapper.readTree(response.body());
                String state = transfer.get("state").asText();
                
                LOGGER.debug("Transfer state: {}", state);
                
                if ("STARTED".equals(state) || "COMPLETED".equals(state)) {
                    // Get the EDR (Endpoint Data Reference)
                    JsonNode dataAddress = transfer.get("dataAddress");
                    if (dataAddress != null) {
                        String endpoint = dataAddress.get("endpoint").asText();
                        String authCode = dataAddress.get("authorization").asText();
                        
                        // Retrieve actual data from the endpoint
                        return fetchDataFromEndpoint(endpoint, authCode);
                    }
                } else if ("TERMINATED".equals(state) || "ERROR".equals(state)) {
                    throw new RuntimeException("Transfer failed with state: " + state);
                }
            }
            
            attempt++;
        }
        
        throw new RuntimeException("Transfer timeout after " + maxAttempts + " seconds");
    }

    /**
     * Fetch actual data from the provider's data endpoint
     */
    private Object fetchDataFromEndpoint(String endpoint, String authCode) throws Exception {
        LOGGER.info("Fetching data from endpoint: {}", endpoint);
        
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Authorization", authCode)
                .header("Content-Type", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to fetch data from endpoint. Status: " + 
                    response.statusCode() + ", Body: " + response.body());
        }

        // Try to parse as JSON, otherwise return as string
        try {
            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            return response.body();
        }
    }

    /**
     * Build HTTP request with authentication
     */
    private HttpRequest buildRequest(String url, String method, String body, 
                                     EdcConnectorRequest.Authentication auth) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30));

        // Add authentication
        if (auth != null) {
            if ("api-key".equals(auth.getType())) {
                builder.header("X-Api-Key", auth.getApiKey());
            } else if ("basic".equals(auth.getType())) {
                String credentials = auth.getUsername() + ":" + auth.getPassword();
                String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());
                builder.header("Authorization", "Basic " + encodedCredentials);
            }
        }

        // Set method and body
        if ("POST".equals(method)) {
            builder.POST(HttpRequest.BodyPublishers.ofString(body));
        } else if ("GET".equals(method)) {
            builder.GET();
        }

        return builder.build();
    }
}
