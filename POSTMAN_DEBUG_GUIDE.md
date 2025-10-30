# EDC Connector Postman Debug Guide

This guide explains how to use the Postman collection to test and debug the EDC connector workflow step by step.

## Prerequisites

- Postman installed (or use Postman Web)
- EDC connector running (Consumer and Provider)
- Access to the consumer's Management API

## Import the Collection

1. Open Postman
2. Click **Import**
3. Select the file: `EDC_Connector_Debug.postman_collection.json`
4. The collection will appear in your Collections sidebar

## Collection Variables

The collection comes pre-configured with these variables:

| Variable | Value | Description |
|----------|-------|-------------|
| `management_url` | `http://host.docker.internal/consumer/cp/api/management` | Consumer Management API endpoint |
| `provider_url` | `http://provider-qna-controlplane:8082` | Provider connector base URL |
| `provider_did` | `did:web:provider-identityhub%3A7083:provider` | Provider's DID for authentication |
| `asset_id` | `asset-1` | Asset to retrieve |
| `api_key` | `password` | API key for Management API authentication |

**Important**: If you're running Postman **outside Docker**, change:
- `management_url` to: `http://localhost/consumer/cp/api/management`

If you're running Postman **inside Docker**, keep it as `host.docker.internal`.

## Workflow Steps

### Step 1: Query Catalog

**What it does**: Queries the EDC catalog to find the asset and retrieve available offers/policies.

**Request**:
```http
POST {{management_url}}/v3/catalog/request
X-Api-Key: {{api_key}}
Content-Type: application/json

{
  "@context": {
    "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
  },
  "counterPartyAddress": "{{provider_url}}/api/dsp",
  "counterPartyId": "{{provider_did}}",
  "protocol": "dataspace-protocol-http",
  "querySpec": {
    "filterExpression": {
      "operandLeft": "https://w3id.org/edc/v0.0.1/ns/id",
      "operator": "=",
      "operandRight": "{{asset_id}}"
    }
  }
}
```

**Expected Response** (200 OK):
```json
{
  "@id": "...",
  "@type": "dcat:Catalog",
  "dcat:dataset": {
    "@id": "asset-1",
    "@type": "dcat:Dataset",
    "odrl:hasPolicy": {
      "@id": "bWVtYmVyLWFuZC1kYXRhcHJvY2Vzc29yLWRlZg==:YXNzZXQtMQ==:...",
      "@type": "odrl:Offer",
      "odrl:permission": [...],
      "odrl:prohibition": [],
      "odrl:obligation": []
    },
    "dcat:distribution": [...]
  }
}
```

**What to check**:
- ✅ Status code is 200
- ✅ Response contains `dcat:dataset` with your asset ID
- ✅ Dataset has `odrl:hasPolicy` with an offer
- ✅ The test script automatically saves `offer_id` and `offer_policy` for the next step

**Common Errors**:
- **404**: Management URL is incorrect
- **401/403**: API key is wrong
- **Empty dataset**: Asset doesn't exist or provider is unreachable

---

### Step 2: Initiate Contract Negotiation

**What it does**: Starts a contract negotiation using the offer from the catalog.

**Request**:
```http
POST {{management_url}}/v3/contractnegotiations
X-Api-Key: {{api_key}}
Content-Type: application/json

{
  "@context": [
    "https://w3id.org/edc/connector/management/v0.0.1"
  ],
  "@type": "ContractRequest",
  "counterPartyAddress": "{{provider_url}}/api/dsp",
  "counterPartyId": "{{provider_did}}",
  "protocol": "dataspace-protocol-http",
  "policy": {{offer_policy}},
  "callbackAddresses": []
}
```

**Note**: The `policy` field contains the entire offer object from Step 1's catalog response. It includes `@id`, `@type`, `permission`, `prohibition`, `obligation`, and `target` fields.

**Expected Response** (200 or 201):
```json
{
  "@type": "ContractNegotiation",
  "@id": "550e8400-e29b-41d4-a716-446655440000",
  "type": "CONSUMER",
  "protocol": "dataspace-protocol-http",
  "state": "REQUESTING",
  "counterPartyId": "did:web:provider-identityhub%3A7083:provider",
  "counterPartyAddress": "http://provider-qna-controlplane:8082/api/dsp"
}
```

