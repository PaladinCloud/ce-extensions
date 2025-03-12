package models

type Tag struct {
	TagName string `db:"tagName"`
}

type ConfigResult struct {
	Value string `db:"value"`
}
