module svc-core-proxy-layer

go 1.23.1

require (
	github.com/elastic/go-elasticsearch/v7 v7.17.10 // DO NOT UPGRADE - WILL BREAK OPENSEARCH EXTENSION DUE TO HEADER CHECK
	github.com/georgysavva/scany v1.2.2
	github.com/go-chi/chi/v5 v5.2.0
	github.com/go-sql-driver/mysql v1.8.1
)

require (
	github.com/aws/aws-sdk-go-v2 v1.32.7
	github.com/aws/aws-sdk-go-v2/config v1.28.7
	github.com/aws/aws-sdk-go-v2/credentials v1.17.48
	github.com/aws/aws-sdk-go-v2/feature/dynamodb/attributevalue v1.15.22
	github.com/aws/aws-sdk-go-v2/service/dynamodb v1.38.1
	github.com/aws/aws-sdk-go-v2/service/secretsmanager v1.34.8
	github.com/aws/aws-sdk-go-v2/service/sts v1.33.3
	github.com/google/uuid v1.6.0
)

require (
	filippo.io/edwards25519 v1.1.0 // indirect
	github.com/aws/aws-sdk-go-v2/feature/ec2/imds v1.16.22 // indirect
	github.com/aws/aws-sdk-go-v2/internal/configsources v1.3.26 // indirect
	github.com/aws/aws-sdk-go-v2/internal/endpoints/v2 v2.6.26 // indirect
	github.com/aws/aws-sdk-go-v2/internal/ini v1.8.1 // indirect
	github.com/aws/aws-sdk-go-v2/service/dynamodbstreams v1.24.10 // indirect
	github.com/aws/aws-sdk-go-v2/service/internal/accept-encoding v1.12.1 // indirect
	github.com/aws/aws-sdk-go-v2/service/internal/endpoint-discovery v1.10.7 // indirect
	github.com/aws/aws-sdk-go-v2/service/internal/presigned-url v1.12.7 // indirect
	github.com/aws/aws-sdk-go-v2/service/sso v1.24.8 // indirect
	github.com/aws/aws-sdk-go-v2/service/ssooidc v1.28.7 // indirect
	github.com/aws/smithy-go v1.22.1 // indirect
	github.com/jmespath/go-jmespath v0.4.0 // indirect
)
