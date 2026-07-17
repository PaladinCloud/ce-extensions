/*******************************************************************************
 * Copyright 2024 Paladin Cloud, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package com.paladincloud.datamapper.utils;

import com.paladincloud.datamapper.model.PluginDoneReceiveEvent;
import com.paladincloud.datamapper.service.CommonMapperService;
import com.paladincloud.datamapper.service.MapperService;
import com.paladincloud.datamapper.service.WizMapperService;

public class MapperServiceFactory {
    public static synchronized MapperService getMapperService(PluginDoneReceiveEvent receivedEvent) {
        if (receivedEvent.getSource().equals("wiz")) {
            return new WizMapperService(receivedEvent);
        } else {
            return new CommonMapperService(receivedEvent);
        }
    }
}
