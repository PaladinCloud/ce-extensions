# Constants for each service directory
CORE_PROXY_LAYER_DIR := $(CURDIR)/layers/svc-core-proxy

# Directory where all zip files will be moved/copied
DIST_DIR_CORE_PROXY := $(CURDIR)/dist/svc-core-proxy

# Run all packaging for the three services and move zip files to dist directory
package-all: package-core-proxy move-zips

# Package svc-core-proxy service
package-core-proxy:
	$(MAKE) -C $(CORE_PROXY_LAYER_DIR) package-all

# Move zip files to the dist directory after packaging is complete
move-zips: create-dist-dir
	@test -f $(CORE_PROXY_LAYER_DIR)/bin/layers/bin/extension.zip || (echo "ERROR: $(CORE_PROXY_LAYER_DIR)/bin/extension.zip not found" && exit 1)
	@echo "Moving core proxy service files..."
	cp -v $(CORE_PROXY_LAYER_DIR)/bin/layers/bin/extension.zip $(DIST_DIR_CORE_PROXY)/svc-core-proxy-extension.zip

# Create dist directory if it does not exist
create-dist-dir:
	@mkdir -p $(DIST_DIR_CORE_PROXY)

# Clean up dist directory
clean:
	@rm -rf $(DIST_DIR_CORE_PROXY)