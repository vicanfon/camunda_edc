package io.camunda.connector.edc.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response model for the EDC Connector
 */
public class EdcConnectorResponse {

    @JsonProperty("assetId")
    private String assetId;

    @JsonProperty("contractAgreementId")
    private String contractAgreementId;

    @JsonProperty("transferId")
    private String transferId;

    @JsonProperty("data")
    private Object data;

    @JsonProperty("status")
    private String status;

    @JsonProperty("message")
    private String message;

    public EdcConnectorResponse() {
    }

    public EdcConnectorResponse(String assetId, String contractAgreementId, String transferId, Object data) {
        this.assetId = assetId;
        this.contractAgreementId = contractAgreementId;
        this.transferId = transferId;
        this.data = data;
        this.status = "SUCCESS";
    }

    // Getters and Setters
    public String getAssetId() {
        return assetId;
    }

    public void setAssetId(String assetId) {
        this.assetId = assetId;
    }

    public String getContractAgreementId() {
        return contractAgreementId;
    }

    public void setContractAgreementId(String contractAgreementId) {
        this.contractAgreementId = contractAgreementId;
    }

    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "EdcConnectorResponse{" +
                "assetId='" + assetId + '\'' +
                ", contractAgreementId='" + contractAgreementId + '\'' +
                ", transferId='" + transferId + '\'' +
                ", status='" + status + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}
