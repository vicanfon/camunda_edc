# EDC Data Connector - Configuration Examples

## Example 1: Simple Data Retrieval

### Process Variables Required:
- `assetId`: "sensor-data-2024"

### Connector Configuration:
```
EDC Management API URL: http://localhost:9193/management
EDC API Key: {{secrets.EDC_API_KEY}}
Provider Connector URL: http://provider:8282/api/v1/dsp
Asset ID: =assetId
Result Variable: edcResult
```

### Expected Output:
```json
{
  "success": true,
  "assetId": "sensor-data-2024",
  "agreementId": "urn:uuid:agreement-abc-123",
  "transferProcessId": "urn:uuid:transfer-xyz-789",
  "data": "{\"temperature\": 23.5, \"humidity\": 65, \"timestamp\": \"2024-10-24T10:00:00Z\"}"
}
```

---

## Example 2: Multiple Asset Retrieval

### BPMN Multi-Instance Configuration:
```xml
<bpmn:multiInstanceLoopCharacteristics>
  <bpmn:extensionElements>
    <zeebe:loopCharacteristics inputCollection="=assetIds" inputElement="currentAssetId" />
  </bpmn:extensionElements>
</bpmn:multiInstanceLoopCharacteristics>
```

### Process Variables:
```json
{
  "assetIds": ["asset-1", "asset-2", "asset-3"]
}
```

### Connector Configuration:
```
Asset ID: =currentAssetId
Result Variable: edcResult
```

---

## Example 3: Dynamic Provider Selection

### Process Variables:
```json
{
  "providerRegistry": {
    "provider-a": "http://provider-a:8282/api/v1/dsp",
    "provider-b": "http://provider-b:8282/api/v1/dsp"
  },
  "selectedProvider": "provider-a",
  "assetId": "shared-dataset"
}
```

### Connector Configuration:
```
Provider Connector URL: =providerRegistry[selectedProvider]
Asset ID: =assetId
```

---

## Example 4: Conditional Retry Pattern

### BPMN Error Handling:
```xml
<bpmn:boundaryEvent id="ErrorEvent" attachedToRef="Activity_GetEDCData">
  <bpmn:errorEventDefinition errorRef="Error_EDC_Failure" />
  <bpmn:outgoing>Flow_to_retry</bpmn:outgoing>
</bpmn:boundaryEvent>
```

### Script Task for Retry Logic:
```javascript
// Check retry count
if (retryCount == null) {
  retryCount = 0;
}

if (retryCount < 3) {
  retryCount = retryCount + 1;
  shouldRetry = true;
} else {
  shouldRetry = false;
}
```

---

## Example 5: Data Transformation After Retrieval

### Script Task to Parse JSON:
```feel
// Parse the JSON data from EDC
parsedData = FEEL(edcResult.data)

// Extract specific fields
temperature = parsedData.temperature
humidity = parsedData.humidity

// Transform to application format
sensorReading = {
  "temp_celsius": temperature,
  "humidity_percent": humidity,
  "retrieved_at": now(),
  "source_asset": edcResult.assetId
}
```

---

## Example 6: Caching Strategy

### Check Cache First:
```xml
<bpmn:exclusiveGateway id="Gateway_CheckCache" name="Data in Cache?">
  <bpmn:incoming>Flow_from_start</bpmn:incoming>
  <bpmn:outgoing>Flow_cache_hit</bpmn:outgoing>
  <bpmn:outgoing>Flow_cache_miss</bpmn:outgoing>
</bpmn:exclusiveGateway>

<bpmn:sequenceFlow id="Flow_cache_miss" sourceRef="Gateway_CheckCache" targetRef="Activity_GetEDCData">
  <bpmn:conditionExpression>=cachedData = null or now() - cacheTimestamp > duration("PT1H")</bpmn:conditionExpression>
</bpmn:sequenceFlow>
```

---

## Environment-Specific Configurations

### Development Environment:
```yaml
EDC_MANAGEMENT_URL: http://localhost:9193/management
EDC_API_KEY: dev-api-key-12345
PROVIDER_URL: http://localhost:8282/api/v1/dsp
```

### Staging Environment:
```yaml
EDC_MANAGEMENT_URL: https://edc-staging.company.com/management
EDC_API_KEY: {{secrets.EDC_API_KEY_STAGING}}
PROVIDER_URL: https://provider-staging.partner.com/api/v1/dsp
```

### Production Environment:
```yaml
EDC_MANAGEMENT_URL: https://edc.company.com/management
EDC_API_KEY: {{secrets.EDC_API_KEY_PROD}}
PROVIDER_URL: https://provider.partner.com/api/v1/dsp
```

---

## Security Best Practices

### 1. Always Use Secrets for API Keys:
```
# Good
EDC API Key: {{secrets.EDC_API_KEY}}

# Bad - Never hardcode!
EDC API Key: my-api-key-12345
```

### 2. Use HTTPS in Production:
```
# Good
Provider URL: https://provider.partner.com/api/v1/dsp

# Acceptable only in development
Provider URL: http://localhost:8282/api/v1/dsp
```

### 3. Validate Asset IDs:
```feel
// Validate before using
validAssetId = if string length(assetId) > 0 and matches(assetId, "^[a-zA-Z0-9-]+$") 
               then assetId 
               else error("Invalid asset ID")
```

---

## Monitoring and Observability

### Add Custom Business Metrics:
```xml
<bpmn:extensionElements>
  <zeebe:properties>
    <zeebe:property name="metric.edc.asset.id" value="=assetId" />
    <zeebe:property name="metric.edc.provider" value="=providerUrl" />
  </zeebe:properties>
</bpmn:extensionElements>
```

### Log Important Events:
Use script tasks to log:
```feel
logMessage = "EDC Data Retrieved - Asset: " + edcResult.assetId + 
             ", Agreement: " + edcResult.agreementId + 
             ", Data Size: " + string length(edcResult.data)
```

---

## Testing Configuration

### Unit Test with Mock Data:
```json
{
  "edcManagementUrl": "http://localhost:9193/management",
  "edcApiKey": "test-api-key",
  "providerUrl": "http://mock-provider:8282/api/v1/dsp",
  "assetId": "test-asset-123"
}
```

### Integration Test:
Use MinimumViableDataspace for realistic testing:
```bash
git clone https://github.com/eclipse-edc/MinimumViableDataspace
cd MinimumViableDataspace
docker-compose up -d
```

Then configure:
```
EDC Management API URL: http://localhost:29193/management
Provider URL: http://localhost:29291/api/v1/dsp
Asset ID: asset-1
```
