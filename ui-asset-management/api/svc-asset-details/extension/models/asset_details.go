package models

type AssetDetails struct {
	AccountId       string            `json:"accountId"`
	Source          string            `json:"source"`
	SourceName      string            `json:"SourceName"`
	TargetType      string            `json:"targetType"`
	TargetTypeName  string            `json:"targetTypeName"`
	Tags            map[string]string `json:"tags"`
	PrimaryProvider string            `json:"primaryProvider"`
}
