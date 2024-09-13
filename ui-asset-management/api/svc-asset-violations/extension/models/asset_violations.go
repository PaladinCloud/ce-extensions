package models

type Violation struct {
	PolicyName     string `json:"policyName"`
	Severity       string `json:"severity"`
	Category       string `json:"category"`
	LastScanStatus string `json:"lastScanStatus"`
	IssueId        string `json:"issueId"`
}

type AssetViolations struct {
	Violations []Violation `json:"violations"`
}
