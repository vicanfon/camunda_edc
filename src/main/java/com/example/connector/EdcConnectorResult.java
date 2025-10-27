package com.example.connector;

/**
 * Result returned by the EDC Data Connector
 */
public class EdcConnectorResult {

    private boolean success;
    private String assetId;
    private String agreementId;
    private String transferProcessId;
    private String data;
    private String errorMessage;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getAssetId() {
        return assetId;
    }

    public void setAssetId(String assetId) {
        this.assetId = assetId;
    }

    public String getAgreementId() {
        return agreementId;
    }

    public void setAgreementId(String agreementId) {
        this.agreementId = agreementId;
    }

    public String getTransferProcessId() {
        return transferProcessId;
    }

    public void setTransferProcessId(String transferProcessId) {
        this.transferProcessId = transferProcessId;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
