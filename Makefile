# Constants for each service directory
ASSET_MANAGEMENT := assets-management
UI_ASSET_MANAGEMENT := ui-asset-management
UI_COMMON := ui-common
DATA_MORPHER := data-morpher

# Directory where all zip files will be moved/copied
DIST_DIR := dist

# Main target to package all services and move zip files to the dist directory
package-all: package-asset-management package-ui-asset-management package-ui-common move-zips
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

# Package data-morpher service
package-data-morpher:
	@if [ ! -d "$(DATA_MORPHER)" ]; then \
		echo "Error: $(DATA_MORPHER) directory not found"; \
		exit 1; \
	elif [ ! -f "$(DATA_MORPHER)/Makefile" ]; then \
		echo "Error: $(DATA_MORPHER)/Makefile not found"; \
		exit 1; \
	else \
		$(MAKE) -C $(DATA_MORPHER) package-all; \
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

	# Copy data-morpher artifacts
	@if [ -d "$(DATA_MORPHER)/$(DIST_DIR)" ]; then \
		cp -r $(DATA_MORPHER)/$(DIST_DIR)/* $(DIST_DIR)/ || exit 1; \
	else \
		echo "Error: $(DATA_MORPHER)/$(DIST_DIR) not found"; \
		exit 1; \
	fi

# Create dist directory if it does not exist
create-dist-dir:
	mkdir -p $(DIST_DIR)
