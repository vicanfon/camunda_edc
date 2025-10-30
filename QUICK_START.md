# Quick Start: What to Do Next

## Summary of Changes

I've fixed a critical bug and added comprehensive documentation to help you configure the EDC connector properly.

### Critical Fix Applied
**The connector now automatically appends `/api/dsp` to the provider URL**. You only need to provide the base URL.

## Step-by-Step Instructions

### Step 1: Set Up Port Forwarding to EDC Services

Since Camunda is running outside K8s (via docker-compose), you need to expose the EDC services:

```bash
# Terminal 1: Forward Consumer Management API
kubectl port-forward svc/consumer-controlplane 9193:8082 -n <your-namespace>

# Terminal 2: Forward Provider DSP
kubectl port-forward svc/provider-qna-controlplane 9092:8082 -n <your-namespace>
```

**Note:** Replace `<your-namespace>` with the actual K8s namespace where MVD is deployed (check with `kubectl get pods -A | grep consumer`).

### Step 2: Test Connectivity

Run the automated test script:

```bash
cd /home/user/camunda_edc
./test-edc-connection.sh
```

This script will:
- ✓ Verify Consumer Management API is accessible
- ✓ Query the provider's catalog
- ✓ Check if asset `normal-asset-1` exists
- ✓ Confirm provider DSP endpoint is reachable

**If tests fail:** The script will provide troubleshooting guidance.

### Step 3: Build the Connector

```bash
mvn clean package
```

This creates: `target/edc-data-connector-1.0.0.jar`

### Step 4: Deploy to Camunda

Copy the JAR file to your Camunda connectors directory:

```bash
# Check your Camunda docker-compose.yml for the connectors volume mount
# Typically something like:
cp target/edc-data-connector-1.0.0.jar /path/to/camunda/connectors/
```

Restart Camunda to load the new connector:

```bash
docker-compose restart
```

### Step 5: Configure in Camunda Modeler

1. Open Camunda Modeler
2. Create a new BPMN diagram (or open existing one)
3. Add a **Service Task**
4. Click on the task and apply the **"EDC Data Connector"** template from the template catalog
5. Configure with these exact values:

```
┌─────────────────────────────────────────────────────┐
│ EDC Data Connector Configuration                    │
├─────────────────────────────────────────────────────┤
│ EDC Management URL:     http://localhost:9193/api/management
│ Provider Connector URL: http://localhost:9092       │
│ Asset ID:               normal-asset-1              │
│ Authentication Type:    api-key                     │
│ API Key:                password                    │
│ Result Variable:        edcResult                   │
└─────────────────────────────────────────────────────┘
```

**Important Notes:**
- ✓ Management URL must include `/api/management` suffix
- ✓ Provider URL is **base URL only** (no `/api/dsp` - connector adds it automatically)
- ✓ Ports 9193 and 9092 must match your port-forward commands
- ✓ API key is typically "password" in MVD default setup

### Step 6: Deploy and Test

1. **Deploy** the BPMN process to Camunda
2. **Start** a process instance
3. **Check** the result in the process variables:

```javascript
// The edcResult variable will contain:
{
  "success": true,                    // or false if error occurred
  "assetId": "normal-asset-1",        // the asset you requested
  "agreementId": "agreement-123",     // contract agreement ID
  "transferProcessId": "transfer-456", // transfer process ID
  "data": "{...actual data...}",      // the retrieved data
  "errorMessage": null                // error message if failed
}
```

## Alternative: Using Ingress (If Available)

If you have ingress routes configured and accessible from your host:

```
EDC Management URL:     http://localhost/consumer/cp/api/management
Provider Connector URL: http://localhost/provider-qna
Asset ID:               normal-asset-1
Authentication Type:    api-key
API Key:                password
```

## Troubleshooting

### Port Forwarding Not Working

**Symptom:** `curl: (7) Failed to connect to localhost`

**Solution:**
```bash
# Check if port forwarding is running
ps aux | grep "kubectl port-forward"

# Check if services exist
kubectl get svc -A | grep -E 'consumer|provider'

# Verify pods are running
kubectl get pods -A | grep -E 'consumer|provider'
```

### Asset Not Found in Catalog

**Symptom:** "Asset normal-asset-1 not found in catalog"

**Solution:**
```bash
# Query catalog directly to see available assets
curl -X POST "http://localhost:9193/api/management/v3/catalog/request" \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: password" \
  -d '{
    "@context": ["https://w3id.org/edc/connector/management/v0.0.1"],
    "@type": "CatalogRequest",
    "counterPartyAddress": "http://localhost:9092/api/dsp",
    "protocol": "dataspace-protocol-http"
  }'
```

Check the response for available asset IDs and update your configuration accordingly.

### Connector Not Appearing in Modeler

**Symptom:** "EDC Data Connector" template not in Camunda Modeler

**Solution:**
1. Copy `element-templates/edc-connector.json` to Camunda Modeler's template directory
2. Or configure Modeler to look in your project's `element-templates/` folder
3. Restart Camunda Modeler

### Build Failures

**Symptom:** Maven build errors

**Solution:**
```bash
# Ensure Java 17+ is installed
java -version

# Clear Maven cache if needed
rm -rf ~/.m2/repository/io/camunda

# Rebuild
mvn clean install -DskipTests
```

## What Changed?

### Code Changes:
1. **DSP Auto-Append**: The connector now automatically appends `/api/dsp` to the provider URL for all DSP communication (catalog, negotiation, transfer)
2. **Management API v3**: All endpoints use `/v3/` paths (compatible with EDC 0.8.x+ and MVD)

### New Files:
- **CONFIGURATION_GUIDE.md**: Comprehensive configuration guide for different scenarios
- **test-edc-connection.sh**: Automated connectivity test script
- **QUICK_START.md**: This file - step-by-step instructions
- **Updated README.md**: Quick reference with correct examples

### Files Already Present:
- **K8S_CONFIGURATION.md**: Kubernetes deployment guide
- **TROUBLESHOOTING.md**: Common errors and solutions
- **check-k8s-setup.sh**: K8s diagnostic script

## Need More Help?

1. **Configuration issues**: See `CONFIGURATION_GUIDE.md`
2. **Kubernetes setup**: See `K8S_CONFIGURATION.md`
3. **Common errors**: See `TROUBLESHOOTING.md`
4. **Test connectivity**: Run `./test-edc-connection.sh`
5. **Check K8s setup**: Run `./check-k8s-setup.sh`

## Summary

You're now ready to use the EDC connector! The key steps are:

1. ✅ **Port forward** EDC services (or use ingress)
2. ✅ **Test** connectivity with `./test-edc-connection.sh`
3. ✅ **Build** with `mvn clean package`
4. ✅ **Deploy** JAR to Camunda
5. ✅ **Configure** in Camunda Modeler
6. ✅ **Test** with a process instance

Good luck! 🚀
