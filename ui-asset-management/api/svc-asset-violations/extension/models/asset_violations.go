package models

type Violation struct {
	PolicyName     string `json:"policyName"`
	Severity       string `json:"severity"`
	Category       string `json:"category"`
	LastScanStatus string `json:"lastScan"`
	IssueId        string `json:"issueId"`
}

type SeverityInfo struct {
	Severity string `json:"severity"`
	Count    int    `json:"count"`
}

type PolicyViolations struct {
	Violations      []Violation    `json:"violations"`
	TotalPolicies   int            `json:"totalPolicies"`
	TotalViolations int            `json:"totalViolations"`
	Compliance      int            `json:"compliance"`
	SeverityInfos   []SeverityInfo `json:"severityInfo"`
}

type AssetViolations struct {
	Data PolicyViolations `json:"data"`
}
