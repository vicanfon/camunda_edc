#!/bin/bash

# EDC Connector K8s Configuration Checker
# This script helps identify the correct URLs for your K8s deployment

echo "=========================================="
echo "EDC Connector K8s Setup Checker"
echo "=========================================="
echo ""

# Check 1: Is Camunda running in K8s?
echo "1. Checking if Camunda is running in Kubernetes..."
CAMUNDA_PODS=$(kubectl get pods -A 2>/dev/null | grep -i camunda | wc -l)
if [ "$CAMUNDA_PODS" -gt 0 ]; then
    echo "   ✓ Found $CAMUNDA_PODS Camunda pod(s) in K8s cluster"
    echo "   → Use INTERNAL K8s service names for configuration"
    CAMUNDA_LOCATION="internal"
else
    echo "   ✗ No Camunda pods found in K8s cluster"
    echo "   → Use INGRESS URLs for configuration"
    CAMUNDA_LOCATION="external"
fi
echo ""

# Check 2: List all ingresses
echo "2. Checking ingress routes..."
echo "   Available ingress routes:"
kubectl get ingress -A -o custom-columns=NAMESPACE:.metadata.namespace,NAME:.metadata.name,HOSTS:.spec.rules[*].host,PATHS:.spec.rules[*].http.paths[*].path 2>/dev/null | grep -E 'consumer|provider|NAMESPACE'
echo ""

# Check 3: List EDC services
echo "3. Checking EDC services..."
echo "   Consumer services:"
kubectl get svc -A 2>/dev/null | grep -i consumer | awk '{print "   - " $2 " (namespace: " $1 ", port: " $6 ")"}'
echo ""
echo "   Provider services:"
kubectl get svc -A 2>/dev/null | grep -i provider | awk '{print "   - " $2 " (namespace: " $1 ", port: " $6 ")"}'
echo ""

# Check 4: Test Management API
echo "4. Testing Consumer Management API..."
echo "   Testing via ingress (http://localhost/consumer/cp/management/v3/assets/request)..."

MGMT_RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
  "http://localhost/consumer/cp/management/v3/assets/request" \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: password" \
  -d '{"@context": {"@vocab": "https://w3id.org/edc/v0.0.1/ns/"}, "@type": "QuerySpec"}' 2>/dev/null)

if [ "$MGMT_RESPONSE" = "200" ]; then
    echo "   ✓ Management API accessible via ingress (HTTP $MGMT_RESPONSE)"
    MGMT_URL="http://localhost/consumer/cp/management"
elif [ "$MGMT_RESPONSE" = "401" ]; then
    echo "   ⚠ Management API found but authentication failed (HTTP $MGMT_RESPONSE)"
    echo "     Check your API key"
    MGMT_URL="http://localhost/consumer/cp/management"
else
    echo "   ✗ Management API not accessible via ingress (HTTP $MGMT_RESPONSE)"
    echo "     Trying port-forward method..."
    MGMT_URL="http://consumer-controlplane:8082/management"
fi
echo ""

# Check 5: Determine provider URL
echo "5. Determining Provider URL..."
if [ "$CAMUNDA_LOCATION" = "internal" ]; then
    echo "   → Using internal service name"
    PROVIDER_URL="http://provider-qna-controlplane:8082"
else
    echo "   → Need to check for provider ingress..."
    echo "   Looking for provider ingress routes..."
    PROVIDER_INGRESS=$(kubectl get ingress -A 2>/dev/null | grep -i 'provider.*qna\|provider.*dsp' | awk '{print $4}')

    if [ -n "$PROVIDER_INGRESS" ]; then
        echo "   ✓ Found provider ingress: $PROVIDER_INGRESS"
        PROVIDER_URL="http://localhost/provider-qna"
    else
        echo "   ✗ No provider ingress found"
        echo "   Options:"
        echo "     1. Set up port-forward: kubectl port-forward svc/provider-qna-controlplane 9092:8082"
        echo "        Then use: http://localhost:9092"
        echo "     2. Create an ingress for provider DSP endpoint"
        PROVIDER_URL="http://provider-qna-controlplane:8082"
    fi
fi
echo ""

# Summary
echo "=========================================="
echo "RECOMMENDED CONFIGURATION"
echo "=========================================="
echo ""
echo "Based on the checks above, use these settings in your Camunda connector:"
echo ""
echo "EDC Management URL:      $MGMT_URL"
echo "Provider Connector URL:  $PROVIDER_URL"
echo "Asset ID:                normal-asset-1"
echo "Authentication Type:     api-key"
echo "API Key:                 password"
echo ""

# Test command
echo "=========================================="
echo "TEST COMMAND"
echo "=========================================="
echo ""
echo "Run this command to test catalog query:"
echo ""

if [ "$CAMUNDA_LOCATION" = "internal" ]; then
    echo "kubectl run curl-test --rm -it --image=curlimages/curl --restart=Never -- \\"
    echo "  curl -X POST '$MGMT_URL/v3/catalog/request' \\"
else
    echo "curl -X POST '$MGMT_URL/v3/catalog/request' \\"
fi

echo "  -H 'Content-Type: application/json' \\"
echo "  -H 'X-Api-Key: password' \\"
echo "  -d '{"
echo "    \"@context\": [\"https://w3id.org/edc/connector/management/v0.0.1\"],"
echo "    \"@type\": \"CatalogRequest\","
echo "    \"counterPartyAddress\": \"$PROVIDER_URL/api/dsp\","
echo "    \"protocol\": \"dataspace-protocol-http\","
echo "    \"querySpec\": {\"offset\": 0, \"limit\": 50}"
echo "  }'"
echo ""

echo "=========================================="
echo "For more help, see K8S_CONFIGURATION.md"
echo "=========================================="
