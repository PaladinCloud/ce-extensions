# Constants for directories
SERVICE_BIN_DIR := bin/service
EXTENSIONS_BIN_DIR := bin/layers/bin/extensions
EXTENSIONS_ZIP_DIR := bin/layers/bin/
DESCRIPTION := "Asset state count service layer"

LAMBDA_LAYER_NAME := svc-asset-state-count

# Build the extension for Lambda
build-ext: dependencies
	cd extension; GOOS=linux GOARCH=arm64 go build -tags lambda.norpc -o ../$(EXTENSIONS_BIN_DIR)/$(LAMBDA_LAYER_NAME) main.go

# Package the extension
package-ext: build-ext
	cd $(EXTENSIONS_ZIP_DIR); zip -r extension.zip extensions/ -x '**/.*' -x '**/__MACOSX'

# Deploy the extension to AWS Lambda
deploy-ext: build-ext package-ext
	cd $(EXTENSIONS_ZIP_DIR); aws lambda publish-layer-version --layer-name '$(LAMBDA_LAYER_NAME)' --region us-east-1 --zip-file 'fileb://extension.zip'

# Package the Lambda function
package: dependencies
	zip $(SERVICE_BIN_DIR)/lambda_function.zip service/lambda_function.py -x '**/.*' -x '**/__MACOSX'

# Package all
package-all: package package-ext

# Dependencies: create required directories if they don't exist
dependencies:
	mkdir -p $(SERVICE_BIN_DIR)
	mkdir -p $(EXTENSIONS_BIN_DIR)
