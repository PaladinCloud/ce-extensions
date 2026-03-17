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
	"svc-core-proxy-layer/clients"
	"svc-core-proxy-layer/extension"
	"svc-core-proxy-layer/server"
	"time"

	"syscall"
)

var (
	httpServerClient *server.HttpServer
	port             = "4568"
	timeout          = 5 * time.Second

	extensionName    = filepath.Base(os.Args[0]) // extension name has to match the filename
	lambdaRuntimeAPI = os.Getenv("AWS_LAMBDA_RUNTIME_API")
	extensionClient  = extension.NewClient(lambdaRuntimeAPI)
)

func main() {
	configuration, err := clients.LoadConfigurationDetails()
	if err != nil {
		log.Fatalf("failed to load aws configuration %+v", err)
	}

	err2 := startMain(configuration)
	if err2 != nil {
		log.Fatalf("failed to start main %+v", err2)
	}
}

func startMain(configuration *clients.Configuration) error {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	proxyClient, err := clients.NewProxyClient(ctx, configuration)
	if err != nil {
		return err
	}

	httpServerClient = &server.HttpServer{
		ProxyClient: proxyClient,
	}

	server.Start(port, httpServerClient, configuration.EnableExtension)

	if configuration.EnableExtension {
		sigs := make(chan os.Signal, 1)
		signal.Notify(sigs, syscall.SIGTERM, syscall.SIGINT)
		go func() {
			s := <-sigs
			cancel()
			log.Println("received", s)
			log.Println("exiting")
		}()

		// Register the extension client with the Lambda runtime
		_, err2 := extensionClient.Register(ctx, extensionName)
		if err2 != nil {
			return fmt.Errorf("failed to register extension client: %w", err2)
		}

		// Will block until shutdown event is received or cancelled via the context.
		if err := processEvents(ctx); err != nil {
			log.Printf("Error in event processing: %v", err)
		}
	}

	return nil
}

func processEvents(ctx context.Context) error {
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
