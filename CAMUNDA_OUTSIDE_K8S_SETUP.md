# EDC Connector Configuration for Camunda Outside K8s

## Your Setup
- **Camunda 8.8**: Running in Docker Compose on host machine
- **MVD EDC**: Running inside Kubernetes (namespace: mvd)
- **Access**: Via Kubernetes ingress at `http://localhost`

## Critical Networking Concept

There are **TWO** communication paths to configure:

### Path 1: Camunda → Consumer EDC Management API
**Who**: Camunda (outside K8s) needs to reach Consumer EDC Management API (inside K8s)
**Use**: Ingress URL
**Value**: `http://localhost/consumer/cp/api/management`

### Path 2: Consumer EDC → Provider EDC
**Who**: Consumer EDC (inside K8s) needs to reach Provider EDC (inside K8s)
**Use**: Internal Kubernetes service name
**Value**: `http://provider-qna-controlplane:8082`

## Correct Configuration

### In Camunda Modeler

Apply the "EDC Data Connector" template to a Service Task with these values:

```yaml
EDC Management URL:     http://localhost/consumer/cp/api/management
Provider Connector URL: http://provider-qna-controlplane:8082
Asset ID:               normal-asset-1
Authentication Type:    api-key
API Key:                password
Result Variable:        edcResult
```

### Why These Values?

**EDC Management URL** = `http://localhost/consumer/cp/api/management`
- Camunda uses this to call the consumer's Management API
- Since Camunda is outside K8s, it uses the ingress URL
- Ingress routes `/consumer/cp` to the consumer-controlplane service

**Provider Connector URL** = `http://provider-qna-controlplane:8082`
- The connector passes this to the consumer EDC
- The consumer EDC uses this to contact the provider via DSP
- Since both EDCs are inside K8s, they use internal service names
- `:8082` is the DSP protocol endpoint port
- The connector automatically appends `/api/dsp` to this URL

**Asset ID** = `normal-asset-1`
- This is the asset you want to retrieve from the provider's catalog
- Must match an asset that exists in the provider's catalog

**API Key** = `password`
- This is the default API key for MVD deployments
- Used to authenticate with the consumer's Management API

## Testing the Configuration

Run the test script:

```bash
./test-edc-connection.sh
```

Expected output:
```
✓ Management API accessible (HTTP 200)
✓ Catalog query successful (HTTP 200)
✓ Asset 'normal-asset-1' found in catalog
✓ Provider DSP endpoint reachable
```

## Common Mistakes to Avoid

### ❌ Wrong: Using ingress URL for provider
```yaml
Provider Connector URL: http://localhost/provider-qna
```
**Why wrong**: The consumer EDC (inside K8s) cannot reach `localhost/provider-qna` because from inside the pod, `localhost` refers to the pod itself, not the host machine.

### ✓ Correct: Using K8s service name for provider
```yaml
Provider Connector URL: http://provider-qna-controlplane:8082
```
**Why correct**: This is the internal Kubernetes service name that the consumer EDC can reach directly within the cluster.

### ❌ Wrong: Including /api/dsp in provider URL
```yaml
Provider Connector URL: http://provider-qna-controlplane:8082/api/dsp
```
**Why wrong**: The connector automatically appends `/api/dsp` for DSP communication. Including it would result in `/api/dsp/api/dsp`.

### ✓ Correct: Base URL only
```yaml
Provider Connector URL: http://provider-qna-controlplane:8082
```
**Why correct**: The connector appends `/api/dsp` when needed.

## Deployment Steps

### 1. Build the Connector
```bash
mvn clean package
```

### 2. Copy to Camunda Connectors Directory
```bash
# Find your connectors directory in docker-compose.yml
# It's usually mounted as a volume, e.g., ./connectors:/opt/connectors
cp target/edc-data-connector-1.0.0.jar /path/to/camunda/connectors/
```

