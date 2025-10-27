# EDC Data Connector for Camunda 8.8

A custom Camunda 8 connector that integrates with Eclipse Dataspace Components (EDC) to retrieve data from data spaces.

## Overview

This connector automates the complete EDC data consumption workflow:
1. Catalog Request - Queries the provider's data catalog
2. Contract Negotiation - Automatically negotiates a data usage contract
3. Data Transfer - Initiates the data transfer process
4. Data Retrieval - Retrieves the actual data from the data plane

## Quick Start

### Building
```bash
mvn clean package
```

### Configuration in Modeler
1. Add Service Task to your BPMN
2. Select "EDC Data Connector" template
3. Configure:
   - EDC Management API URL: http://localhost:9193/management
   - EDC API Key: {{secrets.EDC_API_KEY}}
   - Provider URL: http://provider:8282/api/v1/dsp
   - Asset ID: your-asset-id
   - Result Variable: edcResult

### Output
The connector returns:
```json
{
  "success": true,
  "assetId": "your-asset-id",
  "agreementId": "agreement-123",
  "transferProcessId": "transfer-456",
  "data": "{...retrieved data...}"
}
```

See full documentation in comments within the source files.
