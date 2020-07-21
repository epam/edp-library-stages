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

package com.epam.edp.stages.impl.ci.impl.sonar


import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage

@Stage(name = "sonar", buildTool = "go", type = ProjectType.APPLICATION)
class SonarGo {

    Script script

    void run(context) {

        def scannerHome = script.tool 'SonarQube Scanner'
        script.dir("${context.workDir}") {
            script.withSonarQubeEnv('Sonar') {
                script.sh "${scannerHome}/bin/sonar-scanner " +
                        "-Dsonar.projectKey=${context.codebase.name} " +
                        "-Dsonar.projectName=${context.codebase.name} " +
                        "-Dsonar.go.coverage.reportPaths=coverage.out "
            }
            script.timeout(time: 10, unit: 'MINUTES') {
                def qualityGateResult = script.waitForQualityGate()
                if (qualityGateResult.status != 'OK')
                    script.error "[JENKINS][ERROR] Sonar quality gate check has been failed with status " +
                            "${qualityGateResult.status}"
            }
        }
    }
}
