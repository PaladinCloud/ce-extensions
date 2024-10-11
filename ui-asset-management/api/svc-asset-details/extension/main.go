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
	httpServerClient *server.HttpServer
	port             = "4567"

	extensionName    = filepath.Base(os.Args[0]) // extension name has to match the filename
	lambdaRuntimeAPI = os.Getenv("AWS_LAMBDA_RUNTIME_API")
	extensionClient  = extension.NewClient(lambdaRuntimeAPI)
)

func main() {
	fmt.Printf("starting asset details extension - %s\n", extensionName)

	fmt.Println("loading configuration")
	configuration := clients.LoadConfigurationDetails()
	fmt.Println("configuration loaded successfully")

	startMain(configuration)
}

func startMain(configuration *clients.Configuration) {
	ctx, cancel := context.WithCancel(context.Background())

	fmt.Println("initializing http server client")
	httpServerClient = &server.HttpServer{
		AssetDetailsClient: clients.NewAssetDetailsClient(configuration),
	}
	fmt.Println("http server initialized successfully")

	if configuration.EnableExtension {
		fmt.Printf("registering extension client - %s | %s\n", extensionName, lambdaRuntimeAPI)
		sigs := make(chan os.Signal, 1)
		signal.Notify(sigs, syscall.SIGTERM, syscall.SIGINT)
		go func() {
			s := <-sigs
			cancel()
			fmt.Printf("received | %+v\n", s)
			fmt.Println("exiting")
		}()

		// Register the extension client with the Lambda runtime
		res, err := extensionClient.Register(ctx, extensionName)
		if err != nil {
			fmt.Errorf("unable to register extension: %+v\n", err)
		}

		fmt.Printf("client registered: %+v\n", res)

		// Will block until shutdown event is received or cancelled via the context.
		processEvents(ctx)
	}

	fmt.Printf("starting local http server on port: %s\n", port)
	server.Start(port, httpServerClient, configuration.EnableExtension)
}

func processEvents(ctx context.Context) {
	fmt.Println("starting processing events")
	for {
		select {
		case <-ctx.Done():
			fmt.Println("context done, exiting event loop")
			return
		default:
			fmt.Println("waiting for next event...")
			// Fetch the next event and check for errors.
			_, err := extensionClient.NextEvent(ctx)
			if err != nil {
				fmt.Errorf("error fetching next event: %v\n", err)
				return
			}
		}
	}
}
