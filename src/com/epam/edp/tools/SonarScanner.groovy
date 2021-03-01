/* Copyright 2021 EPAM Systems.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.

See the License for the specific language governing permissions and
limitations under the License.*/

package com.epam.edp.tools


class SonarScanner {
    Script script

    SonarScanner(script) {
        this.script = script
    }

    def sendSonarScanWithCredentials(workDir, credentialsId, scriptText) {
        script.dir("${workDir}") {
            script.withCredentials([script.usernamePassword(credentialsId: "${credentialsId}",
                passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
                    script.withSonarQubeEnv('Sonar') {
                        script.sh scriptText.replace("LOGIN_REPLACE", "${script.USERNAME}")
                                            .replace("PASSWORD_REPLACE", "${script.PASSWORD}");
                    }
            }
        }
    }

    def sendSonarScanWithoutCredentials(workDir, scriptText) {
        script.dir("${workDir}") {
            script.withSonarQubeEnv('Sonar') {
                script.sh scriptText
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

    def getSonarReportInJson(workDir, url, path) {
        def sonarAuthHeader;
        script.dir("${workDir}") {
            script.withSonarQubeEnv('Sonar') {
                sonarAuthHeader="${script.env.SONAR_AUTH_TOKEN}:".bytes.encodeBase64().toString()
            }
        }
        script.httpRequest acceptType: 'APPLICATION_JSON',
                url: url,
                httpMode: 'GET',
                customHeaders: [[name: 'Authorization', value: "Basic ${sonarAuthHeader}"]],
                outputFile: "${workDir}/${path}/sonar-report.json"
    }

    def sendStatusToGerrit(workDir, sonarURL, path) {
        script.dir("${workDir}") {
            script.sonarToGerrit inspectionConfig: [baseConfig: [projectPath: "${workDir}", sonarReportPath: "${path}/sonar-report.json"], serverURL: "${sonarURL}"],
                    notificationConfig: [commentedIssuesNotificationRecipient: 'NONE', negativeScoreNotificationRecipient: 'NONE'],
                    reviewConfig: [issueFilterConfig: [newIssuesOnly: false, changedLinesOnly: false, severity: 'CRITICAL']],
                    scoreConfig: [category: 'Sonar-Verified', noIssuesScore: +1, issuesScore: -1, issueFilterConfig: [severity: 'CRITICAL']]
        }
    }

    def cleanSonarProjectRange(patchsetNumber, sonarRoute, sonarProjectKey, sonarAuthHeader) {
        for (int i = 1; i <= (patchsetNumber as Integer) ; i++) {
            def response = script.httpRequest url: "${sonarRoute}/api/components/show?key=${sonarProjectKey}-${i}",
                    httpMode: 'GET',
                    customHeaders: [[name: 'Authorization', value: "Basic ${sonarAuthHeader}"]],
                    validResponseCodes: '100:399,404'
            if (response.status == 200) {
                script.httpRequest url: "${sonarRoute}/api/projects/delete?key=${sonarProjectKey}-${i}",
                        httpMode: 'POST',
                        customHeaders: [[name: 'Authorization', value: "Basic ${sonarAuthHeader}"]]
                script.println("[JENKINS][DEBUG] Project ${sonarProjectKey}-${i} deleted")
            }
        }
    }
}