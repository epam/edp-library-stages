/* Copyright 2022 EPAM Systems.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.

See the License for the specific language governing permissions and
limitations under the License.*/

package com.epam.edp.stages.impl.ci.impl.sonarcleanup

import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage
import com.epam.edp.tools.SonarScanner

@Stage(name = "sonar-cleanup", buildTool = ["maven", "npm", "dotnet", "gradle", "python", "go"], type = [ProjectType.APPLICATION, ProjectType.LIBRARY, ProjectType.AUTOTESTS])
class SonarCleanup {
    Script script

    void run(context) {
        SonarScanner sonarScanner = new SonarScanner(script);
        script.withSonarQubeEnv('Sonar') {
            def sonarAuthHeader="${script.env.SONAR_AUTH_TOKEN}:".bytes.encodeBase64().toString()
            def sonarProjectKey = "${context.codebase.name}-${context.git.normalizedBranch}:change-${context.git.changeNumber}"
            if (context.git.patchsetNumber != "0" && context.git.changeNumber != "0") {
                sonarScanner.cleanSonarProjectRange(context.git.patchsetNumber, context.sonar.route, sonarProjectKey, sonarAuthHeader)
            }
        }
    }
}