**What to check**:
- ✅ Status code is 200 or 201
- ✅ Response contains `@id` (negotiation ID)
- ✅ State is `REQUESTING` or `REQUESTED`
- ✅ The test script saves `negotiation_id` for the next step

**Common Errors**:
- **400 Bad Request**:
  - Check if `offer_policy` variable is set (run Step 1 first)
  - Check if the policy structure from catalog is correct
  - Verify provider URL and DID are correct
  - Ensure the `@context` and `@type` fields match EDC Management API v3 spec
- **404**: Incorrect endpoint URL

---

### Step 3: Check Negotiation Status

**What it does**: Polls the negotiation status until it's finalized.

**Request**:
```http
GET {{management_url}}/v3/contractnegotiations/{{negotiation_id}}
X-Api-Key: {{api_key}}
```

**Expected Response** (200 OK):
```json
{
  "@type": "ContractNegotiation",
  "@id": "550e8400-e29b-41d4-a716-446655440000",
  "type": "CONSUMER",
  "protocol": "dataspace-protocol-http",
  "state": "FINALIZED",
  "counterPartyId": "did:web:provider-identityhub%3A7083:provider",
  "counterPartyAddress": "http://provider-qna-controlplane:8082/api/dsp",
  "contractAgreementId": "660e8400-e29b-41d4-a716-446655440000"
}
```

**What to check**:
- ✅ Run this request multiple times (every 2-3 seconds) until state is `FINALIZED`
- ✅ When finalized, response contains `contractAgreementId`
- ✅ The test script saves `agreement_id` for the next step

**Possible States**:
- `REQUESTING` → Negotiation initiated
- `REQUESTED` → Provider received the request
- `AGREEING` → Provider is processing
- `AGREED` → Provider agreed
- `FINALIZED` → ✅ Success! Agreement created
- `TERMINATED` → ❌ Failed
- `ERROR` → ❌ Failed

**Common Errors**:
- **404**: Negotiation ID not found (check if Step 2 succeeded)
- **State = TERMINATED**: Policy violation or provider rejected the offer

---

### Step 4: Initiate Transfer

**What it does**: Initiates a data transfer using the contract agreement.

**Request**:
```http
POST {{management_url}}/v3/transferprocesses
X-Api-Key: {{api_key}}
Content-Type: application/json

{
  "@context": {
    "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
  },
  "counterPartyAddress": "{{provider_url}}/api/dsp",
  "counterPartyId": "{{provider_did}}",
  "contractId": "{{agreement_id}}",
  "assetId": "{{asset_id}}",
  "protocol": "dataspace-protocol-http",
  "dataDestination": {
    "type": "HttpProxy"
  }
}
```

**Expected Response** (200 or 201):
```json
{
  "@type": "TransferProcess",
  "@id": "770e8400-e29b-41d4-a716-446655440000",
  "state": "REQUESTING"
}
```

**What to check**:
- ✅ Status code is 200 or 201
- ✅ Response contains `@id` (transfer ID)
- ✅ The test script saves `transfer_id` for the next step

**Common Errors**:
- **400 Bad Request**:
  - Agreement ID is invalid or expired
  - Asset ID doesn't match the agreement
- **404**: Incorrect endpoint

---

### Step 5: Check Transfer Status

**What it does**: Polls the transfer status and retrieves the EDR (Endpoint Data Reference).

**Request**:
```http
GET {{management_url}}/v3/transferprocesses/{{transfer_id}}
X-Api-Key: {{api_key}}
```

**Expected Response** (200 OK):
```json
{
  "@type": "TransferProcess",
  "@id": "770e8400-e29b-41d4-a716-446655440000",
  "state": "STARTED",
  "dataAddress": {
    "@type": "DataAddress",
    "type": "HttpData",
    "endpoint": "http://provider-qna-dataplane:8081/api/public/...",
    "authorization": "eyJhbGciOiJSUzI1NiJ9...",
    "endpointType": "https://w3id.org/idsa/v4.1/HTTP"
  }
}
```

