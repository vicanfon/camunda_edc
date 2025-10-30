package io.camunda.connector.edc;

import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.edc.model.EdcConnectorRequest;
import io.camunda.connector.edc.model.EdcConnectorResponse;
import io.camunda.connector.edc.service.EdcService;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for EDC Connector
 */
class EdcConnectorFunctionTest {

    private EdcService mockEdcService;
    private EdcConnectorFunction connectorFunction;

    @BeforeEach
    void setUp() {
        mockEdcService = Mockito.mock(EdcService.class);
        connectorFunction = new EdcConnectorFunction(mockEdcService);
    }

    @Test
    void shouldExecuteSuccessfully() throws Exception {
        // Given
        EdcConnectorRequest request = new EdcConnectorRequest();
        request.setEdcManagementUrl("http://localhost:9193/management");
        request.setProviderUrl("http://provider:8080");
        request.setProviderDid("did:web:provider:test");
        request.setAssetId("test-asset");

        EdcConnectorResponse expectedResponse = new EdcConnectorResponse(
                "test-asset",
                "agreement-123",
                "transfer-456",
                "{\"data\": \"test\"}"
        );

        when(mockEdcService.executeEdcWorkflow(any(EdcConnectorRequest.class)))
                .thenReturn(expectedResponse);

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
                .variables(request)
                .build();

        // When
        Object result = connectorFunction.execute(context);

        // Then
        assertThat(result).isInstanceOf(EdcConnectorResponse.class);
        EdcConnectorResponse response = (EdcConnectorResponse) result;
        assertThat(response.getAssetId()).isEqualTo("test-asset");
        assertThat(response.getContractAgreementId()).isEqualTo("agreement-123");
        assertThat(response.getTransferId()).isEqualTo("transfer-456");
        assertThat(response.getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void shouldThrowExceptionWhenEdcManagementUrlIsMissing() {
        // Given
        EdcConnectorRequest request = new EdcConnectorRequest();
        request.setProviderUrl("http://provider:8080");
        request.setAssetId("test-asset");
        // Missing edcManagementUrl

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
                .variables(request)
                .build();

        // When/Then
        assertThatThrownBy(() -> connectorFunction.execute(context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EDC Management URL is required");
    }

    @Test
    void shouldThrowExceptionWhenAssetIdIsMissing() {
        // Given
        EdcConnectorRequest request = new EdcConnectorRequest();
        request.setEdcManagementUrl("http://localhost:9193/management");
        request.setProviderUrl("http://provider:8080");
        // Missing assetId

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
                .variables(request)
                .build();

        // When/Then
        assertThatThrownBy(() -> connectorFunction.execute(context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Asset ID is required");
    }

    @Test
    void shouldThrowExceptionWhenProviderUrlIsMissing() {
        // Given
        EdcConnectorRequest request = new EdcConnectorRequest();
        request.setEdcManagementUrl("http://localhost:9193/management");
        request.setAssetId("test-asset");
        // Missing providerUrl

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
                .variables(request)
                .build();

        // When/Then
        assertThatThrownBy(() -> connectorFunction.execute(context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Provider URL is required");
    }

    @Test
    void shouldValidateApiKeyAuthentication() {
        // Given
        EdcConnectorRequest request = new EdcConnectorRequest();
        request.setEdcManagementUrl("http://localhost:9193/management");
        request.setProviderUrl("http://provider:8080");
        request.setProviderDid("did:web:provider:test");
        request.setAssetId("test-asset");

        EdcConnectorRequest.Authentication auth = new EdcConnectorRequest.Authentication();
        auth.setType("api-key");
        // Missing apiKey
        request.setAuthentication(auth);

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
                .variables(request)
                .build();

        // When/Then
        assertThatThrownBy(() -> connectorFunction.execute(context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("API Key is required");
    }

    @Test
    void shouldValidateBasicAuthentication() {
        // Given
        EdcConnectorRequest request = new EdcConnectorRequest();
        request.setEdcManagementUrl("http://localhost:9193/management");
        request.setProviderUrl("http://provider:8080");
        request.setProviderDid("did:web:provider:test");
        request.setAssetId("test-asset");

        EdcConnectorRequest.Authentication auth = new EdcConnectorRequest.Authentication();
        auth.setType("basic");
        auth.setUsername("admin");
        // Missing password
        request.setAuthentication(auth);

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
                .variables(request)
                .build();

        // When/Then
        assertThatThrownBy(() -> connectorFunction.execute(context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password is required");
    }

    @Test
    void shouldHandleEdcServiceException() throws Exception {
        // Given
        EdcConnectorRequest request = new EdcConnectorRequest();
        request.setEdcManagementUrl("http://localhost:9193/management");
        request.setProviderUrl("http://provider:8080");
        request.setProviderDid("did:web:provider:test");
        request.setAssetId("test-asset");

        when(mockEdcService.executeEdcWorkflow(any(EdcConnectorRequest.class)))
                .thenThrow(new RuntimeException("EDC service error"));

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
                .variables(request)
                .build();

        // When/Then
        assertThatThrownBy(() -> connectorFunction.execute(context))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to retrieve data from EDC");
    }
}
