module svc-asset-violations-layer

go 1.23.1

require (
	github.com/aws/aws-sdk-go v1.55.5
	github.com/elastic/go-elasticsearch/v7 v7.13.1 // DO NOT UPGRADE - WILL BREAK OPENSEARCH EXTENSION DUE TO HEADER CHECK
	github.com/go-chi/chi/v5 v5.1.0
	github.com/go-sql-driver/mysql v1.8.1
)

require github.com/georgysavva/scany v1.2.2

require (
	filippo.io/edwards25519 v1.1.0 // indirect
	github.com/jmespath/go-jmespath v0.4.0 // indirect
	github.com/stretchr/testify v1.8.4 // indirect
	golang.org/x/crypto v0.17.0 // indirect
	golang.org/x/sys v0.15.0 // indirect
	golang.org/x/text v0.14.0 // indirect
)
