# Constants for each service directory
ASSET_MANAGEMENT := assets-management
UI_ASSET_MANAGEMENT := ui-asset-management
UI_COMMON := ui-common

# Directory where all zip files will be moved/copied
DIST_DIR := dist

# Main target to package all services and move zip files to the dist directory
package-all: package-asset-management package-ui-asset-management package-ui-common move-zips
	@echo "Packaging completed for all services and artifacts moved to $(DIST_DIR)."

# Package assets-management service
package-asset-management:
	$(MAKE) -C $(ASSET_MANAGEMENT) package-all

# Package ui-asset-management service
package-ui-asset-management:
	$(MAKE) -C $(UI_ASSET_MANAGEMENT) package-all

# Package ui-common service
package-ui-common:
	$(MAKE) -C $(UI_COMMON) package-all

# Move zip files to the dist directory after packaging is complete
move-zips: create-dist-dir
	# Clean dist directory
	rm -rf $(DIST_DIR)/*

	# Copy ui-asset-management artifacts
	@if [ -d "$(UI_ASSET_MANAGEMENT)/$(DIST_DIR)" ]; then \
		mkdir -p $(DIST_DIR)/$(UI_ASSET_MANAGEMENT); \
		cp -r $(UI_ASSET_MANAGEMENT)/$(DIST_DIR)/* $(DIST_DIR)/; \
	else \
		echo "Warning: $(UI_ASSET_MANAGEMENT)/$(DIST_DIR) not found"; \
	fi

	# Copy ui-common artifacts
	@if [ -d "$(UI_COMMON)/$(DIST_DIR)" ]; then \
		mkdir -p $(DIST_DIR)/$(UI_COMMON); \
		cp -r $(UI_COMMON)/$(DIST_DIR)/* $(DIST_DIR)/; \
	else \
		echo "Warning: $(UI_COMMON)/$(DIST_DIR) not found"; \
	fi

	# Copy assets-management artifacts
	@if [ -d "$(ASSET_MANAGEMENT)/$(DIST_DIR)" ]; then \
		mkdir -p $(DIST_DIR)/$(ASSET_MANAGEMENT); \
		cp -r $(ASSET_MANAGEMENT)/$(DIST_DIR)/* $(DIST_DIR)/; \
	else \
		echo "Warning: $(ASSET_MANAGEMENT)/$(DIST_DIR) not found"; \
	fi

# Create dist directory if it does not exist
create-dist-dir:
	mkdir -p $(DIST_DIR)
