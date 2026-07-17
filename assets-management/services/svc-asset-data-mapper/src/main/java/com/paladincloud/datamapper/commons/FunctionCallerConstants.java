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
package com.paladincloud.datamapper.commons;

public class FunctionCallerConstants {
    public static final String BOOLEAN_TRUE_AS_STRING = "true";
    public static final String BOOLEAN_FALSE_AS_STRING = "false";
    public static final String KEY_FIELD = "key";
    public static final String VALUE_FIELD = "value";
    public static final String JSON_KEY_VALUE_SEPARATOR = ":";
    public static final String JSON_FIELD_SEPARATOR = ",";
    public static final String DATE_FORMAT = "yyyy-MM-dd HH:00:00Z";
    public static final String IAM_RESOURCE_FORMAT = "projects/%s/datasets/%s/tables/%s";
    public static final String NAME_KEY = "name";
    public static final String AUTO_UPGRADE_KEY = "auto_upgrade";
    public static final String AUTO_REPAIR_KEY = "auto_repair";
    public static final String ENABLE_INTEGRITY_KEY = "enable_integrity_monitoring";
    public static final String ENABLE_SECURE_BOOT_KEY = "enable_secure_boot";
    public static final String AUTO_UPGRADE_NODE_KEY = "autoUpgrade";
    public static final String AUTO_REPAIR_NODE_KEY = "autoRepair";
    public static final String ENABLE_INTEGRITY_NODE_KEY = "enableIntegrityMonitoring";
    public static final String ENABLE_SECURE_BOOT_NODE_KEY = "enableSecureBoot";
    public static final String MANAGEMENT_KEY = "management";
    public static final String CONFIG_KEY = "config";
    public static final String SHIELDED_INSTANCE_CONFIG_KEY = "shielded_instance_config";

    protected static final String[] NODE_NAMES = {
            "AndroidKeyRestrictions",
            "ServerKeyRestrictions",
            "IosKeyRestrictions",
            "BrowserKeyRestrictions"
    };

    private FunctionCallerConstants() {
        throw new IllegalStateException("FunctionCallerConstants is a Utility class");
    }
}
