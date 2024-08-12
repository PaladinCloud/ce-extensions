USE
    `pacmandata`;


########## start plugins seed data ##########
INSERT INTO `plugins` (source, name, type, description, iconURL, isLegacy, gapPolicyAvailable, disablePolicyActions, isInbound, isComposite, isCloud, created_by, created_date)
VALUES ('aws', 'AWS', 'Cloud Provider', '', '/assets/icons/aws-color.svg', _binary '\0', _binary '\0', _binary '\0', _binary '', _binary '\0', _binary '', 'system', CURDATE());
INSERT INTO `plugins` (source, name, type, description, iconURL, isLegacy, gapPolicyAvailable, disablePolicyActions, isInbound, isComposite, isCloud, created_by, created_date)
VALUES ('azure', 'Azure', 'Cloud Provider', '', '/assets/icons/azure-color.svg', _binary '\0', _binary '\0', _binary '\0', _binary '', _binary '\0', _binary '', 'system', CURDATE());
INSERT INTO `plugins` (source, name, type, description, iconURL, isLegacy, gapPolicyAvailable, disablePolicyActions, isInbound, isComposite, isCloud, created_by, created_date)
VALUES ('gcp', 'GCP', 'Cloud Provider', '', '/assets/icons/gcp-color.svg', _binary '', _binary '\0', _binary '\0', _binary '', _binary '\0', _binary '', 'system', CURDATE());
INSERT INTO `plugins` (source, name, type, description, iconURL, isLegacy, gapPolicyAvailable, disablePolicyActions, isInbound, isComposite, isCloud, created_by, created_date)
VALUES ('qualys', 'Qualys', 'Vulnerability Management', '', '/assets/icons/qualys-color.svg', _binary '', _binary '\0', _binary '\0', _binary '', _binary '\0', _binary '\0', 'system', CURDATE());
INSERT INTO `plugins` (source, name, type, description, iconURL, isLegacy, gapPolicyAvailable, disablePolicyActions, isInbound, isComposite, isCloud, created_by, created_date)
VALUES ('redhat', 'Red Hat', 'Advanced Cluster Security', '', '/assets/icons/redhat-color.svg', _binary '\0', _binary '\0', _binary '', _binary '', _binary '\0', _binary '\0', 'system', CURDATE());
INSERT INTO `plugins` (source, name, type, description, iconURL, isLegacy, gapPolicyAvailable, disablePolicyActions, isInbound, isComposite, isCloud, created_by, created_date)
VALUES ('tenable', 'Tenable', 'Vulnerability Management', '', '/assets/icons/tenable-color.svg', _binary '\0', _binary '', _binary '\0', _binary '', _binary '\0', _binary '\0', 'system', CURDATE());
INSERT INTO `plugins` (source, name, type, description, iconURL, isLegacy, gapPolicyAvailable, disablePolicyActions, isInbound, isComposite, isCloud, created_by, created_date)
VALUES ('aqua', 'Aqua', 'Vulnerability Management', '', '/assets/icons/aqua-color.svg', _binary '\0', _binary '\0', _binary '\0', _binary '', _binary '\0', _binary '\0', 'system', CURDATE());
INSERT INTO `plugins` (source, name, type, description, iconURL, isLegacy, gapPolicyAvailable, disablePolicyActions, isInbound, isComposite, isCloud, created_by, created_date)
VALUES ('contrast', 'Contrast', 'DAST', 'Dynamic Application Security Testing', '/assets/icons/contrast-color.svg', _binary '\0', _binary '\0', _binary '', _binary '', _binary '\0', _binary '\0', 'system', CURDATE());
INSERT INTO `plugins` (source, name, type, description, iconURL, isLegacy, gapPolicyAvailable, disablePolicyActions, isInbound, isComposite, isCloud, created_by, created_date)
VALUES ('checkmarx', 'Checkmarx', 'DAST', 'Dynamic Application Security Testing', '/assets/icons/checkmarx-color.svg', _binary '\0', _binary '\0', _binary '', _binary '', _binary '\0', _binary '\0', 'system', CURDATE());
INSERT INTO `plugins` (source, name, type, description, iconURL, isLegacy, gapPolicyAvailable, disablePolicyActions, isInbound, isComposite, isCloud, created_by, created_date)
VALUES ('crowdstrike', 'CrowdStrike', 'Vulnerability Management', '', '/assets/icons/crowdStrike-color.svg', _binary '\0', _binary '\0', _binary '', _binary '', _binary '\0', _binary '\0', 'system', CURDATE());
INSERT INTO `plugins` (source, name, type, description, iconURL, isLegacy, gapPolicyAvailable, disablePolicyActions, isInbound, isComposite, isCloud, created_by, created_date)
VALUES ('rapid7', 'Rapid7', 'Vulnerability Management', '', '/assets/icons/rapid7-color.svg', _binary '\0', _binary '\0', _binary '\0', _binary '', _binary '\0', _binary '\0', 'system', CURDATE());
INSERT INTO `plugins` (source, name, type, description, iconURL, isLegacy, gapPolicyAvailable, disablePolicyActions, isInbound, isComposite, isCloud, created_by, created_date)
VALUES ('wiz', 'WIZ', 'CSPM', 'Cloud Security Posture Management', '/assets/icons/wiz-color.svg', _binary '\0', _binary '\0', _binary '', _binary '', _binary '', _binary '\0', 'system', CURDATE());
INSERT INTO `plugins` (source, name, type, description, iconURL, isLegacy, gapPolicyAvailable, disablePolicyActions, isInbound, isComposite, isCloud, created_by, created_date)
VALUES ('burpsuite', 'Burp Suite Enterprise Edition', 'Application Security Testing Software', '', '/assets/icons/burpsuite-color.svg', _binary '\0', _binary '\0', _binary '\0', _binary '', _binary '\0', _binary '\0', 'system', CURDATE());
INSERT INTO `plugins` (source, name, type, description, iconURL, isLegacy, gapPolicyAvailable, disablePolicyActions, isInbound, isComposite, isCloud, created_by, created_date)
VALUES ('jira', 'Jira', 'ITSM', 'IT service management', '/assets/icons/jira-color.svg', _binary '\0', _binary '\0', _binary '\0', _binary '\0', _binary '\0', _binary '\0', 'system', CURDATE());
########## end of plugins seed data ##########

