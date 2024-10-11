module svc-asset-violations-layer

go 1.23.1

require (
	github.com/elastic/go-elasticsearch/v7 v7.13.1 // DO NOT UPGRADE - WILL BREAK OPENSEARCH EXTENSION DUE TO HEADER CHECK
	github.com/go-chi/chi/v5 v5.1.0
	github.com/go-sql-driver/mysql v1.8.1
)

require (
	github.com/aws/aws-sdk-go-v2 v1.32.2
	github.com/aws/aws-sdk-go-v2/config v1.27.43
	github.com/aws/aws-sdk-go-v2/credentials v1.17.41
	github.com/aws/aws-sdk-go-v2/feature/dynamodb/attributevalue v1.15.12
	github.com/aws/aws-sdk-go-v2/service/dynamodb v1.36.2
	github.com/aws/aws-sdk-go-v2/service/secretsmanager v1.34.2
	github.com/aws/aws-sdk-go-v2/service/sts v1.32.2
	github.com/georgysavva/scany v1.2.2
)

require (
	filippo.io/edwards25519 v1.1.0 // indirect
	github.com/aws/aws-sdk-go-v2/feature/ec2/imds v1.16.17 // indirect
	github.com/aws/aws-sdk-go-v2/internal/configsources v1.3.21 // indirect
	github.com/aws/aws-sdk-go-v2/internal/endpoints/v2 v2.6.21 // indirect
	github.com/aws/aws-sdk-go-v2/internal/ini v1.8.1 // indirect
	github.com/aws/aws-sdk-go-v2/service/dynamodbstreams v1.24.2 // indirect
	github.com/aws/aws-sdk-go-v2/service/internal/accept-encoding v1.12.0 // indirect
	github.com/aws/aws-sdk-go-v2/service/internal/endpoint-discovery v1.10.2 // indirect
	github.com/aws/aws-sdk-go-v2/service/internal/presigned-url v1.12.2 // indirect
	github.com/aws/aws-sdk-go-v2/service/sso v1.24.2 // indirect
	github.com/aws/aws-sdk-go-v2/service/ssooidc v1.28.2 // indirect
	github.com/aws/smithy-go v1.22.0 // indirect
	github.com/jmespath/go-jmespath v0.4.0 // indirect
	github.com/stretchr/testify v1.8.4 // indirect
	golang.org/x/crypto v0.17.0 // indirect
	golang.org/x/sys v0.15.0 // indirect
	golang.org/x/text v0.14.0 // indirect
)
