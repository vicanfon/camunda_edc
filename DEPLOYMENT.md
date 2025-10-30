# Deployment Guide for EDC Data Connector

## Deployment Options

### Option 1: Camunda 8 SaaS

1. **Build the connector**
   ```bash
   mvn clean package
   ```

2. **Upload to Camunda Console**
   - Navigate to Console → Your Cluster → Connectors
   - Click "Upload Connector"
   - Select `target/edc-data-connector-1.0.0.jar`

3. **Configure Secrets**
   - Go to Console → Organization Settings → Secrets
   - Add secret: `EDC_API_KEY` with your EDC API key value

4. **Install Element Template**
   - In Web Modeler, go to your project
   - Click "Connector templates" → "Upload"
   - Upload `element-templates/edc-data-connector.json`

### Option 2: Camunda 8 Self-Managed (Docker)

1. **Build the connector**
   ```bash
   mvn clean package
   ```

2. **Add to Connector Runtime**
   
   Create a `docker-compose.override.yml`:
   ```yaml
   version: '3'
   services:
     connectors:
       volumes:
         - ./target/edc-data-connector-1.0.0.jar:/opt/app/edc-data-connector-1.0.0.jar
       environment:
         - EDC_API_KEY=your-api-key-here
   ```

3. **Restart connector runtime**
   ```bash
   docker-compose up -d connectors
   ```

### Option 3: Camunda 8 Self-Managed (Kubernetes)

1. **Build and push to registry**
   ```bash
   mvn clean package
   # Upload JAR to your artifact repository or include in custom image
   ```

2. **Update Helm values**
   ```yaml
   connectors:
     extraVolumes:
       - name: custom-connectors
         configMap:
           name: custom-connector-jars
     extraVolumeMounts:
       - name: custom-connectors
         mountPath: /opt/app/custom
     env:
       - name: EDC_API_KEY
         valueFrom:
           secretKeyRef:
             name: edc-secrets
             key: api-key
   ```

3. **Create ConfigMap with JAR**
   ```bash
   kubectl create configmap custom-connector-jars \
     --from-file=edc-data-connector.jar=target/edc-data-connector-1.0.0.jar
   ```

4. **Apply changes**
   ```bash
   helm upgrade camunda camunda/camunda-platform -f values.yaml
   ```

## Verification

After deployment, verify the connector is loaded:

1. Check connector runtime logs:
   ```bash
   # Docker
   docker logs <connector-container> | grep "EDC"
   
   # Kubernetes
   kubectl logs <connector-pod> | grep "EDC"
   ```

2. You should see:
   ```
   INFO  Discovered connector: EDC Data Connector
   INFO  Registered connector type: io.camunda:edc-connector:1
   ```

3. In Modeler, the "EDC Data Connector" should appear in the connector template dropdown

## Troubleshooting

### Connector not appearing in Modeler
- Verify JAR is in the correct classpath
- Check connector runtime logs for errors
- Ensure element template is installed

### Authentication errors
- Verify `EDC_API_KEY` is set correctly
- Check secrets configuration in Console (SaaS)
- Ensure environment variables are passed to container (Self-Managed)

### Connection issues
- Verify EDC connector URLs are reachable from connector runtime
- Check network policies in Kubernetes
- Test connectivity: `curl http://your-edc:9193/management/v3/catalog/request`
