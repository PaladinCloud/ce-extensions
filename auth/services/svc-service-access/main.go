/*
 * Copyright (c) 2023 Paladin Cloud, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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
	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-lambda-go/lambda"
	"os"
	"os/signal"
	"partner-access-auth/service"
	"partner-access-auth/service/clients"
	"syscall"
)

var (
	Version string
)

func HandleRequest(request events.APIGatewayCustomAuthorizerRequest) (events.APIGatewayCustomAuthorizerResponse, error) {
	ctx, cancel := context.WithCancel(context.Background())
	configuration := clients.LoadConfigurationDetails(ctx)

	sigs := make(chan os.Signal, 1)
	signal.Notify(sigs, syscall.SIGTERM, syscall.SIGINT)
	go func() {
		s := <-sigs
		cancel()
		fmt.Printf("Request received: %+v\n", request)
		fmt.Printf("Exiting due to signal: %+v\n", s)
	}()

	return service.HandleLambdaRequest(ctx, request, configuration)
}

func main() {
	fmt.Println("Partner Access Auth Invoke - Started - Version: " + Version)
	lambda.Start(HandleRequest)
	fmt.Println("Partner Access Auth - Ended")
}
