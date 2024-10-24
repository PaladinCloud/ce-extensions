# Define variables
# Directory containing the Lambda function
LAMBDA_DIR := api/svc-options-method/service
# Name of the Lambda function file
LAMBDA_FILE := lambda_function.py
# Output zip file name
ZIP_FILE := svc-options-method.zip
# Distribution directory for packaged Lambda
DIST_DIR := dist/svc-options-method
# Default target
package-all: zip-lambda copy-zip

# Target to zip the lambda_function.py
zip-lambda:
	cd $(LAMBDA_DIR) && zip -r $(ZIP_FILE) $(LAMBDA_FILE)
	@if [ ! -f "$(LAMBDA_DIR)/$(LAMBDA_FILE)" ]; then \
		echo "Error: Lambda function file not found: $(LAMBDA_DIR)/$(LAMBDA_FILE)"; \
		exit 1; \
	fi
	@rm -f "$(LAMBDA_DIR)/$(ZIP_FILE)"
	cd $(LAMBDA_DIR) && zip -r $(ZIP_FILE) $(LAMBDA_FILE) || { echo "Error: Failed to create zip file"; exit 1; }
# Target to copy the zip file to the current directory
copy-zip: create-dist-dir
	@if [ ! -f "$(LAMBDA_DIR)/$(ZIP_FILE)" ]; then \
		echo "Error: Zip file not found: $(LAMBDA_DIR)/$(ZIP_FILE)"; \
		exit 1; \
	fi
	cp -r $(LAMBDA_DIR)/$(ZIP_FILE) ./${DIST_DIR}/ || { echo "Error: Failed to copy zip file"; exit 1; }

create-dist-dir:
	mkdir -p $(DIST_DIR)