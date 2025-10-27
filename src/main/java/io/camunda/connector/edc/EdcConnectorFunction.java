package io.camunda.connector.edc;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.edc.model.EdcConnectorRequest;
import io.camunda.connector.edc.model.EdcConnectorResponse;
import io.camunda.connector.edc.service.EdcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EDC Connector for Camunda 8.8
 * 
 * This connector integrates with Eclipse Dataspace Components (EDC) to:
 * 1. Query a catalog
 * 2. Negotiate a contract for a specific asset
 * 3. Initiate a data transfer
 * 4. Retrieve the transferred data
 */
@OutboundConnector(
    name = "EDC Connector",
    inputVariables = {"edcManagementUrl", "assetId", "providerUrl", "authentication"},
    type = "io.camunda:edc-connector:1"
)
public class EdcConnectorFunction implements OutboundConnectorFunction {

    private static final Logger LOGGER = LoggerFactory.getLogger(EdcConnectorFunction.class);
    
    private final EdcService edcService;

    public EdcConnectorFunction() {
        this.edcService = new EdcService();
    }

    // Constructor for testing
    public EdcConnectorFunction(EdcService edcService) {
        this.edcService = edcService;
    }

    @Override
    public Object execute(OutboundConnectorContext context) throws Exception {
        LOGGER.info("Starting EDC Connector execution");
        
        // Bind input variables to request object
        final EdcConnectorRequest request = context.bindVariables(EdcConnectorRequest.class);
        
        LOGGER.info("EDC Connector request: Management URL={}, Asset ID={}, Provider URL={}", 
            request.getEdcManagementUrl(), request.getAssetId(), request.getProviderUrl());
        
        // Validate request
        request.validate();
        
        try {
            // Execute the EDC workflow
            EdcConnectorResponse response = edcService.executeEdcWorkflow(request);
            
            LOGGER.info("EDC Connector execution completed successfully. Transfer ID: {}", 
                response.getTransferId());
            
            return response;
            
        } catch (Exception e) {
            LOGGER.error("Error executing EDC connector", e);
            throw new RuntimeException("Failed to retrieve data from EDC: " + e.getMessage(), e);
        }
    }
}
