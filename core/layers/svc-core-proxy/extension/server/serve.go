/*
 * Copyright (c) 2024 Paladin Cloud, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package server

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"net/url"
	"svc-core-proxy-layer/clients"
	"svc-core-proxy-layer/models"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
)

const (
	tenantIDParam = "tenantId"
)

type HttpServer struct {
	ProxyClient *clients.ProxyClient
}

type Config struct {
	Port            string
	EnableExtension bool
}

// Start begins running the sidecar
func Start(port string, server *HttpServer, enableExtension bool) {
	if enableExtension {
		log.Println("Starting the server in background")
		go func() {
			if err := startHTTPServer(port, server); err != nil {
				log.Fatalf("Failed to start server in background: %v", err)
			}
		}()
	} else {
		log.Println("Starting the server")
		if err := startHTTPServer(port, server); err != nil {
			log.Fatalf("Failed to start server: %v", err)
		}
	}
}

// startHTTPServer initializes and starts the HTTP server
func startHTTPServer(port string, httpConfig *HttpServer) error {
	r := chi.NewRouter()
	r.Use(middleware.Logger)
	r.Use(middleware.Recoverer)

	r.Get("/tenant/{tenantId}/secrets/{id}", handleRequestWithId(httpConfig.ProxyClient.GetTenantSecretDetails))
	r.Get("/tenant/{tenantId}/features", handleRequest(httpConfig.ProxyClient.GetTenantFeatures))
	r.Get("/tenant/{tenantId}/rds", handleRequest(httpConfig.ProxyClient.GetTenantRdsDetails))
	r.Get("/tenant/{tenantId}/os", handleRequest(httpConfig.ProxyClient.GetTenantOpenSearchDetails))

	log.Printf("Starting server on port [%s]\n", port)
	return http.ListenAndServe(fmt.Sprintf(":%s", port), r)
}

// handleRequest is a generic handler for tenant-related requests
func handleRequest(getDetails func(ctx context.Context, tenantId string) (*models.Response, error)) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		tenantId := chi.URLParam(r, tenantIDParam)
		log.Printf("fetching details for tenant id [%s]\n", tenantId)

		details, err := getDetails(r.Context(), tenantId)
		if err != nil {
			logError("Error fetching details", err)
			http.Error(w, err.Error(), http.StatusNotFound)
			return
		}

		if err := json.NewEncoder(w).Encode(details); err != nil {
			logError("Error encoding response", err)
			http.Error(w, "Internal server error", http.StatusInternalServerError)
		}
	}
}

// handleRequest is a generic handler for tenant-related requests
func handleRequestWithId(getDetailsWithId func(ctx context.Context, tenantId, id string) (*models.Response, error)) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		tenantId := chi.URLParam(r, tenantIDParam)
		id, err := url.QueryUnescape(chi.URLParam(r, "id"))
		if err != nil {
			logError("Error unescaping id parameter", err)
			http.Error(w, "Invalid id parameter", http.StatusBadRequest)
			return
		}
		log.Printf("fetching details for tenant id [%s]\n", tenantId)

		details, err := getDetailsWithId(r.Context(), tenantId, id)
		if err != nil {
			logError("Error fetching details", err)
			http.Error(w, err.Error(), http.StatusNotFound)
			return
		}

		if err := json.NewEncoder(w).Encode(details); err != nil {
			logError("Error encoding response", err)
			http.Error(w, "Internal server error", http.StatusInternalServerError)
		}
	}
}

func logError(message string, err error) {
	type ErrorOutput struct {
		Message string `json:"message"`
		Error   string `json:"error"`
		Details string `json:"details,omitempty"`
	}

	errorOutput := ErrorOutput{
		Message: message,
		Error:   fmt.Sprintf("%T", err),
		Details: err.Error(),
	}

	jsonOutput, jsonErr := json.MarshalIndent(errorOutput, "", "  ")
	if jsonErr != nil {
		log.Printf("ERROR: %s: %v (JSON marshaling failed: %v)", message, err, jsonErr)
		return
	}

	log.Printf("%s", jsonOutput)
}
