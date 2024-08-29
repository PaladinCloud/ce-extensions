package clients

import (
	"context"
	"fmt"
	"slices"
	"strings"
	"svc-asset-details-layer/models"
)

type AssetDetailsClient struct {
	rdsClient *RdsClient
}

const (
	docIDKeyword            = "_docid.keyword"
	docTypeKeyword          = "docType.keyword"
	cloudType               = "_cloudType"
	resourceId              = "_resourceid"
	docId                   = "_docid"
	underscoreDiscoveryDate = "_discoverydate"
	discoveryDate           = "discoverydate"
	firstDiscoveredOn       = "firstdiscoveredon"
	entity                  = "_entity"
	entityType              = "_entitytype"
	loadDate                = "_loaddate"
	assetRiskScore          = "assetRiskScore"
)

func NewAssetDetailsClient(configuration *Configuration) *AssetDetailsClient {
	return &AssetDetailsClient{rdsClient: NewRdsClient(configuration)}
}

func (c *AssetDetailsClient) GetAssetDetails(ctx context.Context, ag string, targetType string, assetId string) (*models.AssetDetails, error) {
	if len(strings.TrimSpace(assetId)) == 0 {
		return nil, fmt.Errorf("assetId must be present")
	}

	result, err := FetchAssetDetails(ctx, ag, targetType, assetId, 1)

	if err != nil {
		return nil, err
	}

	sourceArr := (*result)["hits"].(map[string]interface{})["hits"].([]interface{})
	if len(sourceArr) > 0 {
		assetDetails := sourceArr[0].(map[string]interface{})

		var assetDetailsFlat = make(map[string]interface{})
		flatten("", &assetDetails, &assetDetailsFlat)

		source := assetDetailsFlat[cloudType]
		fieldsToBeSkipped := []string{resourceId, docId, underscoreDiscoveryDate, discoveryDate, firstDiscoveredOn, entity, entityType, loadDate, assetRiskScore}
		attributes := []map[string]interface{}{}
		tagsKvPairs := map[string]string{}

		for key, value := range assetDetailsFlat {
			//modify Cloud Source to desired format
			if key == cloudType && value != nil {
				value, _ = c.rdsClient.GetPluginName(ctx, value.(string))
			}

			if !slices.Contains(fieldsToBeSkipped, key) && value != nil && value != "" {
				tagsPrefix := "tags."
				if strings.HasPrefix(key, tagsPrefix) {
					tagKey := key[len(tagsPrefix):]
					if source == "gcp" {
						tagKey = strings.ToLower(tagKey)
					}
					tagsKvPairs[tagKey] = value.(string)
				} else {
					attribute := make(map[string]interface{})
					attribute["name"] = key
					attribute["value"] = []string{value.(string)}
					attribute["category"] = ""
					attributes = append(attributes, attribute)
				}
			}
		}

		return &models.AssetDetails{Tags: tagsKvPairs, Attributes: attributes}, nil
	} else {
		return nil, fmt.Errorf("asset detials not found for assetId: %s", assetId)
	}

}

func flatten(prefix string, source *map[string]interface{}, destination *map[string]interface{}) {
	if len(prefix) > 0 {
		prefix += "."
	}
	for k, v := range *source {
		switch child := v.(type) {
		case map[string]interface{}:
			flatten(prefix+k, &child, destination)
		case []interface{}:
			(*destination)["list"] = v
		default:
			(*destination)[prefix+k] = v
		}
	}
}
