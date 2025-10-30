package io.camunda.connector.edc.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Request model for the EDC Connector
 */
public class EdcConnectorRequest {

    @JsonProperty("edcManagementUrl")
    private String edcManagementUrl;

    @JsonProperty("providerUrl")
    private String providerUrl;

    @JsonProperty("providerDid")
    private String providerDid;

    @JsonProperty("assetId")
    private String assetId;

    @JsonProperty("authentication")
    private Authentication authentication;

    @JsonProperty("timeout")
    private Integer timeout = 60; // Default timeout in seconds

    @JsonProperty("counterPartyAddress")
    private String counterPartyAddress;

    public void validate() {
        if (edcManagementUrl == null || edcManagementUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("EDC Management URL is required");
        }

        // Validate EDC Management URL format
        if (!edcManagementUrl.startsWith("http://") && !edcManagementUrl.startsWith("https://")) {
            throw new IllegalArgumentException(
                "EDC Management URL must start with http:// or https://. Got: " + edcManagementUrl
            );
        }

        // Check if management URL includes the /management path
        if (!edcManagementUrl.contains("/management")) {
            throw new IllegalArgumentException(
                "EDC Management URL should include /management path. " +
                "Example: http://localhost:9193/management. Got: " + edcManagementUrl +
                "\nSee TROUBLESHOOTING.md for configuration details."
            );
        }

        if (assetId == null || assetId.trim().isEmpty()) {
            throw new IllegalArgumentException("Asset ID is required");
        }

        if (providerUrl == null || providerUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Provider URL is required");
        }

        if (providerDid == null || providerDid.trim().isEmpty()) {
            throw new IllegalArgumentException("Provider DID is required for authentication");
        }

        // Validate Provider URL format
        if (!providerUrl.startsWith("http://") && !providerUrl.startsWith("https://")) {
            throw new IllegalArgumentException(
                "Provider URL must start with http:// or https://. Got: " + providerUrl
            );
        }

        // Warn if provider URL includes /api/dsp (common mistake)
        if (providerUrl.contains("/api/dsp")) {
            throw new IllegalArgumentException(
                "Provider URL should be the base URL only, without /api/dsp. " +
                "The connector will append /api/dsp automatically. " +
                "Example: http://provider:8080 (not http://provider:8080/api/dsp). " +
                "Got: " + providerUrl +
                "\nSee TROUBLESHOOTING.md for configuration details."
            );
        }

        if (authentication != null) {
            authentication.validate();
        }
    }

    // Getters and Setters
    public String getEdcManagementUrl() {
        return edcManagementUrl;
    }

    public void setEdcManagementUrl(String edcManagementUrl) {
        this.edcManagementUrl = edcManagementUrl;
    }

    public String getProviderUrl() {
        return providerUrl;
    }

    public void setProviderUrl(String providerUrl) {
        this.providerUrl = providerUrl;
    }

    public String getProviderDid() {
        return providerDid;
    }

    public void setProviderDid(String providerDid) {
        this.providerDid = providerDid;
    }

    public String getAssetId() {
        return assetId;
    }

    public void setAssetId(String assetId) {
        this.assetId = assetId;
    }

    public Authentication getAuthentication() {
        return authentication;
    }

    public void setAuthentication(Authentication authentication) {
        this.authentication = authentication;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public String getCounterPartyAddress() {
        return counterPartyAddress;
    }

    public void setCounterPartyAddress(String counterPartyAddress) {
        this.counterPartyAddress = counterPartyAddress;
    }

    @Override
    public String toString() {
        return "EdcConnectorRequest{" +
                "edcManagementUrl='" + edcManagementUrl + '\'' +
                ", providerUrl='" + providerUrl + '\'' +
                ", assetId='" + assetId + '\'' +
                ", timeout=" + timeout +
                ", counterPartyAddress='" + counterPartyAddress + '\'' +
                '}';
    }

    /**
     * Authentication configuration for EDC Management API
     */
    public static class Authentication {
        @JsonProperty("type")
        private String type = "api-key"; // api-key or basic

        @JsonProperty("apiKey")
        // @Secret annotation removed - add back if using Camunda runtime with secret support
        private String apiKey;

        @JsonProperty("username")
        private String username;

        @JsonProperty("password")
        // @Secret annotation removed - add back if using Camunda runtime with secret support
        private String password;

        public void validate() {
            if ("api-key".equals(type) && (apiKey == null || apiKey.trim().isEmpty())) {
                throw new IllegalArgumentException("API Key is required for api-key authentication");
            }
            if ("basic".equals(type)) {
                if (username == null || username.trim().isEmpty()) {
                    throw new IllegalArgumentException("Username is required for basic authentication");
                }
                if (password == null || password.trim().isEmpty()) {
                    throw new IllegalArgumentException("Password is required for basic authentication");
                }
            }
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
