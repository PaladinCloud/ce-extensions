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
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package clients

import (
	"context"
	"encoding/json"
	"fmt"
	"github.com/google/uuid"
	"log"
	"svc-asset-state-count-layer/models"

	"github.com/aws/aws-sdk-go-v2/credentials/stscreds"
	"github.com/aws/aws-sdk-go-v2/service/sts"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/secretsmanager"
)

type SecretsClient struct {
	secretsClient *secretsmanager.Client
}

var (
	versionStage string = "AWSCURRENT"
)

// NewSecretsClient initializes the SecretsManager client
func NewSecretsClient(ctx context.Context, useAssumeRole bool, assumeRoleArn, region string) (*SecretsClient, error) {
	// Load the default configuration with region
	cfg, err := config.LoadDefaultConfig(ctx, config.WithRegion(region))
	if err != nil {
		return nil, fmt.Errorf("error loading AWS config %w", err)
	}

	var svc *secretsmanager.Client
	if useAssumeRole {
		// Create an STS client
		stsClient := sts.NewFromConfig(cfg)

		// Assume the role using STS
		creds := stscreds.NewAssumeRoleProvider(stsClient, assumeRoleArn, func(o *stscreds.AssumeRoleOptions) {
			o.RoleSessionName = fmt.Sprintf("SecretsSession-%s", uuid.New())
		})

		// Create a new configuration with the assumed role credentials
		assumedCfg := aws.Config{
			Credentials: aws.NewCredentialsCache(creds),
			Region:      region,
		}

		// Initialize the Secrets Manager client
		svc = secretsmanager.NewFromConfig(assumedCfg)
	} else {
		// Initialize the Secrets Manager client
		svc = secretsmanager.NewFromConfig(cfg)
	}

	log.Println("initialized secrets client")
	return &SecretsClient{
		secretsClient: svc,
	}, nil
}

// GetRdsSecret retrieves the RDS secret from AWS Secrets Manager
func (r *SecretsClient) GetRdsSecret(ctx context.Context, secretIdPrefix, tenantId string) (*models.RdsSecret, error) {
	// Create the secretId using the prefix and tenantId
	secretId := fmt.Sprintf("%s%s", secretIdPrefix, tenantId)
	log.Printf("getting rds secrets for [%s]\n", secretId)

	// Prepare the input for retrieving the secret
	input := &secretsmanager.GetSecretValueInput{
		SecretId:     aws.String(secretId),
		VersionStage: aws.String(versionStage),
	}

	// Call Secrets Manager to retrieve the secret
	result, err := r.secretsClient.GetSecretValue(ctx, input)
	if err != nil {
		return nil, fmt.Errorf("failed to retrieve secret value for [%s] %w", secretId, err)
	}

	// Check if the result contains a secret string
	var secretString string
	if result.SecretString != nil {
		secretString = *result.SecretString
	} else {
		return nil, fmt.Errorf("secret string is nil for secret [%s]", secretId)
	}

	// Unmarshal the secret string into the RdsSecret struct
	var secretData models.RdsSecret
	err = json.Unmarshal([]byte(secretString), &secretData)
	if err != nil {
		return nil, fmt.Errorf("failed to unmarshal secret data for [%s] %w", secretId, err)
	}

	return &secretData, nil
}
