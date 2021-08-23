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

package com.epam.edp.stages.impl.ci.impl.getversion


import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage

@Stage(name = "get-version", buildTool = ["dotnet"], type = [ProjectType.APPLICATION, ProjectType.LIBRARY])
class GetVersionDotnetApplicationLibrary {
    Script script

    def updateBuildNumber(context) {
        script.sh """
            set -eo pipefail
            sed -i "s#\\(<Version>\\).*\\(</Version>\\)#\\1${context.codebase.branchVersion}-${context.codebase.currentBuildNumber}\\2#" "${context.codebase.deployableModule}/${context.codebase.deployableModule}.csproj"
            kubectl patch codebasebranches.v2.edp.epam.com ${context.codebase.config.name}-${context.git.branch.replaceAll(/\//, "-")} --type=merge -p '{\"status\": {\"build\": "${context.codebase.currentBuildNumber}"}}'
        """
    }

    void run(context) {
        script.dir("${context.workDir}") {
            context.codebase.deployableModule = script.sh(
                    script: "find ./ -name *.csproj | xargs grep -Poh '<DeployableModule>\\K[^<]*' ",
                    returnStdout: true
            ).trim()

            if (context.codebase.config.versioningType == "edp") {
                updateBuildNumber(context)
                context.codebase.vcsTag = "build/${context.codebase.version}"
                context.codebase.isTag = "${context.codebase.version}"
            } else {
                context.codebase.version = script.sh(
                        script: "find ${context.codebase.deployableModule} -name *.csproj | xargs grep -Po '<Version>\\K[^<]*'",
                        returnStdout: true
                ).trim().toLowerCase()
                context.codebase.buildVersion = "${context.codebase.version}-${script.BUILD_NUMBER}"
                context.codebase.version = context.codebase.buildVersion
                context.job.setDisplayName("${script.currentBuild.number}-${context.git.branch}-${context.codebase.version}")
                context.codebase.vcsTag = "${context.git.branch}-${context.codebase.buildVersion}"
                context.codebase.isTag = "${context.git.branch}-${context.codebase.buildVersion}"
            }

            script.println("[JENKINS][DEBUG] Deployable module: ${context.codebase.deployableModule}")
            context.codebase.deployableModuleDir = "${context.workDir}"
        }
        script.println("[JENKINS][DEBUG] Application version - ${context.codebase.version}")
        script.println("[JENKINS][DEBUG] VCS tag - ${context.codebase.vcsTag}")
        script.println("[JENKINS][DEBUG] IS tag - ${context.codebase.isTag}")
    }
}
