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


create table plugins
(
    source               varchar(50)      not null,
    name                 varchar(50)      not null,
    type                 varchar(100)     not null,
    description          varchar(255)     not null,
    iconURL              varchar(255)     not null,
    isLegacy             bit default b'0' not null,
    gapPolicyAvailable   bit default b'0' not null,
    disablePolicyActions bit default b'0' not null,
    isInbound            bit default b'1' not null,
    isComposite          bit default b'0' not null,
    isCloud              bit default b'0' not null,
    created_by           varchar(100)     not null,
    created_date         date             not null,
    modified_by          varchar(100)     null,
    modified_date        date             null,
    primary key (source)
);

create index source
    on plugins (source);

create table plugin_policy_definitions
(
    source           varchar(50)                  not null,
    target_name      varchar(75) COLLATE utf8_bin not null,
    cwe_enabled      bit default b'0'             not null,
    cve_enabled      bit default b'0'             not null,
    mitre_enabled    bit default b'0'             not null,
    asset_lookup_key varchar(75)                  not null,
    source_asset_key varchar(75)                  not null,
    created_by       varchar(100)                 not null,
    created_date     date                         not null,
    modified_by      varchar(100)                 null,
    modified_date    date                         null,
    primary key (source, target_name),
    constraint plugin_policy_definitions_ibfk_1
        foreign key (source) references plugins (source),
    constraint plugin_policy_definitions_ibfk_2
        foreign key (target_name) references cf_Target (targetName)
);

create index target_name
    on plugin_policy_definitions (target_name);
