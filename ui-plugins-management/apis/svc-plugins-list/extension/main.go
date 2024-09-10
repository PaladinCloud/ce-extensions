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
	"svc-plugins-list-layer/clients"
	"svc-plugins-list-layer/extension"
	logger "svc-plugins-list-layer/logging"
	"svc-plugins-list-layer/server"
	"syscall"
)

var (
	log *logger.Logger

	httpServerClient *server.HttpServer

	extensionName    = filepath.Base(os.Args[0]) // extension name has to match the filename
	lambdaRuntimeAPI = os.Getenv("AWS_LAMBDA_RUNTIME_API")
	extensionClient  = extension.NewClient(lambdaRuntimeAPI)
	printPrefix      = fmt.Sprintf("[%s]", extensionName)

	port = "4567"
)

func init() {
	log = logger.NewLogger()
	log.Info("Initializing extension clients - ", extensionName)
}

func main() {
	ctx, cancel := context.WithCancel(context.Background())

	log.Info("Loading Configuration")
	configuration := clients.LoadConfigurationDetails(ctx)
	log.Info("Configuration loaded successfully!")

	log.Info("Initializing HTTP Server")
	httpServerClient = &server.HttpServer{
		Configuration:     configuration,
		PluginsListClient: clients.NewPluginsListClient(configuration),
	}
	log.Info("HTTP Server initialized successfully!")

	if !configuration.IsDebug {
		log.Info("Registering extension client", extensionName, lambdaRuntimeAPI)
		sigs := make(chan os.Signal, 1)
		signal.Notify(sigs, syscall.SIGTERM, syscall.SIGINT)
		go func() {
			s := <-sigs
			cancel()
			println(printPrefix, "Received", s)
			println(printPrefix, "Exiting")
		}()

		// Register the extension client with the Lambda runtime
		res, err := extensionClient.Register(ctx, extensionName)
		if err != nil {
			log.Info("Unable to register extension:", err)
		}

		log.Info("Client Registered:", res)
	}

	println("Starting Local HTTP Server")
	server.Start(port, httpServerClient)

	// Will block until shutdown event is received or cancelled via the context.
	processEvents(ctx)
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
