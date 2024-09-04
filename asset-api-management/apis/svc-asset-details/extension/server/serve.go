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
	"svc-asset-details-layer/clients"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
)

// Start begins running the sidecar
func Start(port string) {
	println("starting the server in background")
	go startHTTPServer(port)
}

// Method that responds back with the cached values
func startHTTPServer(port string) {
	r := chi.NewRouter()
	r.Use(middleware.Logger)
	r.Get("/{ag}/{targetType}/{assetId}/details", handleValue())

	err := http.ListenAndServe(fmt.Sprintf(":%s", port), r)
	if err != nil {
		log.Fatal("error starting the server", err)
		os.Exit(0)
	}

	log.Printf("Server started on %s", port)
}

func handleValue() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		ag := chi.URLParam(r, "ag")
		targetType := chi.URLParam(r, "targetType")
		assetId := chi.URLParam(r, "assetId")
		assetDetails, err := clients.NewAssetDetailsClient().GetAssetDetails(r.Context(), ag, targetType, assetId)
		if err != nil {
			http.Error(w, err.Error(), http.StatusNotFound)
			return
		}

		b, _ := json.Marshal(assetDetails)
		w.Write(b)
	}
}
