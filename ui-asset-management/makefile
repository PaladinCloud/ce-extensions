# Constants for each service directory
ASSET_VIOLATIONS_DIR := $(CURDIR)/api/svc-asset-violations
ASSET_NETWORK_RULES_DIR := $(CURDIR)/api/svc-asset-network-rules
ASSET_DETAILS_DIR := $(CURDIR)/api/svc-asset-details

# Directory where all zip files will be moved/copied
DIST_DIR_VIOLATIONS := $(CURDIR)/dist/svc-asset-violations
DIST_DIR_NETWORK := $(CURDIR)/dist/svc-asset-network-rules
DIST_DIR_DETAILS := $(CURDIR)/dist/svc-asset-details
# Run all packaging for the three services and move zip files to dist directory
package-all: package-violations package-network-rules package-details move-zips

# Package svc-asset-violations service
package-violations:
	$(MAKE) -C $(ASSET_VIOLATIONS_DIR) package-all

# Package svc-asset-network-rules service
package-network-rules:
	$(MAKE) -C $(ASSET_NETWORK_RULES_DIR) package-all

# Package svc-asset-details service
package-details:
	$(MAKE) -C $(ASSET_DETAILS_DIR) package-all

# Move zip files to the dist directory after packaging is complete
move-zips: create-dist-dir
	@test -f $(ASSET_VIOLATIONS_DIR)/bin/service/lambda_function.zip || (echo "Violations lambda zip not found" && exit 1)
	@test -f $(ASSET_VIOLATIONS_DIR)/bin/layers/bin/extension.zip || (echo "Violations extension zip not found" && exit 1)
	cp $(ASSET_VIOLATIONS_DIR)/bin/service/lambda_function.zip $(DIST_DIR_VIOLATIONS)/svc-asset-violations.zip
	cp $(ASSET_VIOLATIONS_DIR)/bin/layers/bin/extension.zip $(DIST_DIR_VIOLATIONS)/svc-asset-violations-extension.zip

	@test -f $(ASSET_NETWORK_RULES_DIR)/bin/service/lambda_function.zip || (echo "Network Rules lambda zip not found" && exit 1)
	@test -f $(ASSET_NETWORK_RULES_DIR)/bin/layers/bin/extension.zip || (echo "Network Rules extension zip not found" && exit 1)
	cp $(ASSET_NETWORK_RULES_DIR)/bin/service/lambda_function.zip $(DIST_DIR_NETWORK)/svc-asset-network-rules.zip
	cp $(ASSET_NETWORK_RULES_DIR)/bin/layers/bin/extension.zip $(DIST_DIR_NETWORK)/svc-asset-network-rules-extension.zip

	@test -f $(ASSET_DETAILS_DIR)/bin/service/lambda_function.zip || (echo "Details lambda zip not found" && exit 1)
	@test -f $(ASSET_DETAILS_DIR)/bin/layers/bin/extension.zip || (echo "Details extension zip not found" && exit 1)
	cp $(ASSET_DETAILS_DIR)/bin/service/lambda_function.zip $(DIST_DIR_DETAILS)/svc-asset-details.zip
	cp $(ASSET_DETAILS_DIR)/bin/layers/bin/extension.zip $(DIST_DIR_DETAILS)/svc-asset-details-extension.zip

# Create dist directory if it does not exist
create-dist-dir:
	mkdir -p $(DIST_DIR_VIOLATIONS)
	mkdir -p $(DIST_DIR_NETWORK)
	mkdir -p $(DIST_DIR_DETAILS)