# Kubernetes Deployment Configuration Guide

## MVD on Kubernetes Setup

This guide helps configure the EDC connector for MVD running on Kubernetes.

## Architecture Overview

In a K8s deployment, you typically have:

1. **Ingress Controller** - Routes external traffic to internal services
2. **Consumer Control Plane** - Management API (accessible via ingress)
3. **Provider Control Plane** - DSP endpoint (may be internal-only or via ingress)

## Determining Your Configuration

### Step 1: Check Where Camunda is Running

Run this command to see if Camunda is in the same K8s cluster:

```bash
kubectl get pods -A | grep camunda
```

- **Found pods?** → Camunda is INSIDE K8s (use Option 2 below)
- **No pods?** → Camunda is OUTSIDE K8s (use Option 1 below)

### Step 2: Find Provider DSP Endpoint

Check if the provider has an ingress route:

```bash
kubectl get ingress -A
```

Look for an ingress that routes to the provider control plane.

## Configuration Options

### Option 1: Camunda Outside K8s (External Access)

Use ingress URLs for everything:

**Connector Configuration:**
```yaml
EDC Management URL: http://localhost/consumer/cp/management
Provider Connector URL: http://localhost/provider/dsp
Asset ID: normal-asset-1
Authentication Type: api-key
API Key: password
```

**Note:** Replace `/provider/dsp` with the actual ingress path for the provider DSP endpoint.

**Test Command:**
```bash
curl -X POST "http://localhost/consumer/cp/management/v3/catalog/request" \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: password" \
  -d '{
    "@context": ["https://w3id.org/edc/connector/management/v0.0.1"],
    "@type": "CatalogRequest",
    "counterPartyAddress": "http://localhost/provider/dsp/api/dsp",
    "protocol": "dataspace-protocol-http"
  }'
```

### Option 2: Camunda Inside K8s (Internal Access)

Use K8s service names:

**Connector Configuration:**
```yaml
EDC Management URL: http://consumer-controlplane:8082/management
Provider Connector URL: http://provider-qna-controlplane:8082
Asset ID: normal-asset-1
Authentication Type: api-key
API Key: password
```

**Test Command (from inside a pod):**
```bash
kubectl exec -it <camunda-pod> -- curl -X POST \
  "http://consumer-controlplane:8082/management/v3/catalog/request" \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: password" \
  -d '{
    "@context": ["https://w3id.org/edc/connector/management/v0.0.1"],
    "@type": "CatalogRequest",
    "counterPartyAddress": "http://provider-qna-controlplane:8082/api/dsp",
    "protocol": "dataspace-protocol-http"
  }'
```

## Common K8s Issues and Solutions

### Issue 1: "Connection refused" or DNS errors

**Cause:** Trying to access internal K8s services from outside the cluster.

**Solution:**
- Use ingress URLs for external access
- Or add port-forward: `kubectl port-forward svc/consumer-controlplane 8082:8082`

### Issue 2: "404 Not Found" on Management API

**Cause:** Incorrect ingress path or missing `/management` suffix.

**Solution:**
```bash
# Check your ingress configuration
kubectl get ingress consumer-controlplane -o yaml

# Look for the path configuration
# The management API should be at: /management/v3/*
```

### Issue 3: Provider Not Reachable from Consumer

**Cause:** Network policies or missing ingress for provider DSP.

**Solutions:**

1. **Check Network Policies:**
   ```bash
   kubectl get networkpolicies -A
   ```

2. **Verify Provider Service:**
   ```bash
   kubectl get svc | grep provider
   ```

3. **Test Provider DSP from Consumer Pod:**
   ```bash
   kubectl exec -it <consumer-pod> -- curl http://provider-qna-controlplane:8082/api/dsp
   ```

## Port Forwarding for Local Testing

If Camunda runs locally but you want to access K8s services:

```bash
# Forward consumer management API
kubectl port-forward svc/consumer-controlplane 9193:8082

# Forward provider DSP
kubectl port-forward svc/provider-qna-controlplane 9092:8082

# Then use these URLs in connector:
# EDC Management URL: http://localhost:9193/management
# Provider URL: http://localhost:9092
```

## Checking Your Current Setup

### 1. List All Ingresses
```bash
kubectl get ingress -A -o wide
```

Look for paths like:
- `/consumer/cp` → Consumer control plane
- `/provider/cp` or `/provider-qna/cp` → Provider control plane

### 2. List All Services
```bash
kubectl get svc -A | grep -E 'consumer|provider'
```

Identify service names and ports.

### 3. Test Management API Accessibility
```bash
# From outside cluster (via ingress)
curl -X POST "http://localhost/consumer/cp/management/v3/assets/request" \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: password" \
  -d '{"@context": {"@vocab": "https://w3id.org/edc/v0.0.1/ns/"}, "@type": "QuerySpec"}'

# From inside cluster (via service)
kubectl run curl-test --rm -it --image=curlimages/curl --restart=Never -- \
  curl -X POST "http://consumer-controlplane:8082/management/v3/assets/request" \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: password" \
  -d '{"@context": {"@vocab": "https://w3id.org/edc/v0.0.1/ns/"}, "@type": "QuerySpec"}'
```

## Environment Variables from Your Postman

Based on your K8s environment:

| Variable | Value | Usage |
|----------|-------|-------|
| HOST | `http://localhost/consumer/cp` | Consumer Management API (via ingress) |
| PROVIDER_DSP_URL | `http://provider-qna-controlplane:8082` | Provider DSP (internal K8s service) |
| CATALOG_SERVER_DSP_URL | `http://provider-catalog-server-controlplane:8082` | Catalog Server DSP (internal) |

**For Connector Configuration:**

If **Camunda is outside K8s**, you need to find or create ingress routes for the provider DSP endpoint. The internal service name `provider-qna-controlplane:8082` won't be accessible from outside the cluster.

## Next Steps

1. **Determine where Camunda runs** (inside or outside K8s)
2. **Check ingress configuration** for provider DSP endpoint
3. **Test Management API** access with curl commands above
4. **Configure connector** with appropriate URLs based on deployment location
5. **Test catalog query** to verify provider connectivity

For additional help, check `TROUBLESHOOTING.md` in this repository.
