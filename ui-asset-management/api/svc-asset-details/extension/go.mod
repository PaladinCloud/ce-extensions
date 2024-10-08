module svc-asset-details-layer

go 1.23.1

require (
	github.com/aws/aws-sdk-go v1.55.5
	github.com/elastic/go-elasticsearch/v7 v7.13.1 // DO NOT UPGRADE - WILL BREAK OPENSEARCH EXTENSION DUE TO HEADER CHECK
	github.com/georgysavva/scany v1.2.2
	github.com/go-chi/chi/v5 v5.1.0
	github.com/go-sql-driver/mysql v1.8.1
)

require (
	filippo.io/edwards25519 v1.1.0 // indirect
	github.com/jmespath/go-jmespath v0.4.0 // indirect
)
