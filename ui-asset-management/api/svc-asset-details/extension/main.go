/*
 * Copyright (c) 2024 Paladin Cloud, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package main

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"os/signal"
	"path/filepath"
	"svc-asset-details-layer/clients"
	"svc-asset-details-layer/extension"
	"svc-asset-details-layer/server"

	"syscall"
)

var (
	httpConfig       *server.HttpServer
	extensionName    = filepath.Base(os.Args[0]) // extension name has to match the filename
	lambdaRuntimeAPI = os.Getenv("AWS_LAMBDA_RUNTIME_API")
	extensionClient  = extension.NewClient(lambdaRuntimeAPI)
	printPrefix      = fmt.Sprintf("[%s]", extensionName)
)

func init() {
	println(printPrefix, "Initializing extension clients")
}

func main() {
	ctx, cancel := context.WithCancel(context.Background())
	configuration := clients.LoadConfigurationDetails(ctx)

	httpConfig = &server.HttpServer{
		Configuration:      configuration,
		AssetDetailsClient: clients.NewAssetDetailsClient(configuration),
	}

	sigs := make(chan os.Signal, 1)
	signal.Notify(sigs, syscall.SIGTERM, syscall.SIGINT)
	go func() {
		s := <-sigs
		cancel()
		println(printPrefix, "Received", s)
		println(printPrefix, "Exiting")
	}()

	println(printPrefix, "Registering extension client", extensionName, lambdaRuntimeAPI)

	res, err := extensionClient.Register(ctx, extensionName)
	if err != nil {
		println(printPrefix, "Unable to register extension:", prettyPrint(err))
	}

	println(printPrefix, "Client Registered:", prettyPrint(res))

	println("hello world! Starting Local HTTP Server")
	server.Start("4567", httpConfig)

	// Will block until shutdown event is received or cancelled via the context.
	processEvents(ctx)
}

func processEvents(ctx context.Context) {
	for {
		select {
		case <-ctx.Done():

			return
		default:
			_, err := extensionClient.NextEvent(ctx)
			if err != nil {
				println(printPrefix, "Error:", err)
				return
			}
		}
	}
}

func prettyPrint(v interface{}) string {
	data, err := json.MarshalIndent(v, "", "\t")
	if err != nil {
		return ""
	}
	return string(data)
}
