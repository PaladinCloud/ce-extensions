# Constants for each service directory
ASSET_MANAGEMENT := assets-management
UI_ASSET_MANAGEMENT := ui-asset-management
UI_COMMON := ui-common

# Directory where all zip files will be moved/copied
DIST_DIR := dist

# Run all packaging for the three services and move zip files to dist directory
package-all: package-asset-management package-ui-asset-management package-ui-common move-zips

# Package asset-violations service
package-asset-management:
	$(MAKE) -C $(ASSET_MANAGEMENT) package-all

# Package asset-network-rules service
package-ui-asset-management:
	$(MAKE) -C $(UI_ASSET_MANAGEMENT) package-all

# Package asset-details service
package-ui-common:
	$(MAKE) -C $(UI_COMMON) package-all

# Move zip files to the dist directory after packaging is complete
move-zips: create-dist-dir
	# Copy ui-asset-management artifacts
	cp -r $(UI_ASSET_MANAGEMENT)/$(DIST_DIR)/* $(DIST_DIR)/
	
	# Copy ui-common artifacts
	cp -r $(UI_COMMON)/$(DIST_DIR)/* $(DIST_DIR)/

	# Copy assets-management artifacts
	cp -r $(ASSET_MANAGEMENT)/$(DIST_DIR)/* $(DIST_DIR)/

create-dist-dir:
	mkdir -p $(DIST_DIR)