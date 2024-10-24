package models

type RelatedAsset struct {
	AssetTypeName string `json:"assetTypeName"`
	AssetId       string `json:"assetId"`
	AssetType     string `json:"assetType"`
	ResourceId    string `json:"resourceId"`
}

type RelatedAssets struct {
	AllRelatedAssets *[]RelatedAsset `json:"relatedAssets"`
}

type Response struct {
	Data    *RelatedAssets `json:"data"`
	Message string         `json:"message"`
}
