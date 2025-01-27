# Constants for each service directory
ASSET_MANAGEMENT := assets-management
UI_ASSET_MANAGEMENT := ui-asset-management
UI_COMMON := ui-common
CORE_PROXY := core
INFRA_MONITORING := infra-monitoring

# Directory where all zip files will be moved/copied
DIST_DIR := dist

# Main target to package all services and move zip files to the dist directory
package-all: package-asset-management package-ui-asset-management package-ui-common package-core-proxy package-infra-monitoring move-zips
	@echo "Packaging completed for all services and artifacts moved to $(DIST_DIR)."

# Package assets-management service
package-asset-management:
	@if [ ! -d "$(ASSET_MANAGEMENT)" ]; then \
		echo "Error: $(ASSET_MANAGEMENT) directory not found"; \
		exit 1; \
	elif [ ! -f "$(ASSET_MANAGEMENT)/Makefile" ]; then \
		echo "Error: $(ASSET_MANAGEMENT)/Makefile not found"; \
		exit 1; \
	else \
		$(MAKE) -C $(ASSET_MANAGEMENT) package-all; \
	fi

# Package ui-asset-management service
package-ui-asset-management:
	@if [ ! -d "$(UI_ASSET_MANAGEMENT)" ]; then \
		echo "Error: $(UI_ASSET_MANAGEMENT) directory not found"; \
		exit 1; \
	elif [ ! -f "$(UI_ASSET_MANAGEMENT)/Makefile" ]; then \
		echo "Error: $(UI_ASSET_MANAGEMENT)/Makefile not found"; \
		exit 1; \
	else \
		$(MAKE) -C $(UI_ASSET_MANAGEMENT) package-all; \
	fi

# Package ui-common service
package-ui-common:
	@if [ ! -d "$(UI_COMMON)" ]; then \
		echo "Error: $(UI_COMMON) directory not found"; \
		exit 1; \
	elif [ ! -f "$(UI_COMMON)/Makefile" ]; then \
		echo "Error: $(UI_COMMON)/Makefile not found"; \
		exit 1; \
	else \
		$(MAKE) -C $(UI_COMMON) package-all; \
	fi

# Package core-proxy service
package-core-proxy:
	@if [ ! -d "$(CORE_PROXY)" ]; then \
		echo "Error: $(CORE_PROXY) directory not found"; \
		exit 1; \
	elif [ ! -f "$(CORE_PROXY)/Makefile" ]; then \
		echo "Error: $(CORE_PROXY)/Makefile not found"; \
		exit 1; \
	else \
		$(MAKE) -C $(CORE_PROXY) package-all; \
	fi

# Package infra-monitoring service
package-infra-monitoring:
	@if [ ! -d "$(INFRA_MONITORING)" ]; then \
		echo "Error: $(INFRA_MONITORING) directory not found"; \
		exit 1; \
	elif [ ! -f "$(INFRA_MONITORING)/Makefile" ]; then \
		echo "Error: $(INFRA_MONITORING)/Makefile not found"; \
		exit 1; \
	else \
		$(MAKE) -C $(INFRA_MONITORING) package-all; \
	fi

# Move zip files to the dist directory after packaging is complete
move-zips: create-dist-dir
	# Clean dist directory with safety check
	@if [ -z "$(DIST_DIR)" ]; then \
		echo "Error: DIST_DIR is not set"; \
		exit 1; \
	else \
		rm -rf $(DIST_DIR)/*; \
	fi

	# Copy ui-asset-management artifacts
	@if [ -d "$(UI_ASSET_MANAGEMENT)/$(DIST_DIR)" ]; then \
		cp -r $(UI_ASSET_MANAGEMENT)/$(DIST_DIR)/* $(DIST_DIR)/ || exit 1; \
	else \
		echo "Error: $(UI_ASSET_MANAGEMENT)/$(DIST_DIR) not found"; \
		exit 1; \
	fi

	# Copy ui-common artifacts
	@if [ -d "$(UI_COMMON)/$(DIST_DIR)" ]; then \
		cp -r $(UI_COMMON)/$(DIST_DIR)/* $(DIST_DIR)/ || exit 1; \
	else \
		echo "Error: $(UI_COMMON)/$(DIST_DIR) not found"; \
		exit 1; \
	fi

	# Copy assets-management artifacts
	@if [ -d "$(ASSET_MANAGEMENT)/$(DIST_DIR)" ]; then \
		cp -r $(ASSET_MANAGEMENT)/$(DIST_DIR)/* $(DIST_DIR)/ || exit 1; \
	else \
		echo "Error: $(ASSET_MANAGEMENT)/$(DIST_DIR) not found"; \
		exit 1; \
	fi

	# Copy core-proxy artifacts
	@if [ -d "$(CORE_PROXY)/$(DIST_DIR)" ]; then \
		cp -r $(CORE_PROXY)/$(DIST_DIR)/* $(DIST_DIR)/ || exit 1; \
	else \
		echo "Error: $(CORE_PROXY)/$(DIST_DIR) not found"; \
		exit 1; \
	fi

	# Copy infra-monitoring artifacts
	@if [ -d "$(INFRA_MONITORING)/$(DIST_DIR)" ]; then \
		cp -r $(INFRA_MONITORING)/$(DIST_DIR)/* $(DIST_DIR)/ || exit 1; \
	else \
		echo "Error: $(INFRA_MONITORING)/$(DIST_DIR) not found"; \
		exit 1; \
	fi

# Create dist directory if it does not exist
create-dist-dir:
	mkdir -p $(DIST_DIR)
