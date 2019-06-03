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

    void run(context) {
        script.dir("${context.workDir}") {
            context.codebase.deployableModule = script.sh(
                    script: "find ./ -name *.csproj | xargs grep -Poh '<DeployableModule>\\K[^<]*' ",
                    returnStdout: true
            ).trim()

            context.codebase.version = script.sh(
                    script: "find ${context.codebase.deployableModule} -name *.csproj | xargs grep -Po '<Version>\\K[^<]*'",
                    returnStdout: true
            ).trim().toLowerCase()
            context.job.setDisplayName("${script.currentBuild.number}-${context.gerrit.branch}-${context.codebase.version}")
            context.codebase.buildVersion = "${context.codebase.version}-${script.BUILD_NUMBER}"
            script.println("[JENKINS][DEBUG] Deployable module: ${context.codebase.deployableModule}")
            context.codebase.deployableModuleDir = "${context.workDir}"
        }
        script.println("[JENKINS][DEBUG] Application version - ${context.codebase.version}")
    }
}
