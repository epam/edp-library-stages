/* Copyright 2020 EPAM Systems.

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
import com.epam.edp.stages.impl.ci.impl.sonarcleanup.SonarCleanupApplicationLibrary

@Stage(name = "sonar", buildTool = "python", type = [ProjectType.APPLICATION, ProjectType.LIBRARY])
class SonarPythonApplicationLibrary {
    Script script

    def sendSonarScan(workDir, codebaseName) {
        def scannerHome = script.tool 'SonarQube Scanner'
        script.dir("${workDir}") {
            script.withSonarQubeEnv('Sonar') {
                script.sh "pylint --exit-zero *.py -r n --msg-template=\"{path}:{line}: [{msg_id}({symbol}), {obj}] {msg}\" > pylint-reports.txt"
                script.sh "pytest --cov=. --cov-report xml:coverage.xml"
                script.sh "${scannerHome}/bin/sonar-scanner " +
                          "-Dsonar.projectKey=${codebaseName} " +
                          "-Dsonar.projectName=${codebaseName} " +
                          "-Dsonar.language=py " +
                          "-Dsonar.python.pylint.reportPath=${workDir}/pylint-reports.txt " +
                          "-Dsonar.sourceEncoding=UTF-8 "
            }
        }
    }

    def waitForQualityGate() {
        script.timeout(time: 10, unit: 'MINUTES') {
            def qualityGateResult = script.waitForQualityGate()
            if (qualityGateResult.status != 'OK')
                script.error "[JENKINS][ERROR] Sonar quality gate check has been failed with status " +
                        "${qualityGateResult.status}"
        }
    }

    def waitForSonarAnalysis(ceTaskUrl) {
        script.println("[JENKINS][DEBUG] Waiting for report from Sonar")
        script.timeout(time: 10, unit: 'MINUTES') {
            while (true) {
                def status = getStatus(ceTaskUrl)
                script.println("[JENKINS][DEBUG] Current status: ${status}")

                if (status == 'FAILED') {
                    script.error "[JENKINS][ERROR] Sonar analysis finished with status: \'${status}\'"
                }

                if (status == 'SUCCESS') {
                    script.println("[JENKINS][ERROR] Sonar analysis finished with status: ${status}")
                    break
                }
            }
        }
    }

    def getStatus(ceTaskUrl) {
        def response = script.httpRequest acceptType: 'APPLICATION_JSON',
                url: ceTaskUrl,
                httpMode: 'GET',
                quiet: true

        def content = script.readJSON text: response.content
        return content.task.status
    }

    def getSonarReportInJson(workDir, url) {
        script.httpRequest acceptType: 'APPLICATION_JSON',
                url: url,
                httpMode: 'GET',
                outputFile: "${workDir}/target/sonar/sonar-report.json"
    }

    def sendStatusToGerrit(workDir, sonarURL) {
        script.dir("${workDir}") {
            script.sonarToGerrit inspectionConfig: [baseConfig: [projectPath: "${workDir}", sonarReportPath: "target/sonar/sonar-report.json"], serverURL: "${sonarURL}"],
                    notificationConfig: [commentedIssuesNotificationRecipient: 'NONE', negativeScoreNotificationRecipient: 'NONE'],
                    reviewConfig: [issueFilterConfig: [newIssuesOnly: false, changedLinesOnly: false, severity: 'CRITICAL']],
                    scoreConfig: [category: 'Sonar-Verified', noIssuesScore: +1, issuesScore: -1, issueFilterConfig: [severity: 'CRITICAL']]
        }
    }

    void run(context) {
        if (context.job.type == "build") {
            new SonarCleanupApplicationLibrary(script: script).run(context)
        }
        if (context.job.type == "codereview" && context.codebase.config.strategy != "import") {
            sendSonarScan(context.workDir, "${context.codebase.name}:change-${context.git.changeNumber}-${context.git.patchsetNumber}")

            def report = script.readProperties file: "${context.workDir}/.scannerwork/report-task.txt"
            def ceTaskUrl = report.ceTaskUrl
            waitForSonarAnalysis(ceTaskUrl)

            def url = "${context.sonar.route}/api/issues/search?componentKeys=${context.codebase.name}:change-${context.git.changeNumber}-${context.git.patchsetNumber}&branch=${context.git.branch}&resolved=false&facets=severities"
            getSonarReportInJson(context.workDir, url)

            sendStatusToGerrit(context.workDir, context.sonar.route)

            waitForQualityGate()
            return
        }
        sendSonarScan(context.workDir, context.codebase.name)
        waitForQualityGate()
    }
}