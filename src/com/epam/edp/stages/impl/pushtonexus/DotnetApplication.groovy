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

package com.epam.edp.stages.impl.pushtonexus

import com.epam.edp.stages.ProjectType
import com.epam.edp.stages.Stage
import groovy.json.*

@Stage(name = "push-to-nexus", buildTool = ["dotnet"], type = ProjectType.APPLICATION)
class DotnetApplication {
    Script script

    void run(context) {
        script.dir("${context.settingsDir}") {
            script.writeFile file: "${context.settingsDir}/get-nuget-token.groovy",
                    text: context.nexus.internalScripts.getNugetToken
        }

        script.dir("${context.workDir}") {
            context.nexus.uploadGroovyScriptToNexus("get-nuget-token", "${context.settingsDir}/get-nuget-token.groovy")
            def response = context.nexus.runNexusGroovyScript("get-nuget-token",
                    "{\"name\": \"${context.nexus.autouser}\"}")
            response = new JsonSlurperClassic().parseText(response.content)
            response = new JsonSlurperClassic().parseText(response.result)

            def nugetApiKey = response.nuGetApiKey
            def nugetPackagesPath = "/tmp/${context.gerrit.project}-nupkgs/"

            script.sh "dotnet pack ${context.nexus.dotnet.sln_filename} --no-build --output ${nugetPackagesPath}"
            script.sh "dotnet nuget push ${nugetPackagesPath} -k ${nugetApiKey} " +
                    "-s ${context.nexus.nugetInternalRegistry}"
        }
        context.deployableModuleDir = "${context.workDir}"
    }
}
