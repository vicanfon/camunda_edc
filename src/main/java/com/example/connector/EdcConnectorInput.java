package com.example.connector;

import io.camunda.connector.api.annotation.Secret;
import jakarta.validation.constraints.NotBlank;

/**
 * Input parameters for the EDC Data Connector
 */
public class EdcConnectorInput {

    @NotBlank
    private String edcManagementUrl;

    @Secret
    @NotBlank
    private String edcApiKey;

    @NotBlank
    private String providerUrl;

    @NotBlank
    private String assetId;

    private Authentication authentication;

    public String getEdcManagementUrl() {
        return edcManagementUrl;
    }

    public void setEdcManagementUrl(String edcManagementUrl) {
        this.edcManagementUrl = edcManagementUrl;
    }

    public String getEdcApiKey() {
        return edcApiKey;
    }

    public void setEdcApiKey(String edcApiKey) {
        this.edcApiKey = edcApiKey;
    }

    public String getProviderUrl() {
        return providerUrl;
    }

    public void setProviderUrl(String providerUrl) {
        this.providerUrl = providerUrl;
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

    public static class Authentication {
        private String type;
        private String token;
        private String username;
        private String password;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
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
