build:
	GOOS=linux GOARCH=arm64 go build -tags lambda.norpc -o bin/extensions/lambda-cache-layer main.go
package: build
	cd bin;zip -r extension.zip extensions/ -x '**/.*' -x '**/__MACOSX'
deploy: build package
	cd bin;aws lambda publish-layer-version  --layer-name 'svc-asset-details-layer' --region us-east-1 --zip-file 'fileb://extension.zip'
