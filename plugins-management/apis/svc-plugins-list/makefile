build:
	cd src/ext;GOOS=linux GOARCH=arm64 go build -tags lambda.norpc -o bin/extensions/lambda-cache-layer main.go
package: build
	cd src/ext/bin;zip -r extension.zip extensions/ -x '**/.*' -x '**/__MACOSX'
deploy: build package
	cd src/ext/bin;aws lambda publish-layer-version  --layer-name 'svc-plugins-list-layer' --region us-east-1 --zip-file 'fileb://extension.zip'
