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
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"net/url"
	"os"
	"svc-asset-violations-layer/clients"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
)

type HttpServer struct {
	Configuration         *clients.Configuration
	AssetViolationsClient *clients.AssetViolationsClient
}

// Start begins running the sidecar
func Start(port string, server *HttpServer, enableExtension bool) {
	if enableExtension {
		log.Println("starting the server in background")
		go startHTTPServer(port, server)
	} else {
		log.Println("starting the server")
		startHTTPServer(port, server)
	}
}

// Method that responds back with the cached values
func startHTTPServer(port string, httpConfig *HttpServer) {
	r := chi.NewRouter()
	r.Use(middleware.Logger)
	r.Use(middleware.Recoverer)
	r.Get("/tenant/{tenantId}/targets/{targetType}/assets/{assetId}/violations", handleValue(httpConfig))

	log.Printf("starting server on port [%s]\n", port)
	if err := http.ListenAndServe(fmt.Sprintf(":%s", port), r); err != nil {
		logError("error starting the server", err)
		os.Exit(1)
	}

	log.Printf("server started on %s\n", port)
}

func handleValue(config *HttpServer) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		tenantId := chi.URLParam(r, "tenantId")
		targetType := chi.URLParam(r, "targetType")
		assetId, err := url.QueryUnescape(chi.URLParam(r, "assetId"))
		if err != nil {
			logError("error decoding the asset id from url path", err)
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}

		assetDetails, err := config.AssetViolationsClient.GetAssetViolations(r.Context(), targetType, tenantId, assetId)
		if err != nil {
			logError("error fetching asset violations", err)
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}

		b, _ := json.Marshal(assetDetails)
		w.Write(b)
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
		// Fallback to basic logging if JSON fails
		log.Printf("ERROR: %s: %v (JSON marshaling failed: %v)", message, err, jsonErr)
		return
	}

	log.Printf("%s", jsonOutput)
}
