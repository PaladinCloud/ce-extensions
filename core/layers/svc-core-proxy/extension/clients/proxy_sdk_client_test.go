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

package clients

import (
	"context"
	"encoding/json"
	"log"
	"net/http"
	"svc-core-proxy-layer/models"
	"testing"
)

const (
	tenantID        = "123"
	ID              = "345"
	port            = ":4568"
	success_message = "success"
)

func TestProxySDK_getTenantSecret(t *testing.T) {
	ctx := context.Background()
	serv := createServer()
	startServer(serv)
	resp, err := getTenantSecret(ctx, tenantID, ID)
	if err != nil {
		t.Fatalf("Error while fetching secret: %+v", err)
	}
	if resp.Message != success_message {
		t.Fatalf("Error while fetching secret, not a success message")
	}
	serv.Shutdown(ctx)
}

func TestProxySDK_getTenantFeatures(t *testing.T) {
	ctx := context.Background()
	serv := createServer()
	startServer(serv)
	resp, err := getTenantFeatures(ctx, tenantID)
	if err != nil {
		t.Fatalf("Error while fetching features: %+v", err)
	}
	if resp.Message != success_message {
		t.Fatalf("Error while fetching features, not a success message")
	}
	serv.Shutdown(ctx)
}
func TestProxySDK_getTenantRdsDetails(t *testing.T) {
	ctx := context.Background()
	serv := createServer()
	startServer(serv)
	resp, err := getTenantRdsDetails(ctx, tenantID)
	if err != nil {
		t.Fatalf("Error while fetching rds: %+v", err)
	}
	if resp.Message != success_message {
		t.Fatalf("Error while fetching rds, not a success message")
	}
	serv.Shutdown(ctx)
}

func TestProxySDK_getTenantOpeanSeachDetails(t *testing.T) {
	ctx := context.Background()
	serv := createServer()
	startServer(serv)
	resp, err := getTenantOpeanSeachDetails(ctx, tenantID)
	if err != nil {
		t.Fatalf("Error while fetching os details: %+v", err)
	}
	if resp.Message != success_message {
		t.Fatalf("Error while fetching os details, not a success message")
	}
	serv.Shutdown(ctx)
}
func createServer() *http.Server {
	var mux *http.ServeMux = http.NewServeMux()
	mux.HandleFunc("/tenant/"+tenantID+"/secrets/"+ID, mockServerResponse)
	mux.HandleFunc("/tenant/"+tenantID+"/features", mockServerResponse)
	mux.HandleFunc("/tenant/"+tenantID+"/rds", mockServerResponse)
	mux.HandleFunc("/tenant/"+tenantID+"/os", mockServerResponse)

	var serv *http.Server = &http.Server{
		Addr:    port,
		Handler: mux,
	}
	return serv
}
func startServer(serv *http.Server) {
	log.Println("Starting sever" + port)
	go func() {
		if err := serv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("Mock server startup failed: %+v", err)
		}
	}()
}

func mockServerResponse(w http.ResponseWriter, r *http.Request) {
	data := models.Response{
		Data:    nil,
		Message: success_message,
	}
	json.NewEncoder(w).Encode(data)
}
