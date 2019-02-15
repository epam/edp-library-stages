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

package com.epam.edp.stages.impl.sonar

import com.epam.edp.stages.ProjectType
import com.epam.edp.stages.Stage
import com.epam.edp.stages.impl.sonarcleanup.SonarCleanupApplication

@Stage(name = "sonar", buildTool = ["dotnet"], type = [ProjectType.APPLICATION])
class SonarDotnetApplication {
    Script script

    void run(context) {
        def scannerHome = script.tool 'SonarScannerMSBuild'
        script.dir("${context.workDir}") {
            if (context.job.type == "codereview") {
                script.withSonarQubeEnv('Sonar') {
                    script.sh """
                dotnet ${scannerHome}/SonarScanner.MSBuild.dll begin /k:${context.application.name} \
                /d:sonar.analysis.mode=preview \
                /k:${context.application.name} \
                /n:${context.application.name} \
                /d:sonar.report.export.path=sonar-report.json \
                /d:sonar.branch=codereview
                dotnet build ${context.buildTool.sln_filename}
                dotnet ${scannerHome}/SonarScanner.MSBuild.dll end
            """
                }
                script.sonarToGerrit inspectionConfig: [baseConfig:
                        [projectPath: "", sonarReportPath: ".sonarqube/out/.sonar/sonar-report.json"],
                                                        serverURL: "${context.sonar.route}"],
                        notificationConfig: [commentedIssuesNotificationRecipient: 'NONE',
                                             negativeScoreNotificationRecipient: 'NONE'],
                        reviewConfig: [issueFilterConfig: [newIssuesOnly: false, changedLinesOnly: false,
                                                           severity: 'MAJOR']],
                        scoreConfig: [category: 'Sonar-Verified', issueFilterConfig: [severity: 'MAJOR']]
            }

            script.withSonarQubeEnv('Sonar') {
                script.sh """
                dotnet ${scannerHome}/SonarScanner.MSBuild.dll begin /k:${context.application.name} \
                /k:${context.application.name} \
                /n:${context.application.name} \
                /d:sonar.branch=${context.job.type == "codereview" ? context.gerrit.changeName : context.gerrit.branch} \
                /d:sonar.cs.opencover.reportsPaths=${context.workDir}/*Tests*/*.xml
                dotnet build ${context.buildTool.sln_filename}
                dotnet ${scannerHome}/SonarScanner.MSBuild.dll end
            """
            }
            script.timeout(time: 10, unit: 'MINUTES') {
                def qualityGateResult = script.waitForQualityGate()
                if (qualityGateResult.status != 'OK')
                    script.error "[JENKINS][ERROR] Sonar quality gate check has been failed with status " +
                            "${qualityGateResult.status}"
            }

            if (context.job.type == "build")
                new SonarCleanupApplication(script: script).run(context)
        }
    }
}
