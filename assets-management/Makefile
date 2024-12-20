# Variables
# Version of the services
VERSION := 1.0-SNAPSHOT
# Names of the services
SERVICES := svc-asset-delta-engine svc-asset-state
# Directory for distribution artifacts
DIST_DIR := dist

# Main target to package all services
package-all: $(addprefix package-, $(SERVICES))
	@echo "Packaging of all services completed successfully."

# Target to package a single service
package-%: build-and-copy-% zip-artifact-%
	@echo "Packaging of $* completed successfully."

# Build, copy, zip, and move zip to dist in one step for a service
build-and-copy-%: create-dist-dir-%
	$(MAKE) mvn-build SERVICE_NAME=$*
	cp services/$*/target/$*-${VERSION}.jar $(DIST_DIR)/$*/$*.jar 
	if [ $$? -ne 0 ]; then \
  		echo "Error: Failed to copy JAR file for $*"; \
  		exit 1; \
	fi
	@echo "Successfully copied JAR file for $*"

# Maven build target
.PHONY: mvn-build
mvn-build:
	@if [ -z "$(SERVICE_NAME)" ]; then \
		echo "Error: SERVICE_NAME is not set"; \
		exit 1; \
	fi
	cd services/$(SERVICE_NAME) || { echo "Error: Failed to change to service directory"; exit 1; }; \
	mvn clean install -B \
		-DskipTests=true \
		-Dmaven.javadoc.skip=true || { echo "Error: Maven build failed"; exit 1; }
	@if [ ! -f "services/$(SERVICE_NAME)/target/$(SERVICE_NAME)-$(VERSION).jar" ]; then \
		echo "Error: Maven build failed to create target/$(SERVICE_NAME)-$(VERSION).jar"; \
		exit 1; \
	fi

# Create distribution directory for a service if it doesn't exist
create-dist-dir-%:
	mkdir -p $(DIST_DIR)/$*

# Zip the artifact into the dist directory for a service
zip-artifact-%:
	@if [ ! -f "$(DIST_DIR)/$*/$*.jar" ]; then \
		echo "Error: $(DIST_DIR)/$*/$*.jar does not exist"; \
		exit 1; \
	fi
	zip -j $(DIST_DIR)/$*/$*.zip $(DIST_DIR)/$*/$*.jar || { \
		echo "Error: Failed to create zip file for $*"; \
		exit 1; \
	}
	@echo "Successfully created $(DIST_DIR)/$*/$*.zip"