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
	"net/http"
	"net/url"
	"os"
	"svc-asset-network-rules-layer/clients"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
)

type HttpServer struct {
	Configuration           *clients.Configuration
	AssetNetworkRulesClient *clients.AssetNetworkRulesClient
}

// Start begins running the sidecar
func Start(port string, server *HttpServer, enableExtension bool) {
	if enableExtension {
		println("starting the server in background")
		go startHTTPServer(port, server)
	} else {
		println("starting the server")
		startHTTPServer(port, server)
	}
}

// Method that responds back with the cached values
func startHTTPServer(port string, httpConfig *HttpServer) {
	r := chi.NewRouter()
	r.Use(middleware.Logger)
	r.Use(middleware.Recoverer)
	r.Get("/tenant/{tenantId}/targets/{targetType}/assets/{assetId}/network-rules", handleValue(httpConfig))

	err := http.ListenAndServe(fmt.Sprintf(":%s", port), r)
	if err != nil {
		fmt.Errorf("error starting the server: %+v", err)
		os.Exit(0)
	}

	fmt.Printf("server started on %s\n", port)
}

func handleValue(config *HttpServer) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		tenantId := chi.URLParam(r, "tenantId")
		targetType := chi.URLParam(r, "targetType")
		assetId, err := url.QueryUnescape(chi.URLParam(r, "assetId"))
		if err != nil {
			fmt.Errorf("error decoding the asset id from url path")
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}

		assetDetails, err := config.AssetNetworkRulesClient.GetPortRuleDetails(r.Context(), tenantId, targetType, assetId)
		if err != nil {
			http.Error(w, err.Error(), http.StatusNotFound)
			return
		}

		b, _ := json.Marshal(assetDetails)
		w.Write(b)
	}
}
