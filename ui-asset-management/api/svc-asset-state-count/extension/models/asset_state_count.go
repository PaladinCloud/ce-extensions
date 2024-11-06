package models

type AssetStateCount struct {
	StateName string `json:"_assetState"`
	Count     int    `json:"count"`
}

type AssetStateCountData struct {
	AssetStateNameCounts []AssetStateCount `json:"assetStateNameCounts"`
}

type AssetStateCountResponse struct {
	Data    AssetStateCountData `json:"data"`
	Message string              `json:"message"`
}
