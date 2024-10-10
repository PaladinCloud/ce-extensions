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
	//log              *logger.Logger
	httpServerClient *server.HttpServer
	port             = "4567"

	extensionName    = filepath.Base(os.Args[0]) // extension name has to match the filename
	lambdaRuntimeAPI = os.Getenv("AWS_LAMBDA_RUNTIME_API")
	extensionClient  = extension.NewClient(lambdaRuntimeAPI)
	printPrefix      = fmt.Sprintf("[%s]", extensionName)
)

func init() {

}

func main() {
	//log = logger.NewLogger(printPrefix)
	fmt.Println("Starting Asset Details Extension", extensionName)

	fmt.Println("Loading Configuration")
	configuration := clients.LoadConfigurationDetails()
	fmt.Println("Configuration loaded successfully!")

	startMain(configuration)
}

func startMain(configuration *clients.Configuration) {
	ctx, cancel := context.WithCancel(context.Background())

	fmt.Println("Initializing HTTP Server Client")
	httpServerClient = &server.HttpServer{
		AssetDetailsClient: clients.NewAssetDetailsClient(configuration),
		//Log:                log,
	}
	fmt.Println("HTTP Server initialized successfully!")

	fmt.Println("Starting Local HTTP Server")
	server.Start(port, httpServerClient)

	if configuration.EnableExtension {
		fmt.Println("Registering extension client", extensionName, lambdaRuntimeAPI)
		sigs := make(chan os.Signal, 1)
		signal.Notify(sigs, syscall.SIGTERM, syscall.SIGINT)
		go func() {
			s := <-sigs
			cancel()
			fmt.Println("Received", s)
			fmt.Println("Exiting")
		}()

		// Register the extension client with the Lambda runtime
		res, err := extensionClient.Register(ctx, extensionName)
		if err != nil {
			fmt.Println("Unable to register extension:", err)
		}

		fmt.Println("Client Registered:", res)

		// Will block until shutdown event is received or cancelled via the context.
		processEvents(ctx)
	}
}

func processEvents(ctx context.Context) {
	fmt.Println("Starting Processing events")
	for {
		select {
		case <-ctx.Done():
			fmt.Println("Context done, exiting event loop")
			return
		default:
			fmt.Println("Waiting for next event...")
			// Fetch the next event and check for errors.
			_, err := extensionClient.NextEvent(ctx)
			if err != nil {
				fmt.Println("Error fetching next event:", err)
				return
			}
		}
	}
}
