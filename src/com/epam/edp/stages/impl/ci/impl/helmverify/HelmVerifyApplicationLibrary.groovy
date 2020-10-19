/* Copyright 2019 EPAM Systems.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.

See the License for the specific language governing permissions and
limitations under the License.*/

package com.epam.edp.stages.impl.ci.impl.helmverify


import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage

@Stage(name = "helm-verify", buildTool = ["gradle", "maven", "dotnet", "npm", "go", "python"], type = [ProjectType.APPLICATION, ProjectType.LIBRARY])
class HelmVerifyApplicationLibrary {
    Script script

    void run(context) {
        def helmDownloadUrl = context.job.getParameterValue("HELM_DOWNLOAD_URL", context.job.defaultHelmDownloadUrl)
        script.dir("${context.workDir}") {
            script.sh """
            curl -o helm.tar.gz ${helmDownloadUrl}
            tar -xzf helm.tar.gz linux-amd64/helm --strip 1
            values_files=`find ${context.job.deployTemplatesDirectory} -maxdepth 1 -name 'values*.yaml'`
            for file in \${values_files}; do echo Linting with \${file} values; ./helm lint "${context.job.deployTemplatesDirectory}" --values \${file}; done
            """
            }
        }
    }
