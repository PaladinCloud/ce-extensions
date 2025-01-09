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

package clients

import (
	"context"
	"encoding/json"
	"fmt"
	"github.com/google/uuid"
	"log"
	"svc-core-proxy-layer/models"

	"github.com/aws/aws-sdk-go-v2/credentials/stscreds"
	"github.com/aws/aws-sdk-go-v2/service/sts"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/secretsmanager"
)

type SecretsClient struct {
	secretsClient *secretsmanager.Client
	prefixId      string
}

var (
	versionStage string = "AWSCURRENT"
)

// NewSecretsClient initializes the SecretsManager client
func NewSecretsClient(ctx context.Context, useAssumeRole bool, assumeRoleArn, region, prefixId string) (*SecretsClient, error) {
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
		prefixId:      prefixId,
	}, nil
}

// GetTenantSecretData retrieves any secret from AWS Secrets Manager and returns it as a JSON object
func (r *SecretsClient) GetTenantSecretData(ctx context.Context, tenantId, secretName string) (map[string]interface{}, error) {
	secretString, err := r.getTenantSecretString(ctx, tenantId, secretName)
	if err != nil {
		return nil, err
	}

	var secretData map[string]interface{}
	err = json.Unmarshal([]byte(secretString), &secretData)
	if err != nil {
		return nil, fmt.Errorf("failed to unmarshal secret data for [%s][%s] %w", tenantId, secretName, err)
	}

	return secretData, nil
}

// GetTenantRdsSecret retrieves the RDS secret from AWS Secrets Manager
func (r *SecretsClient) GetTenantRdsSecret(ctx context.Context, tenantId string) (*models.RdsSecret, error) {
	secretString, err := r.getTenantSecretString(ctx, tenantId, "")
	if err != nil {
		return nil, err
	}

	// Unmarshal the secret string into the RdsSecret struct
	var secretData models.RdsSecret
	err = json.Unmarshal([]byte(secretString), &secretData)
	if err != nil {
		return nil, fmt.Errorf("failed to unmarshal secret data for [%s][%s] %w", tenantId, err)
	}

	return &secretData, nil
}

// getTenantSecretString retrieves any secret from AWS Secrets Manager
func (r *SecretsClient) getTenantSecretString(ctx context.Context, tenantId, secretName string) (string, error) {
	// Create the secretId using the prefix and tenantId
	secretId := fmt.Sprintf("%s%s", r.prefixId, tenantId)
	log.Printf("getting secret for [%s]\n", secretId)
	if secretName != "" {
		secretId = fmt.Sprintf("%s/%s", secretId, secretName)
	}

	return r.getSecret(ctx, secretId)
}

// GetSecret retrieves any secret from AWS Secrets Manager
func (r *SecretsClient) getSecret(ctx context.Context, secretId string) (string, error) {
	log.Printf("getting secret for [%s]\n", secretId)

	// Prepare the input for retrieving the secret
	input := &secretsmanager.GetSecretValueInput{
		SecretId:     aws.String(secretId),
		VersionStage: aws.String(versionStage),
	}

	// Call Secrets Manager to retrieve the secret
	result, err := r.secretsClient.GetSecretValue(ctx, input)
	if err != nil {
		return "", fmt.Errorf("failed to retrieve secret value for [%s] %w", secretId, err)
	}

	// Check if the result contains a secret string
	if result.SecretString != nil {
		return *result.SecretString, nil
	}

	return "", fmt.Errorf("secret string is nil for secret [%s]", secretId)
}
