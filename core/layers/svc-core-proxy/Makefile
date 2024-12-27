# Constants for directories
SERVICE_BIN_DIR := bin/service
EXTENSIONS_BIN_DIR := bin/layers/bin/extensions
EXTENSIONS_ZIP_DIR := bin/layers/bin/

LAMBDA_LAYER_NAME := svc-asset-details-layer

# Build the extension for Lambda
build-ext: dependencies
	cd extension; GOOS=linux GOARCH=arm64 go build -tags lambda.norpc -o ../$(EXTENSIONS_BIN_DIR)/$(LAMBDA_LAYER_NAME) main.go

# Package the extension
package-ext: build-ext
	cd $(EXTENSIONS_ZIP_DIR); zip -r extension.zip extensions/ -x '**/.*' -x '**/__MACOSX'

# Deploy the extension to AWS Lambda
deploy-ext: package-ext
	cd $(EXTENSIONS_ZIP_DIR); aws lambda publish-layer-version --layer-name '$(LAMBDA_LAYER_NAME)' --region us-east-1 --zip-file 'fileb://extension.zip'

# Package all
package-all: package-ext

# Dependencies: create required directories if they don't exist
dependencies:
	mkdir -p $(EXTENSIONS_BIN_DIR)