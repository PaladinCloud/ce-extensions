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
	"cache-layer/src/ext/clients"
	"encoding/json"
	"fmt"
	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"log"
	"net/http"
	"os"
)

type HttpServer struct {
	Configuration     *clients.Configuration
	HttpClient        *http.Client
	PluginsListClient *clients.PluginsListClient
}

// Start begins running the sidecar
func Start(port string, server *HttpServer) {
	println("starting the server in background")
	go startHTTPServer(port, server)
}

// Method that responds back with the cached values
func startHTTPServer(port string, httpConfig *HttpServer) {
	r := chi.NewRouter()
	r.Use(middleware.Logger)
	r.Get("/", handleValue(httpConfig))

	err := http.ListenAndServe(fmt.Sprintf(":%s", port), r)
	if err != nil {
		log.Fatal("error starting the server", err)
		os.Exit(0)
	}

	log.Printf("Server started on %s", port)
}

func handleValue(config *HttpServer) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		plugins, err := config.PluginsListClient.GetPluginsList(r.Context())
		if err != nil {
			http.Error(w, err.Error(), http.StatusNotFound)
			return
		}

		b, _ := json.Marshal(&plugins)
		w.Write(b)
	}
}
