# Variables
SERVICES := svc-terminate-long-running-ec2
DIST_DIR := dist

# Main target to package all services
package-all: $(addprefix package-, $(SERVICES))
	@echo "Packaging of all services completed successfully."

# Target to package a single service
package-%: create-dist-dir
	zip -j $(DIST_DIR)/$*.zip $*/lambda_function.py
	@echo "Packaged $*/lambda_function.py into $(DIST_DIR)/$*.zip"

# Create the distribution directory if it doesn't exist
create-dist-dir:
	mkdir -p $(DIST_DIR)

# Clean up generated artifacts
clean:
	rm -rf $(DIST_DIR)
	@echo "Cleaned up distribution artifacts."