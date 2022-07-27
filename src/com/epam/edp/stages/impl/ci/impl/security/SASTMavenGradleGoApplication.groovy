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

package com.epam.edp.stages.impl.ci.impl.security

import org.apache.commons.lang.RandomStringUtils
import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage
import groovy.json.JsonSlurperClassic

@Stage(name = "sast", buildTool = ["maven","gradle","go"], type = [ProjectType.APPLICATION])
class SASTMavenGradleGoApplication {
    Script script

    void run(context) {
        try {
            script.dir("${context.workDir}") {
                script.stash name: 'all-repo', includes: "**", useDefaultExcludes: false
            }
            def testDir = "${RandomStringUtils.random(10, true, true)}"
            def defectDojoCredentials = getDefectDojoCredentials(context)

            script.node("sast") {
                script.dir("${testDir}") {
                    script.unstash 'all-repo'
                    def dataFromSemgrepScanner = runSemgrepScanner(context)
                    publishReport(defectDojoCredentials, dataFromSemgrepScanner)
                }
            }
        }
        catch (Exception ex) {
            script.unstable("[JENKINS][WARNING] SAST stage has failed with exception - ${ex}")
        }
    }

    def getDefectDojoCredentials(context){
        def defectDojoSecretJson = context.platform.getJsonValue("secret", "defectdojo-ciuser-token")
        def parsedDefectDojoSecretJson = new JsonSlurperClassic().parseText(defectDojoSecretJson)
        def defectDojo = [:]
        defectDojo.token = new String(parsedDefectDojoSecretJson.data.token.decodeBase64())
        defectDojo.url = new String(parsedDefectDojoSecretJson.data.url.decodeBase64())
        return defectDojo
    }

    def runSemgrepScanner(context) {
        def edpName = context.platform.getJsonPathValue("cm", "edp-config", ".data.edp_name")
        def reportData = [:]
        reportData.active = "true"
        reportData.verified = "false"
        reportData.path = "sast-semgrep-report.json"
        reportData.type = "Semgrep JSON Report"
        reportData.productTypeName = "Tenant"
        reportData.productName = "${edpName}"
        reportData.engagementName = "${context.codebase.name}-${context.git.branch}"
        reportData.autoCreateContext = "true"
        reportData.closeOldFindings = "true"
        reportData.pushToJira = "false"
        reportData.environment = "Development"
        reportData.testTitle = "SAST"
        script.sh (script: """
                set -ex
                semgrep --config=auto . --json --output ${reportData.path}
        """)
        return reportData
    }

    def publishReport(credentials, dataReport){
        script.println("[JENKINS][INFO] \n" +
                "curl -X POST ${credentials.url}/api/v2/import-scan/ \n" +
                "-H \"accept: application/json\" \n" +
                "-H \"Authorization: Token XXXXXXXXX\" \n" +
                "-H \"Content-Type: multipart/form-data\" \n" +
                "-F \"scan_date=\\\$(date +%Y-%m-%d)\" \n" +
                "-F \"minimum_severity=Info\" \n" +
                "-F \"active=${dataReport.active}\" \n" +
                "-F \"verified=${dataReport.verified}\" \n" +
                "-F \"scan_type=${dataReport.type}\" \n" +
                "-F \"file=@${dataReport.path};type=application/json\" \n" +
                "-F \"product_type_name=${dataReport.productTypeName}\" \n" +
                "-F \"product_name=${dataReport.productName}\" \n" +
                "-F \"engagement_name=${dataReport.engagementName}\" \n" +
                "-F \"auto_create_context=${dataReport.autoCreateContext}\" \n" +
                "-F \"close_old_findings=${dataReport.closeOldFindings}\" \n" +
                "-F \"push_to_jira=${dataReport.pushToJira}\" \n" +
                "-F \"environment=${dataReport.environment}\" \n" +
                "-F \"test_title=${dataReport.testTitle}\"")
        script.sh (script: """
            set +x
            curl -X POST "${credentials.url}/api/v2/import-scan/" \
                -H "accept: application/json" \
                -H "Authorization: Token ${credentials.token}" \
                -H "Content-Type: multipart/form-data" \
                -F "scan_date=\$(date +%Y-%m-%d)" \
                -F "minimum_severity=Info" \
                -F "active=${dataReport.active}" \
                -F "verified=${dataReport.verified}" \
                -F "scan_type=${dataReport.type}" \
                -F "file=@${dataReport.path};type=application/json" \
                -F "product_type_name=${dataReport.productTypeName}" \
                -F "product_name=${dataReport.productName}" \
                -F "engagement_name=${dataReport.engagementName}" \
                -F "auto_create_context=${dataReport.autoCreateContext}" \
                -F "close_old_findings=${dataReport.closeOldFindings}" \
                -F "push_to_jira=${dataReport.pushToJira}" \
                -F "environment=${dataReport.environment}" \
                -F "test_title=${dataReport.testTitle}"
        """)
    }
}