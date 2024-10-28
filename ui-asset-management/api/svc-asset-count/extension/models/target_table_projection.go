package models

type TargetTableProjection struct {
	Type        string `db:"type"`
	DisplayName string `db:"displayName"`
	Category    string `db:"category"`
	Domain      string `db:"domain"`
	Provider    string `db:"provider"`
}
