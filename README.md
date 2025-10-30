# EDC Data Connector for Camunda 8.8

A custom Camunda 8 connector that integrates with Eclipse Dataspace Components (EDC) to retrieve data from data spaces.

## Overview

This connector automates the complete EDC data consumption workflow:
1. Catalog Request - Queries the provider's data catalog
2. Contract Negotiation - Automatically negotiates a data usage contract
3. Data Transfer - Initiates the data transfer process
4. Data Retrieval - Retrieves the actual data from the data plane

## Quick Start

### 1. Build the Connector
```bash
mvn clean package
```

### 2. Test Your EDC Connection

Before configuring Camunda, test your EDC connectivity:

```bash
# Edit test-edc-connection.sh to match your environment
./test-edc-connection.sh
```

This script will verify:
- Consumer Management API is accessible
- Provider catalog can be queried
- Your target asset exists in the catalog

### 3. Set Up Access to EDC (Choose One)

**Option A: Port Forwarding (Recommended)**
```bash
# Forward Consumer Management API
kubectl port-forward svc/consumer-controlplane 9193:8082 -n <namespace>

# Forward Provider DSP (in another terminal)
kubectl port-forward svc/provider-qna-controlplane 9092:8082 -n <namespace>
```

**Option B: Ingress Access**
If your K8s cluster has ingress configured for EDC services, you can use ingress URLs directly.

### 4. Configure in Camunda Modeler

1. Add Service Task to your BPMN diagram
2. Apply "EDC Data Connector" template
3. Configure with these values:

**Using Port Forwarding:**
```yaml
EDC Management URL: http://localhost:9193/api/management
Provider Connector URL: http://localhost:9092
Asset ID: normal-asset-1
Authentication Type: api-key
API Key: password
Result Variable: edcResult
```

**Using Ingress:**
```yaml
EDC Management URL: http://localhost/consumer/cp/api/management
Provider Connector URL: http://localhost/provider-qna
Asset ID: normal-asset-1
Authentication Type: api-key
API Key: password
Result Variable: edcResult
```

**Important Notes:**
- Management URL must include `/api/management` path
- Provider URL should be **base URL only** (connector appends `/api/dsp` automatically)
- This connector uses Management API **v3** (compatible with EDC 0.8.x+ and MVD)

### 5. Deploy and Test

Deploy your BPMN process and start an instance. The connector will:
1. Query the provider's catalog
2. Negotiate a contract for the asset
3. Initiate a data transfer
4. Retrieve and return the data

### Output

The connector returns:
```json
{
  "success": true,
  "assetId": "normal-asset-1",
  "agreementId": "agreement-123",
  "transferProcessId": "transfer-456",
  "data": "{...retrieved data...}"
}
```

If an error occurs:
```json
{
  "success": false,
  "errorMessage": "Error description here"
}
```

## Documentation

- **[CONFIGURATION_GUIDE.md](CONFIGURATION_GUIDE.md)** - Detailed setup instructions for different deployment scenarios
- **[K8S_CONFIGURATION.md](K8S_CONFIGURATION.md)** - Kubernetes-specific configuration guide
- **[TROUBLESHOOTING.md](TROUBLESHOOTING.md)** - Common errors and solutions
- **[test-edc-connection.sh](test-edc-connection.sh)** - Automated connectivity test script
- **[check-k8s-setup.sh](check-k8s-setup.sh)** - Kubernetes deployment diagnostic script

## Compatibility

- **Camunda Platform**: 8.8+
- **EDC Version**: 0.8.x+ with Management API v3
- **Protocol**: Dataspace Protocol (DSP) at `/api/dsp`
- **Tested with**: MVD (Minimum Viable Dataspace)
