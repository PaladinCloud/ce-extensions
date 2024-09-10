build-ext:
	cd extension;GOOS=linux GOARCH=arm64 go build -tags lambda.norpc -o bin/extensions/lambda-cache-layer main.go
package-ext: build-ext
	cd extension/bin;zip -r extension.zip extensions/ -x '**/.*' -x '**/__MACOSX'
deploy-ext: build-ext package-ext
	cd extension/bin;aws lambda publish-layer-version  --layer-name 'svc-plugins-list-layer' --region us-east-1 --zip-file 'fileb://extension.zip'
