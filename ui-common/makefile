# Define variables
LAMBDA_DIR=./api/svc-options-method/service
LAMBDA_FILE=lambda_function.py
ZIP_FILE=svc-options-method.zip
DIST_DIR := dist/svc-options-method
# Default target
package-all: zip-lambda copy-zip

# Target to zip the lambda_function.py
zip-lambda:
	cd $(LAMBDA_DIR) && zip -r $(ZIP_FILE) $(LAMBDA_FILE)

# Target to copy the zip file to the current directory
copy-zip: create-dist-dir
	cp -r $(LAMBDA_DIR)/$(ZIP_FILE) ./${DIST_DIR}/

create-dist-dir:
	mkdir -p $(DIST_DIR)