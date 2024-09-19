package models

type Policy struct {
	PolicyId   string `db:"policyId"`
	PolicyName string `db:"policyDisplayName"`
	Severity   string `db:"severity"`
	Category   string `db:"category"`
}
