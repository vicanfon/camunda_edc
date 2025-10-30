# EDC Connector Configuration Guide

## Your Setup
- **Camunda**: Running outside K8s via Docker Compose on host machine
- **MVD EDC**: Running inside K8s with ingress routes
- **Network**: Both on the same host machine

## Configuration for Camunda Modeler

### Scenario 1: Using Ingress (Direct Access)

If you can access `http://localhost/consumer/cp` from your host machine:

**Connector Configuration:**
```yaml
EDC Management URL: http://localhost/consumer/cp/api/management
Provider Connector URL: http://localhost/provider-qna
Asset ID: normal-asset-1
Authentication Type: api-key
API Key: password
```

### Scenario 2: Using Port Forwarding (Recommended)

If ingress is not accessible or you prefer internal K8s service access:

**Step 1: Set up port forwarding**
```bash
# Forward Consumer Management API
kubectl port-forward svc/consumer-controlplane 9193:8082 -n <your-namespace>

# In another terminal, forward Provider DSP
kubectl port-forward svc/provider-qna-controlplane 9092:8082 -n <your-namespace>
```

**Step 2: Connector Configuration:**
```yaml
EDC Management URL: http://localhost:9193/api/management
Provider Connector URL: http://localhost:9092
Asset ID: normal-asset-1
Authentication Type: api-key
API Key: password
```

## Testing Your Configuration

### Test 1: Verify Management API Access

```bash
curl -X POST "http://localhost:9193/api/management/v3/assets/request" \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: password" \
  -d '{"@context": {"@vocab": "https://w3id.org/edc/v0.0.1/ns/"}, "@type": "QuerySpec"}'
```

Expected: HTTP 200 with asset list

### Test 2: Query Provider Catalog

```bash
curl -X POST "http://localhost:9193/api/management/v3/catalog/request" \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: password" \
  -d '{
    "@context": ["https://w3id.org/edc/connector/management/v0.0.1"],
    "@type": "CatalogRequest",
    "counterPartyAddress": "http://localhost:9092/api/dsp",
    "protocol": "dataspace-protocol-http",
    "querySpec": {"offset": 0, "limit": 50}
  }'
```

Expected: HTTP 200 with catalog containing datasets, including `normal-asset-1`

## Troubleshooting

### Error: Connection Refused

**Cause**: Ingress not accessible or port forwarding not set up

**Solution**:
1. Check if ingress is running: `kubectl get ingress -A`
2. Set up port forwarding as shown in Scenario 2
3. Verify port forwarding is working: `curl http://localhost:9193/api/management/v3/assets/request`

### Error: Asset Not Found in Catalog

**Cause**: Provider connector not reachable or asset doesn't exist

**Solution**:
1. Verify provider is reachable: Run Test 2 above
2. Check asset exists in provider catalog
3. Verify provider URL doesn't include `/api/dsp` (connector appends this automatically)

### Error: 404 Not Found on Management API

**Cause**: Incorrect path - missing `/api` prefix

**Solution**:
- For ingress: Use `/consumer/cp/api/management` not `/consumer/cp/management`
- For port-forward: Use `/api/management` not just `/management`

### Error: 405 Method Not Allowed

**Cause**: Using old API version or wrong endpoint

**Solution**:
- Ensure Management URL includes `/api/management`
- Ensure you're using the updated connector with v3 API support
- Provider URL should be base URL only (no `/api/dsp` suffix)

## Next Steps

1. **Choose your scenario** (Ingress or Port Forwarding)
2. **Run the test commands** to verify connectivity
3. **Configure the connector** in Camunda Modeler using the element template
4. **Test the connector** by running a process instance
5. **Check logs** if issues occur: `kubectl logs -f <pod-name>`

## Example BPMN Process

Create a simple BPMN process to test:

1. Create a new BPMN diagram in Camunda Modeler
2. Add a Service Task
3. Apply the "EDC Data Connector" template
4. Fill in the configuration values above
5. Add a result variable name: `edcResult`
6. Deploy and run the process

Check the result:
```javascript
// In the next task, you can access:
edcResult.success // true if successful
edcResult.data // The retrieved data
edcResult.assetId // The asset ID
edcResult.agreementId // Contract agreement ID
edcResult.errorMessage // Error message if failed
```

## Important Notes

- The connector automatically appends `/api/dsp` to the Provider URL for DSP communication
- Use Management API v3 endpoints (this connector is v3-compatible)
- API key authentication is used by default in MVD
- The connector handles the full flow: catalog query → contract negotiation → transfer → data retrieval
