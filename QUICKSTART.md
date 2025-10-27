# Quick Start Guide - EDC Connector for Camunda 8.8

This guide will help you get started with the EDC Connector in 5 minutes.

## Prerequisites

- EDC consumer connector running with Management API at `http://localhost:19193/management`
- EDC provider connector with at least one published asset
- Camunda 8.8 account (SaaS or Self-Managed)

## Step 1: Build the Connector (2 minutes)

```bash
cd edc-connector
mvn clean package
```

This creates `target/edc-connector-1.0.0-with-dependencies.jar`

## Step 2: Deploy to Camunda (1 minute)

### For Camunda SaaS:
1. Go to your cluster in Console
2. Navigate to **Connectors**
3. Upload `target/edc-connector-1.0.0-with-dependencies.jar`

### For Self-Managed:
```bash
# Copy to connector runtime
cp target/edc-connector-1.0.0-with-dependencies.jar /path/to/connectors/
```

## Step 3: Install Template in Modeler (30 seconds)

### Desktop Modeler:
Copy `element-templates/edc-connector.json` to your templates folder:
- **Windows**: `%APPDATA%\camunda-modeler\resources\element-templates`
- **Mac**: `~/Library/Application Support/camunda-modeler/resources/element-templates`  
- **Linux**: `~/.config/camunda-modeler/resources/element-templates`

Restart Modeler.

### Web Modeler:
1. Open your project
2. Click **Upload files**
3. Upload `element-templates/edc-connector.json`

## Step 4: Create Your First Process (1 minute)

1. Open Camunda Modeler
2. Create a new BPMN diagram
3. Add a **Service Task**
4. Select **EDC Connector** template
5. Configure:
   - **EDC Management URL**: `http://localhost:19193/management`
   - **Provider Connector URL**: `http://provider-edc:8080`
   - **Asset ID**: `your-asset-id`
   - **Authentication Type**: `api-key` (or `none` for testing)
   - **API Key**: `your-api-key` (if required)
   - **Result Variable**: `edcData`

6. Save and deploy to your cluster

## Step 5: Run and Test (30 seconds)

1. Start a process instance from Modeler or Operate
2. Go to **Operate**
3. Watch your process execute
4. Check the process variables - you should see `edcData` with your retrieved data!

## Example Result

After execution, your `edcData` variable will contain:

```json
{
  "assetId": "your-asset-id",
  "contractAgreementId": "agreement-abc123",
  "transferId": "transfer-xyz789",
  "status": "SUCCESS",
  "data": {
    "your": "actual data from provider"
  }
}
```

## Next Steps

- Read the full [README.md](README.md) for advanced configuration
- Add error handling to your process
- Use the data in subsequent tasks
- Check troubleshooting section if you encounter issues

## Common First-Time Issues

**"Asset not found"**: Verify your asset ID exists in the provider catalog  
**"Connection refused"**: Check your EDC Management URL is accessible  
**"Authentication failed"**: Verify your API key or credentials

## Need Help?

- Check the [README.md](README.md) for detailed documentation
- Look at the example BPMN process
- Review EDC documentation: https://eclipse-edc.github.io/documentation/

Happy automating! 🚀