**What to check**:
- ✅ Run this request multiple times until state is `STARTED` or `COMPLETED`
- ✅ Response contains `dataAddress` with `endpoint` and `authorization`
- ✅ The test script saves `edr_endpoint` and `edr_auth` for the final step

**Possible States**:
- `REQUESTING` → Transfer initiated
- `REQUESTED` → Provider received request
- `STARTED` → ✅ Transfer ready, EDR available
- `COMPLETED` → ✅ Transfer complete
- `TERMINATED` → ❌ Failed
- `ERROR` → ❌ Failed

---

### Step 6: Fetch Actual Data (EDR)

**What it does**: Retrieves the actual data from the provider's data plane using the EDR.

**Request**:
```http
GET {{edr_endpoint}}
Authorization: {{edr_auth}}
Content-Type: application/json
```

**Expected Response** (200 OK):
```json
{
  "id": "asset-1",
  "data": {
    "message": "Hello from EDC!"
  }
}
```

**What to check**:
- ✅ Status code is 200
- ✅ Response contains your actual asset data

**Common Errors**:
- **401/403**: Authorization token is invalid or expired
- **404**: EDR endpoint is incorrect

---

## Troubleshooting

### Network Issues

If you get connection errors:

1. **From outside Docker**: Use `http://localhost/consumer/cp/api/management`
2. **From inside Docker**: Use `http://host.docker.internal/consumer/cp/api/management`
3. Check if EDC is running: `kubectl get pods -n mvd`

### Catalog Returns Empty Dataset

**Symptoms**: Step 1 returns `"dcat:dataset": []`

**Causes**:
- Provider URL is incorrect
- Provider DID is incorrect
- Asset doesn't exist on the provider
- Provider is not reachable from consumer

**Fix**:
1. Verify provider is running: `kubectl get svc -n mvd | grep provider`
2. Check the asset exists on the provider
3. Verify the provider DID is correct

### Negotiation Stuck in REQUESTING

**Symptoms**: Step 3 never reaches FINALIZED state

**Causes**:
- Provider cannot reach consumer's callback endpoint
- Firewall blocking DSP protocol communication
- Provider is down or unresponsive

**Fix**:
1. Check provider logs: `kubectl logs -n mvd <provider-pod>`
2. Verify DSP endpoints are accessible
3. Check if provider can reach consumer

### Transfer Fails

**Symptoms**: Transfer state goes to TERMINATED

**Causes**:
- Data plane is not running
- Agreement has expired
- Asset has been deleted

**Fix**:
1. Check data plane pods: `kubectl get pods -n mvd | grep dataplane`
2. Verify the asset still exists
3. Start a fresh workflow from Step 1

---

## Comparing with Connector Code

Each request in this collection maps to a method in `EdcService.java`:

| Postman Step | Java Method | Line |
|--------------|-------------|------|
| Step 1 | `queryCatalog()` | 78-182 |
| Step 2 | `negotiateContract()` | 187-253 |
| Step 3 | `waitForNegotiation()` | 258-293 |
| Step 4 | `initiateTransfer()` | 298-336 |
| Step 5 | `retrieveTransferData()` | 341-384 |
| Step 6 | `fetchDataFromEndpoint()` | 389-413 |

You can use this collection to:
- ✅ Test each step independently
- ✅ Verify request bodies are correctly formed
- ✅ Debug issues at specific steps
- ✅ Compare expected vs actual responses
- ✅ Test with different assets, providers, or configurations

---

## Quick Reference

### All Endpoints Used

```
POST   /v3/catalog/request
POST   /v3/contractnegotiations
GET    /v3/contractnegotiations/{id}
POST   /v3/transferprocesses
GET    /v3/transferprocesses/{id}
GET    {dynamic EDR endpoint}
```

### Authentication

All Management API requests use:
```http
X-Api-Key: password
```

The final data fetch uses:
```http
Authorization: {JWT from transfer response}
```

### Expected Workflow Timing

- Catalog query: Instant
- Negotiation: 2-10 seconds
- Transfer: 2-10 seconds
- Data fetch: Instant

Total time: ~5-20 seconds for complete workflow.
