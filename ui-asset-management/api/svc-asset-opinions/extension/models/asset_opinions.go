package models

type OpinionsResponse struct {
	DocId    string                 `json:"_docId"`
	DocType  string                 `json:"_docType"`
	Opinions map[string]interface{} `json:"opinions"`
}

type Response struct {
	Data    *OpinionsResponse `json:"data"`
	Message string            `json:"message"`
}
