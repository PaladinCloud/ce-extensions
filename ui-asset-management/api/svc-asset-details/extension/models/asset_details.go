package models

type AssetDetails struct {
	AccountId       string            `json:"accountId"`
	AccountName     string            `json:"accountName"`
	Source          string            `json:"source"`
	SourceName      string            `json:"SourceName"`
	TargetType      string            `json:"targetType"`
	TargetTypeName  string            `json:"targetTypeName"`
	Region          string            `json:"region"`
	Tags            map[string]string `json:"tags"`
	MandatoryTags   map[string]string `json:"mandatoryTags"`
	PrimaryProvider string            `json:"primaryProvider"`
}

type Response struct {
	Data    AssetDetails `json:"data"`
	Message string       `json:"message"`
}
