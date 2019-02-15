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

@Stage(name = "sonar", buildTool = ["npm"], type = [ProjectType.APPLICATION])
class SonarNpmApplication {
    Script script

    void run(context) {
        def scannerHome = script.tool 'SonarQube Scanner'
        script.dir("${context.workDir}") {
            if (context.job.type == "codereview") {
                script.withSonarQubeEnv('Sonar') {
                    script.sh "${scannerHome}/bin/sonar-scanner -Dsonar.analysis.mode=preview " +
                            "-Dsonar.report.export.path=sonar-report.json" +
                            " -Dsonar.branch=codereview"
                }
                script.sonarToGerrit inspectionConfig: [baseConfig: [projectPath: "",
                         sonarReportPath: '.scannerwork/sonar-report.json'], serverURL: "${context.sonar.route}"],
                        notificationConfig: [commentedIssuesNotificationRecipient: 'NONE',
                                             negativeScoreNotificationRecipient: 'NONE'],
                        reviewConfig: [issueFilterConfig: [newIssuesOnly: false, changedLinesOnly: false,
                                                           severity: 'CRITICAL']],
                        scoreConfig: [category: 'Sonar-Verified', issueFilterConfig: [severity: 'CRITICAL']]
            }

            script.withSonarQubeEnv('Sonar') {
                script.sh "${scannerHome}/bin/sonar-scanner " +
                        "-Dsonar.projectKey=${context.application.name} " +
                        "-Dsonar.projectName=${context.application.name} " +
                        "-Dsonar.branch=" +
                        "${context.job.type == "codereview" ? context.gerrit.changeName : context.gerrit.branch}"
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
