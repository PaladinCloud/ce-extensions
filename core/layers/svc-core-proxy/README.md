# Lambda Proxy Extension

## Overview

The Lambda Proxy Extension is a powerful tool designed to enhance the functionality and efficiency of AWS Lambda functions in a multi-tenant environment. This extension serves as a bridge between the Lambda application business layer and the storage persistence layer, providing seamless access to common multi-tenant configurations and resources.

### UML Sequence Diagram
    Lambda->>Extension: Invoke Extension
    Extension->>DynamoDB: Fetch Tenant Configuration
    DynamoDB-->>Extension: Return Configuration
    Extension->>SecretsManager: Retrieve RDS Credentials
    SecretsManager-->>Extension: Return Credentials
    Extension->>RDS: Connect and Fetch Tags
    RDS-->>Extension: Return Mandatory Tags
    Extension->>OpenSearch: Fetch Domain Details
    OpenSearch-->>Extension: Return Domain Properties
    Extension->>Lambda: Provide Tenant Details

## Key Features

1. **Automatic Injection**: The Proxy Extension is automatically injected into all Lambda functions through the CI/CD pipeline by default, ensuring consistent implementation across your serverless architecture.

2. **Multi-Tenant Configuration Retrieval**: The extension facilitates the retrieval of common multi-tenant configurations, including:
    - Tenant Feature Flags V2
    - UI Tenant Feature Flags V2
    - OpenSearch URL
    - RDS Credentials and Host Information

3. **Business-Persistence Layer Bridge**: The Proxy Extension is designed to be expandable, serving as a bridge between the Lambda application's business logic and the storage persistence layer. This design promotes better separation of concerns and enhances overall application architecture.

## How It Works

The Proxy Extension integrates with the Lambda Extensions API to provide additional functionality to your Lambda functions. It runs as a separate process within the Lambda execution environment, allowing it to perform tasks before and after function invocations.

1. **Registration**: The extension registers itself with the Lambda service during the function initialization phase.

2. **Configuration Retrieval**: Before each function invocation, the extension retrieves the necessary multi-tenant configurations from the appropriate sources (e.g., DynamoDB, Secrets Manager).

3. **Data Injection**: The retrieved configurations are made available to the Lambda function through environment variables or a predefined interface.

4. **Persistence Layer Interaction**: As the extension evolves, it will handle interactions with the persistence layer, abstracting away the complexities of data storage and retrieval from the main Lambda function code.

## Benefits

- **Improved Performance**: By retrieving and caching common configurations, the extension reduces the need for repeated API calls, improving overall function performance.
- **Enhanced Security**: Centralized management of sensitive information like RDS credentials improves security posture.
- **Code Simplification**: Lambda functions can focus on business logic, with the extension handling cross-cutting concerns.
- **Consistency**: Ensures consistent access to multi-tenant configurations across all Lambda functions.
- **Scalability**: Facilitates easier management of resources and configurations in a multi-tenant environment.

## Future Enhancements

The Proxy Extension is designed to be expandable. Future enhancements may include:
- Caching mechanisms for frequently accessed data
- Advanced logging and monitoring capabilities
- Integration with additional AWS services
- Custom plugin support for organization-specific needs