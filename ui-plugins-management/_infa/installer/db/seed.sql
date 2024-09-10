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

USE
    `pacmandata`;


########## start plugins seed data ##########
##### start cloud plugins #####
INSERT IGNORE INTO `plugins` (source, name, type, description, iconURL, isLegacy, gapPolicyAvailable, disablePolicyActions, isInbound, isComposite, isCloud, created_by, created_date)
VALUES ('aws', 'AWS', 'Cloud Provider', '', '/assets/icons/aws-color.svg', _binary '', _binary '\0', _binary '\0', _binary '', _binary '\0', _binary '', 'system', CURDATE());
INSERT IGNORE INTO `plugins` (source, name, type, description, iconURL, isLegacy, gapPolicyAvailable, disablePolicyActions, isInbound, isComposite, isCloud, created_by, created_date)
VALUES ('azure', 'Azure', 'Cloud Provider', '', '/assets/icons/azure-color.svg', _binary '', _binary '\0', _binary '\0', _binary '', _binary '\0', _binary '', 'system', CURDATE());
INSERT IGNORE INTO `plugins` (source, name, type, description, iconURL, isLegacy, gapPolicyAvailable, disablePolicyActions, isInbound, isComposite, isCloud, created_by, created_date)
VALUES ('gcp', 'GCP', 'Cloud Provider', '', '/assets/icons/gcp-color.svg', _binary '\0', _binary '\0', _binary '\0', _binary '', _binary '\0', _binary '', 'system', CURDATE());
##### end cloud plugins #####

INSERT IGNORE INTO `plugins` (source, name, type, description, iconURL, isLegacy, gapPolicyAvailable, disablePolicyActions, isInbound, isComposite, isCloud, created_by, created_date)
VALUES ('aqua', 'Aqua', 'Vulnerability Management', '', '/assets/icons/aqua-color.svg', _binary '', _binary '\1', _binary '\0', _binary '', _binary '\0', _binary '\0', 'system', CURDATE());
INSERT IGNORE INTO `plugins` (source, name, type, description, iconURL, isLegacy, gapPolicyAvailable, disablePolicyActions, isInbound, isComposite, isCloud, created_by, created_date)
VALUES ('qualys', 'Qualys', 'Vulnerability Management', '', '/assets/icons/qualys-color.svg', _binary '', _binary '\0', _binary '\0', _binary '', _binary '\0', _binary '\0', 'system', CURDATE());
INSERT IGNORE INTO `plugins` (source, name, type, description, iconURL, isLegacy, gapPolicyAvailable, disablePolicyActions, isInbound, isComposite, isCloud, created_by, created_date)
VALUES ('tenable', 'Tenable', 'Vulnerability Management', '', '/assets/icons/tenable-color.svg', _binary '\0', _binary '', _binary '\0', _binary '', _binary '\0', _binary '\0', 'system', CURDATE());
########## end of plugins seed data ##########

########## start plugin_policy_definitions seed data ##########
INSERT IGNORE INTO `plugin_policy_definitions` (source, target_name, cwe_enabled, cve_enabled, mitre_enabled, asset_lookup_key, source_asset_key, created_by, created_date)
VALUES ('tenable', 'ec2', _binary '\0', _binary '', _binary '\0', 'instanceId', 'instanceid', 'system', CURDATE());
INSERT IGNORE INTO `plugin_policy_definitions` (source, target_name, cwe_enabled, cve_enabled, mitre_enabled, asset_lookup_key, source_asset_key, created_by, created_date)
VALUES ('tenable', 'virtualmachine', _binary '\0', _binary '', _binary '\0', 'azure_vm_id', 'vmId', 'system', CURDATE());
INSERT IGNORE INTO `plugin_policy_definitions` (source, target_name, cwe_enabled, cve_enabled, mitre_enabled, asset_lookup_key, source_asset_key, created_by, created_date)
VALUES ('tenable', 'vminstance', _binary '\0', _binary '', _binary '\0', 'instanceId', 'id', 'system', CURDATE());
########## end of plugin_policy_definitions seed data ##########