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

package service

import (
	"context"
	"fmt"
	"github.com/aws/aws-lambda-go/events"
	"partner-access-auth/service/clients"
	"partner-access-auth/utils/jwt"
)

func HandleLambdaRequest(ctx context.Context, request events.APIGatewayCustomAuthorizerRequest, config *clients.Configuration) (events.APIGatewayCustomAuthorizerResponse, error) {
	fmt.Printf("Request received: %+v\n", request)
	token := request.AuthorizationToken
	if token == "" {
		fmt.Printf("Missing access token\n")
		return CreateDenyAllPolicy("user", request.MethodArn), nil
	}

	isValid, claims, err := jwt.ValidateToken(ctx, token, config.JwksURL, config.Audience, config.Issuer)
	if err != nil {
		fmt.Printf("Error getting authorization: %+v\n", err)
		return CreateDenyAllPolicy("user", request.MethodArn), nil
	}

	if !isValid {
		fmt.Printf("User is not authorized\n")
		return CreateDenyAllPolicy("user", request.MethodArn), nil
	}

	// to hide the tenantId from the client, we will use the accessId claim
	tenantId, err := jwt.GetClaim(claims, "custom:accessId")
	if tenantId == "" {
		fmt.Printf("Missing accessId\n")
		return CreateDenyAllPolicy("user", request.MethodArn), nil
	}

	fmt.Printf("User is authorized\n")
	return CreateAllowAllPolicy("user", request.MethodArn, tenantId), nil
}

func CreateAllowAllPolicy(principalID, methodArn, tenantId string) events.APIGatewayCustomAuthorizerResponse {
	return events.APIGatewayCustomAuthorizerResponse{
		PrincipalID: principalID,
		PolicyDocument: events.APIGatewayCustomAuthorizerPolicy{
			Version: "2012-10-17",
			Statement: []events.IAMPolicyStatement{
				{
					Action:   []string{"execute-api:Invoke"},
					Effect:   "Allow",
					Resource: []string{methodArn},
				},
			},
		},
		Context: map[string]interface{}{
			"tenantId": tenantId,
		},
	}
}

func CreateDenyAllPolicy(principalID string, methodArn string) events.APIGatewayCustomAuthorizerResponse {
	return events.APIGatewayCustomAuthorizerResponse{
		PrincipalID: principalID,
		PolicyDocument: events.APIGatewayCustomAuthorizerPolicy{
			Version: "2012-10-17",
			Statement: []events.IAMPolicyStatement{
				{
					Action:   []string{"execute-api:Invoke"},
					Effect:   "Deny",
					Resource: []string{methodArn},
				},
			},
		},
	}
}
