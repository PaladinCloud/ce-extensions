package models

type Policy struct {
	PolicyId   string `json:"policyId"`
	PolicyName string `json:"policyDisplayName"`
	Severity   string `json:"severity"`
	Category   string `json:"category"`
}
