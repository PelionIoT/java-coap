#!/usr/bin/env bash
#
# Copyright (C) 2011-2018 ARM Limited. All rights reserved.
# Copyright (c) 2023 Izuma Networks. All rights reserved.
#
# SPDX-License-Identifier: Apache-2.0
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#


## Helper script to deploy artifacts to maven repository
## Deployment repository can be configured with extra arguments:
#
## ./deploy.sh -DaltDeploymentRepository=artifactory::default::YOUR-MAVEN-REPOSITORY

[[ -z $(git status -s) ]] || exit 1

VER="5.1.0-`git log --format="%cd-%h" --date=format:%Y%m%d -n 1`"
mvn versions:set -DnewVersion="$VER" -DgenerateBackupPoms=false
mvn -DskipTests deploy -P ci $@
git reset --hard

echo "Deployed with version: $VER"
