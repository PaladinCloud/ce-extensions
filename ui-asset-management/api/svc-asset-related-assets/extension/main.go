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
	"log"
	"os"
	"os/signal"
	"path/filepath"
	"svc-asset-related-assets-layer/clients"
	"svc-asset-related-assets-layer/extension"
	"svc-asset-related-assets-layer/server"

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
	log.Printf("starting extension - %s\n", extensionName)

	log.Println("loading configuration")
	configuration, err := clients.LoadConfigurationDetails()
	if err != nil {
		log.Fatalf("failed to load aws configuratio %+v", err)
	}

	err2 := startMain(configuration)
	if err2 != nil {
		log.Fatalf("failed to start main %+v", err2)
	}
}

func startMain(configuration *clients.Configuration) error {
	ctx, cancel := context.WithCancel(context.Background())

	log.Println("initializing http server")
	relatedAssetsClient, err := clients.NewRelatedAssetsClient(ctx, configuration)
	if err != nil {
		return err
	}

	httpServerClient = &server.HttpServer{
		Configuration:       configuration,
		RelatedAssetsClient: relatedAssetsClient,
	}

	log.Printf("starting http server on port [%s]\n", port)
	server.Start(port, httpServerClient, configuration.EnableExtension)

	if configuration.EnableExtension {
		log.Printf("registering extension client [%s] [%s]\n", extensionName, lambdaRuntimeAPI)
		sigs := make(chan os.Signal, 1)
		signal.Notify(sigs, syscall.SIGTERM, syscall.SIGINT)
		go func() {
			s := <-sigs
			cancel()
			log.Println("received", s)
			log.Println("exiting")
		}()

		// Register the extension client with the Lambda runtime
		res, err2 := extensionClient.Register(ctx, extensionName)
		if err2 != nil {
			return fmt.Errorf("failed to register extension client: %w", err2)
		}

		log.Printf("registered extension client: %+v\n", res)
		// Will block until shutdown event is received or cancelled via the context.
		processEvents(ctx)
	}

	return nil
}

func processEvents(ctx context.Context) error {
	log.Println("starting processing events")
	for {
		select {
		case <-ctx.Done():
			log.Println("context done, exiting event loop")
			return nil
		default:
			log.Println("waiting for next event...")
			// Fetch the next event and check for errors.
			_, err := extensionClient.NextEvent(ctx)
			if err != nil {
				return fmt.Errorf("error fetching next event: %w", err)
			}
		}
	}
}
