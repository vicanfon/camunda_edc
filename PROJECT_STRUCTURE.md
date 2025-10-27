# EDC Connector Project Structure

```
edc-connector/
├── pom.xml                              # Maven build configuration
├── README.md                            # Complete documentation
├── QUICKSTART.md                        # Quick start guide
├── .gitignore                           # Git ignore rules
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── io/camunda/connector/edc/
│   │   │       ├── EdcConnectorFunction.java      # Main connector class
│   │   │       ├── model/
│   │   │       │   ├── EdcConnectorRequest.java   # Request model
│   │   │       │   └── EdcConnectorResponse.java  # Response model
│   │   │       └── service/
│   │   │           └── EdcService.java            # EDC workflow logic
│   │   │
│   │   └── resources/
│   │       └── META-INF/
│   │           └── services/
│   │               └── io.camunda.connector.api.outbound.OutboundConnectorFunction
│   │                                               # Service loader config
│   │
│   └── test/
│       └── java/
│           └── io/camunda/connector/edc/
│               └── EdcConnectorFunctionTest.java  # Unit tests
│
└── element-templates/
    └── edc-connector.json               # Camunda Modeler template
```

## Key Files Description

### Core Implementation

- **EdcConnectorFunction.java**: Entry point for the connector, implements `OutboundConnectorFunction`
- **EdcService.java**: Contains the complete EDC workflow implementation (catalog query, contract negotiation, transfer)
- **EdcConnectorRequest.java**: Input model with validation for connector parameters
- **EdcConnectorResponse.java**: Output model containing the retrieved data and metadata

### Configuration

- **pom.xml**: Maven dependencies and build configuration including the Connector SDK
- **edc-connector.json**: Element template for Camunda Modeler with all connector properties
- **META-INF/services/...**: Java ServiceLoader configuration for automatic connector discovery

### Documentation

- **README.md**: Complete documentation with installation, usage, and troubleshooting
- **QUICKSTART.md**: 5-minute getting started guide

### Testing

- **EdcConnectorFunctionTest.java**: Unit tests covering validation and error scenarios

## Build Artifacts

After running `mvn clean package`, you'll get:

```
target/
├── edc-connector-1.0.0.jar                    # Regular JAR
└── edc-connector-1.0.0-with-dependencies.jar  # Uber JAR (deploy this one)
```

## Deployment Files

To deploy the connector, you need:

1. **For Connector Runtime**: `target/edc-connector-1.0.0-with-dependencies.jar`
2. **For Modeler**: `element-templates/edc-connector.json`

That's it! These two files are all you need to use the connector in Camunda 8.8.
