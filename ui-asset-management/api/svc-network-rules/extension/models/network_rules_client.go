package models

type OutboundNetworkRule struct {
	Protocol    string `json:"protocol"`
	FromPort    string `json:"fromPort"`
	ToPort      string `json:"toPort"`
	Destination string `json:"destination"`
	Access      string `json:"access"`
	Priority    string `json:"priority"`
}

type InboundNetworkRule struct {
	Protocol string `json:"protocol"`
	FromPort string `json:"fromPort"`
	ToPort   string `json:"toPort"`
	Source   string `json:"source"`
	Access   string `json:"access"`
	Priority string `json:"priority"`
}

type NetworkRulesResponse struct {
	InboundRules  []InboundNetworkRule  `json:"inboundRules"`
	OutboundRules []OutboundNetworkRule `json:"outboundRules"`
}

type Response struct {
	Data    *NetworkRulesResponse `json:"data"`
	Message string                `json:"message"`
}
