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

package models

import (
	"time"

	"github.com/aws/aws-sdk-go-v2/feature/dynamodb/attributevalue"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb/types"
)

type TenantFeatureFlags struct {
	FeatureFlags map[string]FeatureFlag `json:"feature_flags" dynamodbav:"feature_flags"`
}

type FeatureFlag struct {
	ExpirationDate *time.Time      `json:"expirationDate,omitempty" dynamodbav:"expirationDate,omitempty"`
	Metadata       FeatureMetadata `json:"metadata" dynamodbav:"metadata"`
	IsEnabled      bool            `json:"isEnabled" dynamodbav:"isEnabled"`
}

type FeatureMetadata struct {
	Customer          string `json:"customer" dynamodbav:"customer"`
	IsCustomerFeature bool   `json:"isCustomerFeature" dynamodbav:"isCustomerFeature"`
}

// UnmarshalDynamoDBAttributeValue implements the custom unmarshalling for FeatureFlag
func (f *FeatureFlag) UnmarshalDynamoDBAttributeValue(av types.AttributeValue) error {
	type Alias FeatureFlag
	aux := &struct {
		ExpirationDate string `dynamodbav:"expirationDate"`
		*Alias
	}{
		Alias: (*Alias)(f),
	}

	if err := attributevalue.Unmarshal(av, &aux); err != nil {
		return err
	}

	if aux.ExpirationDate != "" {
		t, err := time.Parse(time.RFC3339, aux.ExpirationDate)
		if err != nil {
			return err
		}
		f.ExpirationDate = &t
	}

	return nil
}
