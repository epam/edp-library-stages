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

package com.epam.edp.stages.impl.ci.impl.sonarcleanup

import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage

@Stage(name = "sonar-cleanup", buildTool = ["maven", "npm", "dotnet", "gradle"], type = [ProjectType.APPLICATION, ProjectType.LIBRARY])
class SonarCleanupApplicationLibrary {
    Script script

    void run(context) {
        script.withSonarQubeEnv('Sonar') {
            def sonarAuthHeader="${script.env.SONAR_AUTH_TOKEN}:".bytes.encodeBase64().toString()
            def sonarProjectKey = "${context.codebase.name}:change-${context.git.changeNumber}"
            for (int i = 1; i <= (context.git.patchsetNumber as Integer) ; i++) {
                def response = script.httpRequest url: "${script.env.SONAR_HOST_URL}/api/components/show?key=${sonarProjectKey}-${i}",
                        httpMode: 'GET',
                        customHeaders: [[name: 'Authorization', value: "Basic ${sonarAuthHeader}"]],
                        validResponseCodes: '100:399,404'
                if (response.status == 200) {
                    script.httpRequest url: "${script.env.SONAR_HOST_URL}/api/projects/delete?key=${sonarProjectKey}-${i}",
                            httpMode: 'POST',
                            customHeaders: [[name: 'Authorization', value: "Basic ${sonarAuthHeader}"]]
                    script.println("[JENKINS][DEBUG] Project ${sonarProjectKey}-${i} deleted")
                }
            }
        }
    }
}
