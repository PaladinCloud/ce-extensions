# Variables
ARTIFACT_PATH=target/asset-sender-1.0-SNAPSHOT.jar
DIST_DIR := dist/svc-delta-engine
ARTIFACT_NAME=${DIST_DIR}/svc-delta-engine.jar
ZIP_NAME=${DIST_DIR}/svc-delta-engine.zip

package-all: build-and-copy zip-artifact
# Build, copy, zip, and move zip to dist in one step

build-and-copy: create-dist-dir
	cd services/svc-asset-delta-engine && mvn clean install -DskipTests=true -Dmaven.javadoc.skip=true && \
	cp $(ARTIFACT_PATH) ./../../$(ARTIFACT_NAME)

create-dist-dir:
	mkdir -p $(DIST_DIR)

zip-artifact:
	zip -j $(ZIP_NAME) $(ARTIFACT_NAME)