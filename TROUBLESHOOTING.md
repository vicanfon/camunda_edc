# EDC Connector Troubleshooting Guide

## Common Errors and Solutions

### 1. Error 405: "HTTP method POST is not supported by this URL"

**Error Message:**
```
Failed to query catalog. Status: 502, Body: [{
  "message": "HTTP ERROR 405 HTTP method POST is not supported by this URL",
  "URI": "http://host.docker.internal/api/v1/dsp/catalog/request"
}]
```

**Root Cause:**
This error indicates incorrect URL configuration or EDC version mismatch.

**Solution:**

#### Check Your URL Configuration

The connector requires TWO different URLs:

1. **EDC Management URL** (your consumer connector's control API)
   - ✅ Correct: `http://localhost:9193/management` or `http://localhost:8080/management`
   - ❌ Wrong: `http://localhost:9193` (missing `/management`)
   - ❌ Wrong: `http://localhost:9193/api/v1/dsp` (this is the protocol endpoint, not management!)

2. **Provider URL** (the provider connector's base URL, NOT the DSP endpoint)
   - ✅ Correct: `http://provider-connector:8080`
   - ✅ Correct: `http://host.docker.internal:8282`
   - ❌ Wrong: `http://provider-connector:8080/api/v1/dsp` (connector will append this automatically)

The connector automatically appends `/api/v1/dsp` to the Provider URL for DSP protocol communication.

#### Example Configuration in Camunda Modeler

```yaml
EDC Management URL: http://localhost:9193/management
API Key: your-api-key-here
Provider Connector URL: http://provider-host:8080
Asset ID: your-asset-id
```

#### Verify EDC Endpoints

Your consumer EDC should expose:
- Management API: `http://localhost:9193/management/v2/*`
- Protocol (DSP) API: `http://localhost:8080/api/v1/dsp` (for incoming requests from other connectors)

The provider EDC should expose:
- Protocol (DSP) API: `http://provider:8080/api/v1/dsp`

#### Check EDC Version Compatibility

This connector is built for **EDC 0.5.x - 0.8.x** with Management API v2 and DSP (Dataspace Protocol).

If you're using a different EDC version, the API paths might differ:
- EDC < 0.5.0: Uses older IDS protocol (not supported)
- EDC >= 0.5.0: Uses DSP at `/api/v1/dsp`

### 2. Error: "API Key is required for api-key authentication"

**Solution:** Make sure the API key field appears in Camunda Modeler after selecting "API Key" authentication type. If it doesn't appear, reload the element template or restart Camunda Modeler.

### 3. Error: "Asset not found in catalog"

**Possible Causes:**
- The asset ID doesn't exist in the provider's catalog
- The provider connector is not reachable
- Policy restrictions prevent the asset from appearing in your catalog query

**Solution:**
- Verify the asset exists: Query the provider's catalog directly via REST
- Check network connectivity to the provider
- Review the provider's access policies

### 4. Error: "Contract negotiation timeout"

**Solution:**
- Increase the timeout value in the connector configuration (default: 60 seconds)
- Check that both connectors can communicate bidirectionally
- Review contract negotiation logs in both EDC connectors

## Testing Your Configuration

### 1. Test Management API Access

```bash
curl -X GET "http://localhost:9193/management/v2/assets" \
  -H "X-Api-Key: your-api-key"
```

Expected: 200 OK with list of assets

### 2. Test Catalog Query Directly

```bash
curl -X POST "http://localhost:9193/management/v2/catalog/request" \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: your-api-key" \
  -d '{
    "@context": {
      "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
    },
    "counterPartyAddress": "http://provider-host:8080/api/v1/dsp",
    "protocol": "dataspace-protocol-http"
  }'
```

Expected: 200 OK with catalog containing datasets

### 3. Verify Provider DSP Endpoint

The provider should have the DSP endpoint active. You won't be able to access it directly (it requires protocol-specific requests), but it should be configured in the provider's EDC.

## Docker/Kubernetes Networking

If using `host.docker.internal`:
- This works from containers to access host machine
- Ensure the EDC connectors are actually running on the host
- Check port mappings are correct

If using Docker Compose:
- Use service names (e.g., `http://consumer-connector:8080`)
- Ensure both connectors are on the same network
- Check the actual port numbers for management API vs DSP API (often different)

## Need More Help?

1. Check EDC connector logs for detailed error messages
2. Enable DEBUG logging in your EDC configuration
3. Verify all firewall rules and network policies
4. Review EDC documentation: https://eclipse-edc.github.io/docs/