### 3. Copy Element Template to Modeler
```bash
# Find your Camunda Modeler's resources directory
# Usually: ~/.config/camunda-modeler/resources/element-templates/
cp element-templates/edc-connector.json ~/.config/camunda-modeler/resources/element-templates/
```

### 4. Restart Camunda
```bash
docker-compose restart
```

### 5. Restart Camunda Modeler
Close and reopen Camunda Modeler to load the new template.

## Full Example BPMN Process

1. **Create BPMN Process**:
   - Open Camunda Modeler
   - Create new BPMN diagram
   - Name it "EDC Data Retrieval Test"

2. **Add Start Event**

3. **Add Service Task**:
   - Name: "Retrieve EDC Data"
   - Apply template: "EDC Data Connector"
   - Configure as shown above
   - Result Variable: `edcResult`

4. **Add Script Task** (to log the result):
   - Name: "Log Result"
   - Script Format: `javascript`
   - Script:
   ```javascript
   print("EDC Result: " + JSON.stringify(edcResult));
   ```

5. **Add End Event**

6. **Deploy**:
   - Click "Deploy" button
   - Select your Camunda cluster endpoint
   - Deploy the process

7. **Start Process Instance**:
   - Start a new instance of your process
   - Monitor in Camunda Operate
   - Check the `edcResult` variable in the process variables

## Expected Result

The `edcResult` variable should contain:

```json
{
  "success": true,
  "assetId": "normal-asset-1",
  "agreementId": "abc-123-def-456",
  "transferProcessId": "xyz-789-uvw-012",
  "data": "... actual data from provider ..."
}
```

If there's an error:

```json
{
  "success": false,
  "errorMessage": "Description of what went wrong"
}
```

## Troubleshooting

### Ingress Not Working

**Symptom**: `curl: (7) Failed to connect to localhost port 80`

**Check**:
```bash
# Verify ingress is running
kubectl get ingress -n mvd

# Check ingress controller
kubectl get pods -n ingress-nginx

# Test ingress endpoint
curl -v http://localhost/consumer/cp/api/management/v3/assets/request \
  -H "X-Api-Key: password" \
  -H "Content-Type: application/json" \
  -d '{"@context": {"@vocab": "https://w3id.org/edc/v0.0.1/ns/"}, "@type": "QuerySpec"}'
```

### Consumer Cannot Reach Provider

**Symptom**: "Failed to connect" or "Connection refused" when querying catalog

**Check**:
```bash
# Check provider service exists
kubectl get svc provider-qna-controlplane -n mvd

# Check provider pod is running
kubectl get pods -n mvd | grep provider-qna

# Check provider logs
kubectl logs -f deployment/provider-qna-controlplane -n mvd
```

### Asset Not Found

**Symptom**: "Asset normal-asset-1 not found in catalog"

**Solution**: Query the catalog to see available assets:
```bash
curl -X POST "http://localhost/consumer/cp/api/management/v3/catalog/request" \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: password" \
  -d '{
    "@context": ["https://w3id.org/edc/connector/management/v0.0.1"],
    "@type": "CatalogRequest",
    "counterPartyAddress": "http://provider-qna-controlplane:8082/api/dsp",
    "protocol": "dataspace-protocol-http"
  }'
```

Look for the `"dcat:dataset"` array in the response to see available assets.

## Additional Resources

- **CONFIGURATION_GUIDE.md**: Detailed configuration for different scenarios
- **K8S_CONFIGURATION.md**: Kubernetes networking concepts
- **TROUBLESHOOTING.md**: Common errors and solutions
- **test-edc-connection.sh**: Automated connectivity test
- **check-k8s-setup.sh**: Kubernetes diagnostic script

## Summary

The key to success is understanding the networking:
- Camunda (outside K8s) → Consumer EDC: Use **ingress URL**
- Consumer EDC (inside K8s) → Provider EDC (inside K8s): Use **K8s service name**

Use the test script to verify everything works before configuring Camunda!
