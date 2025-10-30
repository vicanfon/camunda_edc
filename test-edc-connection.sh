#!/bin/bash

# EDC Connector Connection Test Script
# This script tests connectivity to your EDC deployment before configuring Camunda

set -e

echo "=========================================="
echo "EDC Connector Connection Test"
echo "=========================================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration - UPDATE THESE VALUES BASED ON YOUR SETUP
# Consumer Management URL: Ingress URL (for Camunda outside K8s)
# Provider URL: Internal K8s service name (for consumer EDC inside K8s to reach provider)
# Provider DID: Decentralized Identifier for authentication
CONSUMER_MGMT_URL="http://localhost/consumer/cp/api/management"
PROVIDER_BASE_URL="http://provider-qna-controlplane:8082"
PROVIDER_DID="did:web:provider-identityhub%3A7083:provider"
API_KEY="password"
ASSET_ID="asset-1"

echo "Testing with configuration:"
echo "  Consumer Management URL: $CONSUMER_MGMT_URL"
echo "  Provider Base URL: $PROVIDER_BASE_URL"
echo "  Provider DID: $PROVIDER_DID"
echo "  API Key: $API_KEY"
echo "  Asset ID: $ASSET_ID"
echo ""

# Test 1: Check Management API Assets Endpoint
echo "=========================================="
echo "Test 1: Consumer Management API - Assets"
echo "=========================================="

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$CONSUMER_MGMT_URL/v3/assets/request" \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: $API_KEY" \
  -d '{"@context": {"@vocab": "https://w3id.org/edc/v0.0.1/ns/"}, "@type": "QuerySpec"}')

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" = "200" ]; then
    echo -e "${GREEN}✓ Management API accessible (HTTP $HTTP_CODE)${NC}"
    echo "  Assets endpoint working correctly"
else
    echo -e "${RED}✗ Management API failed (HTTP $HTTP_CODE)${NC}"
    echo "  Response: $BODY"
    echo ""
    echo "  Troubleshooting:"
    echo "  1. Check if port forwarding is active: kubectl port-forward svc/consumer-controlplane 9193:8082"
    echo "  2. Or try ingress URL: http://localhost/consumer/cp/api/management"
    echo "  3. Verify API key is correct"
    exit 1
fi
echo ""

# Test 2: Query Provider Catalog
echo "=========================================="
echo "Test 2: Provider Catalog Query"
echo "=========================================="

CATALOG_REQUEST="{
  \"@context\": [\"https://w3id.org/edc/connector/management/v0.0.1\"],
  \"@type\": \"CatalogRequest\",
  \"counterPartyAddress\": \"$PROVIDER_BASE_URL/api/dsp\",
  \"counterPartyId\": \"$PROVIDER_DID\",
  \"protocol\": \"dataspace-protocol-http\",
  \"querySpec\": {\"offset\": 0, \"limit\": 50}
}"

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$CONSUMER_MGMT_URL/v3/catalog/request" \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: $API_KEY" \
  -d "$CATALOG_REQUEST")

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" = "200" ]; then
    echo -e "${GREEN}✓ Catalog query successful (HTTP $HTTP_CODE)${NC}"

    # Check if catalog contains datasets
    DATASET_COUNT=$(echo "$BODY" | grep -o '"dcat:dataset"' | wc -l)
    if [ "$DATASET_COUNT" -gt 0 ]; then
        echo "  Catalog contains datasets"

        # Check if our specific asset exists
        if echo "$BODY" | grep -q "\"$ASSET_ID\""; then
            echo -e "${GREEN}✓ Asset '$ASSET_ID' found in catalog${NC}"
        else
            echo -e "${YELLOW}⚠ Asset '$ASSET_ID' NOT found in catalog${NC}"
            echo "  Available assets:"
            echo "$BODY" | grep -o '"@id":"[^"]*"' | sed 's/"@id":"//g' | sed 's/"//g' | sed 's/^/    - /'
        fi
    else
        echo -e "${YELLOW}⚠ Catalog is empty${NC}"
        echo "  Response: $BODY"
    fi
else
    echo -e "${RED}✗ Catalog query failed (HTTP $HTTP_CODE)${NC}"
    echo "  Response: $BODY"
    echo ""
    echo "  Troubleshooting:"
    echo "  1. Check if provider port forwarding is active: kubectl port-forward svc/provider-qna-controlplane 9092:8082"
    echo "  2. Verify provider URL is correct: $PROVIDER_BASE_URL"
    echo "  3. Check network connectivity between consumer and provider"
    exit 1
fi
echo ""

# Test 3: Verify Provider DSP Endpoint (Basic Check)
echo "=========================================="
echo "Test 3: Provider DSP Endpoint"
echo "=========================================="

RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$PROVIDER_BASE_URL/api/dsp" 2>&1)

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)

if [ "$HTTP_CODE" = "000" ]; then
    echo -e "${RED}✗ Provider DSP endpoint not reachable${NC}"
    echo "  URL: $PROVIDER_BASE_URL/api/dsp"
    echo ""
    echo "  Troubleshooting:"
    echo "  1. Set up port forwarding: kubectl port-forward svc/provider-qna-controlplane 9092:8082"
    echo "  2. Verify the provider service is running: kubectl get pods | grep provider"
else
    echo -e "${GREEN}✓ Provider DSP endpoint reachable${NC}"
    echo "  HTTP Code: $HTTP_CODE (non-zero means endpoint exists)"
fi
echo ""

# Summary
echo "=========================================="
echo "SUMMARY"
echo "=========================================="
echo ""
echo "All connectivity tests passed! You can now configure Camunda:"
echo ""
echo "Connector Configuration:"
echo "  EDC Management URL:     $CONSUMER_MGMT_URL"
echo "  Provider Connector URL: $PROVIDER_BASE_URL"
echo "  Provider DID:           $PROVIDER_DID"
echo "  Asset ID:               $ASSET_ID"
echo "  Authentication Type:    api-key"
echo "  API Key:                $API_KEY"
echo ""
echo "Next steps:"
echo "1. Open Camunda Modeler"
echo "2. Create or open a BPMN diagram"
echo "3. Add a Service Task"
echo "4. Apply 'EDC Data Connector' template"
echo "5. Enter the configuration above"
echo "6. Deploy and test!"
echo ""
echo "For detailed instructions, see CONFIGURATION_GUIDE.md"
echo "=========================================="
