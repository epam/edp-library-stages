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

package com.epam.edp.stages.impl.compile

import com.epam.edp.stages.ProjectType
import com.epam.edp.stages.Stage

@Stage(name = "compile", buildTool = "dotnet", type = ProjectType.APPLICATION)
class DotnetApplication {
    Script script

    void run(context) {
        script.dir("${context.workDir}") {
            context.nexus.dotnet.sln_filename = script.sh(
                    script: "ls *.sln",
                    returnStdout: true
            ).trim()
            script.sh "dotnet build ${context.nexus.dotnet.sln_filename}"
        }
    }
}