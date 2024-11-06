# Variables
# Version of the svc-asset-delta-engine service
VERSION := 1.0-SNAPSHOT
# Name of the service
SERVICE_NAME := svc-asset-delta-engine
# Path to the built artifact
ARTIFACT_PATH := target/$(SERVICE_NAME)-$(VERSION).jar
# Directory for distribution artifacts
DIST_DIR := dist/$(SERVICE_NAME)
# Final JAR name
ARTIFACT_NAME := $(DIST_DIR)/$(SERVICE_NAME).jar
# Final ZIP name
ZIP_NAME := $(DIST_DIR)/$(SERVICE_NAME).zip

# Main target to package the service
package-all: build-and-copy zip-artifact
	@echo "Packaging completed successfully."

# Build, copy, zip, and move zip to dist in one step
build-and-copy: create-dist-dir
	$(MAKE) mvn-build
	cp services/$(SERVICE_NAME)/$(ARTIFACT_PATH) $(ARTIFACT_NAME)

# Maven build target
.PHONY: mvn-build
mvn-build:
	cd services/$(SERVICE_NAME) || { echo "Error: Failed to change to service directory"; exit 1; }; \
	mvn clean install -B \
		-DskipTests=true \
		-Dmaven.javadoc.skip=true || { echo "Error: Maven build failed"; exit 1; }
	@if [ ! -f "services/$(SERVICE_NAME)/$(ARTIFACT_PATH)" ]; then \
		echo "Error: Maven build failed to create $(ARTIFACT_PATH)"; \
		exit 1; \
	fi

# Create distribution directory if it doesn't exist
create-dist-dir:
	mkdir -p $(DIST_DIR)

# Zip the artifact into the dist directory
zip-artifact:
	@if [ ! -f "$(ARTIFACT_NAME)" ]; then \
		echo "Error: $(ARTIFACT_NAME) does not exist"; \
		exit 1; \
	fi
	zip -j $(ZIP_NAME) $(ARTIFACT_NAME) || { echo "Error: Failed to create zip file"; exit 1; }
	@echo "Successfully created $(ZIP_NAME)"
