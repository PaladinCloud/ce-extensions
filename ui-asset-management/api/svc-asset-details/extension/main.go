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
	logger "svc-asset-details-layer/logging"
	"svc-asset-details-layer/server"

	"syscall"
)

var (
	log              *logger.Logger
	httpServerClient *server.HttpServer
	port             = "4567"

	extensionName    = filepath.Base(os.Args[0]) // extension name has to match the filename
	lambdaRuntimeAPI = os.Getenv("AWS_LAMBDA_RUNTIME_API")
	extensionClient  = extension.NewClient(lambdaRuntimeAPI)
	printPrefix      = fmt.Sprintf("[%s]", extensionName)
)

func init() {
	log = logger.NewLogger(printPrefix)
	log.Info("Initializing extension clients - ", extensionName)
}

func main() {
	log.Info("Loading Configuration")
	configuration := clients.LoadConfigurationDetails()
	log.Info("Configuration loaded successfully!")

	startMain(configuration)
}

func startMain(configuration *clients.Configuration) {
	ctx, cancel := context.WithCancel(context.Background())

	log.Info("Initializing HTTP Server Client")
	httpServerClient = &server.HttpServer{
		AssetDetailsClient: clients.NewAssetDetailsClient(configuration, log),
		Log:                log,
	}
	log.Info("HTTP Server initialized successfully!")

	log.Info("Starting Local HTTP Server")
	server.Start(port, httpServerClient)

	if configuration.EnableExtension {
		log.Info("Registering extension client", extensionName, lambdaRuntimeAPI)
		sigs := make(chan os.Signal, 1)
		signal.Notify(sigs, syscall.SIGTERM, syscall.SIGINT)
		go func() {
			s := <-sigs
			cancel()
			log.Info("Received", s)
			log.Info("Exiting")
		}()

		// Register the extension client with the Lambda runtime
		res, err := extensionClient.Register(ctx, extensionName)
		if err != nil {
			log.Info("Unable to register extension:", err)
		}

		log.Info("Client Registered:", res)

		// Will block until shutdown event is received or cancelled via the context.
		processEvents(ctx)
	}
}

func processEvents(ctx context.Context) {
	log.Info("Starting Processing events")
	for {
		select {
		case <-ctx.Done():
			log.Info("Context done, exiting event loop")
			return
		default:
			log.Info("Waiting for next event...")
			// Fetch the next event and check for errors.
			_, err := extensionClient.NextEvent(ctx)
			if err != nil {
				log.Info("Error fetching next event:", err)
				return
			}
		}
	}
}
