# Constants for each service directory
ASSET_VIOLATIONS_DIR := $(CURDIR)/api/svc-asset-violations
ASSET_NETWORK_RULES_DIR := $(CURDIR)/api/svc-asset-network-rules
ASSET_DETAILS_DIR := $(CURDIR)/api/svc-asset-details
ASSET_RELATED_ASSETS_DIR := $(CURDIR)/api/svc-asset-related-assets
ASSET_COUNT_DIR := $(CURDIR)/api/svc-asset-state-count
# Directory where all zip files will be moved/copied
DIST_DIR_VIOLATIONS := $(CURDIR)/dist/svc-asset-violations
DIST_DIR_NETWORK := $(CURDIR)/dist/svc-asset-network-rules
DIST_DIR_DETAILS := $(CURDIR)/dist/svc-asset-details
DIST_DIR_RELATED_ASSETS := $(CURDIR)/dist/svc-asset-related-assets
DIST_DIR_COUNT := $(CURDIR)/dist/svc-asset-state-count
# Run all packaging for the three services and move zip files to dist directory
package-all: package-violations package-network-rules package-details package-related-assets package-count move-zips

# Package svc-asset-violations service
package-violations:
	$(MAKE) -C $(ASSET_VIOLATIONS_DIR) package-all

# Package svc-asset-network-rules service
package-network-rules:
	$(MAKE) -C $(ASSET_NETWORK_RULES_DIR) package-all

# Package svc-asset-details service
package-details:
	$(MAKE) -C $(ASSET_DETAILS_DIR) package-all

# Package svc-asset-related-assets service
package-related-assets:
	$(MAKE) -C $(ASSET_RELATED_ASSETS_DIR) package-all

# Package svc-asset-count service
package-count:
	$(MAKE) -C $(ASSET_COUNT_DIR) package-all

# Move zip files to the dist directory after packaging is complete
move-zips: create-dist-dir
	@test -f $(ASSET_VIOLATIONS_DIR)/bin/service/lambda_function.zip || (echo "ERROR: $(ASSET_VIOLATIONS_DIR)/bin/service/lambda_function.zip not found" && exit 1)
	@test -f $(ASSET_VIOLATIONS_DIR)/bin/layers/bin/extension.zip || (echo "ERROR: $(ASSET_VIOLATIONS_DIR)/bin/layers/bin/extension.zip not found" && exit 1)
	@echo "Moving violations service files..."
	cp -v $(ASSET_VIOLATIONS_DIR)/bin/service/lambda_function.zip $(DIST_DIR_VIOLATIONS)/svc-asset-violations.zip
	cp -v $(ASSET_VIOLATIONS_DIR)/bin/layers/bin/extension.zip $(DIST_DIR_VIOLATIONS)/svc-asset-violations-extension.zip
	
	@test -f $(ASSET_NETWORK_RULES_DIR)/bin/service/lambda_function.zip || (echo "ERROR: $(ASSET_NETWORK_RULES_DIR)/bin/service/lambda_function.zip not found" && exit 1)
	@test -f $(ASSET_NETWORK_RULES_DIR)/bin/layers/bin/extension.zip || (echo "ERROR: $(ASSET_NETWORK_RULES_DIR)/bin/layers/bin/extension.zip not found" && exit 1)
	@echo "Moving Network port service files..."
	cp -v $(ASSET_NETWORK_RULES_DIR)/bin/service/lambda_function.zip $(DIST_DIR_NETWORK)/svc-asset-network-rules.zip
	cp -v $(ASSET_NETWORK_RULES_DIR)/bin/layers/bin/extension.zip $(DIST_DIR_NETWORK)/svc-asset-network-rules-extension.zip

	@test -f $(ASSET_DETAILS_DIR)/bin/service/lambda_function.zip || (echo "ERROR: $(ASSET_DETAILS_DIR)/bin/service/lambda_function.zip not found" && exit 1)
	@test -f $(ASSET_DETAILS_DIR)/bin/layers/bin/extension.zip || (echo "ERROR: $(ASSET_DETAILS_DIR)/bin/layers/bin/extension.zip not found" && exit 1)
	@echo "Moving asset details service files..."
	cp -v $(ASSET_DETAILS_DIR)/bin/service/lambda_function.zip $(DIST_DIR_DETAILS)/svc-asset-details.zip
	cp -v $(ASSET_DETAILS_DIR)/bin/layers/bin/extension.zip $(DIST_DIR_DETAILS)/svc-asset-details-extension.zip

	@test -f $(ASSET_RELATED_ASSETS_DIR)/bin/service/lambda_function.zip || (echo "ERROR: $(ASSET_RELATED_ASSETS_DIR)/bin/service/lambda_function.zip not found" && exit 1)
	@test -f $(ASSET_RELATED_ASSETS_DIR)/bin/layers/bin/extension.zip || (echo "ERROR: $(ASSET_RELATED_ASSETS_DIR)/bin/layers/bin/extension.zip not found" && exit 1)
	@echo "Moving asset related assets service files..."
	cp -v $(ASSET_RELATED_ASSETS_DIR)/bin/service/lambda_function.zip $(DIST_DIR_RELATED_ASSETS)/svc-asset-related-assets.zip
	cp -v $(ASSET_RELATED_ASSETS_DIR)/bin/layers/bin/extension.zip $(DIST_DIR_RELATED_ASSETS)/svc-asset-related-assets-extension.zip

	@test -f $(ASSET_COUNT_DIR)/bin/service/lambda_function.zip || (echo "ERROR: $(ASSET_COUNT_DIR)/bin/service/lambda_function.zip not found" && exit 1)
	@test -f $(ASSET_COUNT_DIR)/bin/layers/bin/extension.zip || (echo "ERROR: $(ASSET_COUNT_DIR)/bin/layers/bin/extension.zip not found" && exit 1)
	@echo "Moving asset count service files..."
	cp -v $(ASSET_COUNT_DIR)/bin/service/lambda_function.zip $(DIST_DIR_COUNT)/svc-asset-state-count.zip
	cp -v $(ASSET_COUNT_DIR)/bin/layers/bin/extension.zip $(DIST_DIR_COUNT)/svc-asset-state-count-extension.zip

# Create dist directory if it does not exist
create-dist-dir:
	@echo "Creating distribution directories..."
	@mkdir -p $(DIST_DIR_VIOLATIONS) || (echo "ERROR: Failed to create $(DIST_DIR_VIOLATIONS)" && exit 1)
	@mkdir -p $(DIST_DIR_NETWORK) || (echo "ERROR: Failed to create $(DIST_DIR_NETWORK)" && exit 1)
	@mkdir -p $(DIST_DIR_DETAILS) || (echo "ERROR: Failed to create $(DIST_DIR_DETAILS)" && exit 1)
	@mkdir -p $(DIST_DIR_RELATED_ASSETS) || (echo "ERROR: Failed to create $(DIST_DIR_RELATED_ASSETS)" && exit 1)
	@mkdir -p $(DIST_DIR_COUNT) || (echo "ERROR: Failed to create $(DIST_DIR_COUNT)" && exit 1)
	@echo "Distribution directories created successfully"
