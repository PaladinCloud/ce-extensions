# Makefile for building, packaging, and deploying the extension

# Variables
EXT_DIR := extension
BIN_DIR := $(EXT_DIR)/bin
ZIP_FILE := $(BIN_DIR)/extension.zip
LAYER_NAME := svc-plugins-list-layer
REGION := us-east-1
GOOS := linux
GOARCH := arm64
GO_BUILD_FLAGS := -tags lambda.norpc

# Phony targets
.PHONY: all build-ext package-ext deploy-ext clean

# Default target
all: deploy-ext

# Build the extension
build-ext:
	@echo "Building extension..."
	go build $(GO_BUILD_FLAGS) -o $(BIN_DIR)/extensions/lambda-cache-layer $(EXT_DIR)/main.go

# Package the extension
package-ext: build-ext
	@echo "Packaging extension..."
	zip -r $(ZIP_FILE) -j $(BIN_DIR)/extensions/* -x '**/.*' -x '**/__MACOSX'

# Deploy the extension
deploy-ext: package-ext
	@echo "Deploying extension..."
	aws lambda publish-layer-version --layer-name $(LAYER_NAME) --region $(REGION) --zip-file 'fileb://$(ZIP_FILE)'

# Clean up build artifacts
clean:
	@echo "Cleaning up..."
	rm -rf $(BIN_DIR)/extensions/ $(ZIP_FILE)