########## start plugin_policy_definitions seed data ##########
INSERT INTO `plugin_policy_definitions` (source, target_name, cwe_enabled, cve_enabled, mitre_enabled, asset_lookup_key, source_asset_key, created_by, created_date)
VALUES ('tenable', 'ec2', _binary '\0', _binary '', _binary '\0', 'instanceId', 'instanceid', 'system', CURDATE());
INSERT INTO `plugin_policy_definitions` (source, target_name, cwe_enabled, cve_enabled, mitre_enabled, asset_lookup_key, source_asset_key, created_by, created_date)
VALUES ('tenable', 'virtualmachine', _binary '\0', _binary '', _binary '\0', 'azure_vm_id', 'vmId', 'system', CURDATE());
INSERT INTO `plugin_policy_definitions` (source, target_name, cwe_enabled, cve_enabled, mitre_enabled, asset_lookup_key, source_asset_key, created_by, created_date)
VALUES ('tenable', 'vminstance', _binary '\0', _binary '', _binary '\0', 'instanceId', 'id', 'system', CURDATE());
INSERT INTO `plugin_policy_definitions` (source, target_name, cwe_enabled, cve_enabled, mitre_enabled, asset_lookup_key, source_asset_key, created_by, created_date)
VALUES ('crowdstrike', 'ec2', _binary '', _binary '', _binary '', 'instanceId', 'instanceid', 'system', CURDATE());
INSERT INTO `plugin_policy_definitions` (source, target_name, cwe_enabled, cve_enabled, mitre_enabled, asset_lookup_key, source_asset_key, created_by, created_date)
VALUES ('crowdstrike', 'mobile', _binary '', _binary '', _binary '', 'externalId', '_resourceid', 'system', CURDATE());
INSERT INTO `plugin_policy_definitions` (source, target_name, cwe_enabled, cve_enabled, mitre_enabled, asset_lookup_key, source_asset_key, created_by, created_date)
VALUES ('crowdstrike', 'virtualmachine', _binary '', _binary '', _binary '', 'instanceId', 'vmId', 'system', CURDATE());
INSERT INTO `plugin_policy_definitions` (source, target_name, cwe_enabled, cve_enabled, mitre_enabled, asset_lookup_key, source_asset_key, created_by, created_date)
VALUES ('crowdstrike', 'vminstance', _binary '', _binary '', _binary '', 'instanceId', '_resourceid', 'system', CURDATE());
INSERT INTO `plugin_policy_definitions` (source, target_name, cwe_enabled, cve_enabled, mitre_enabled, asset_lookup_key, source_asset_key, created_by, created_date)
VALUES ('crowdstrike', 'workstation', _binary '', _binary '', _binary '', 'externalId', '_resourceid', 'system', CURDATE());
########## end of plugin_policy_definitions seed data ##########