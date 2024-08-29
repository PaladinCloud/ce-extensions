package models

type AssetDetails struct {
	Tags       map[string]string        `json:"tags"`
	Attributes []map[string]interface{} `json:"attributes"`
}
