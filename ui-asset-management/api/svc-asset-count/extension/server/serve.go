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
	"os"
	"svc-asset-count-layer/clients"
	"svc-asset-count-layer/models"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
)

type HttpServer struct {
	Configuration    *clients.Configuration
	AssetCountClient *clients.AssetCountClient
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
	r.Get("/tenant/{tenantId}/assetgroups/{ag}/assets/count", handleValue(httpConfig))

	srv := &http.Server{
		Addr:    fmt.Sprintf(":%s", port),
		Handler: r,
	}
	if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		log.Printf("error starting the server: %v\n", err)
		os.Exit(1)
	}

	log.Printf("server started on %s\n", port)
}

func handleValue(config *HttpServer) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		tenantId := chi.URLParam(r, "tenantId")
		ag := chi.URLParam(r, "ag")
		domain := r.URL.Query().Get("domain")

		if tenantId == "" || ag == "" {
			log.Println("missing required tenant id and/or asset group parameter(s)")
			errorResponse := getErrorResponse("missing required tenant id and/or asset group parameter(s)")
			responseForBadRequest, marshalErr := json.Marshal(errorResponse)
			if marshalErr != nil {
				http.Error(w, `{"message":"internal server error"}`, http.StatusInternalServerError)
				return
			}

			http.Error(w, string(responseForBadRequest), http.StatusBadRequest)
			return
		}

		assetCounts, err := config.AssetCountClient.GetAssetCountForAssetGroup(r.Context(), tenantId, ag, domain)
		if err != nil {
			log.Println(err)
			errorResponse := getErrorResponse(assetCounts.Message)
			failureResponse, marshalErr := json.Marshal(errorResponse)
			if marshalErr != nil {
				http.Error(w, `{"message":"internal server error"}`, http.StatusInternalServerError)
				return
			}

			http.Error(w, string(failureResponse), http.StatusNotFound)
			return
		}

		b, _ := json.Marshal(assetCounts)
		w.Write(b)
	}
}

func getErrorResponse(errMsg string) models.AssetStateCountResponse {
	return models.AssetStateCountResponse{
		Message: errMsg,
		Data: models.AssetStateCountData{
			AssetStateNameCounts: []models.AssetStateCount{},
		},
	}
}
