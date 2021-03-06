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
import com.epam.edp.stages.impl.ci.impl.sonarcleanup.SonarCleanupApplicationLibrary

@Stage(name = "sonar", buildTool = ["dotnet"], type = [ProjectType.APPLICATION, ProjectType.LIBRARY])
class SonarDotnetApplicationLibrary {
    def getSonarReportJson(context, codereviewAnalysisRunDir) {
        String sonarAnalysisStatus
        def sonarReportMap = script.readProperties file: "${codereviewAnalysisRunDir}/.sonarqube/out/.sonar/report-task.txt"
        def sonarJsonReportLink = "${context.sonar.route}/api/issues/search?componentKeys=${context.codebase.name}:change-${context.git.changeNumber}-${context.git.patchsetNumber}&branch=${context.git.branch}&resolved=false&facets=severities"

        script.println("[JENKINS][DEBUG] Waiting for report from Sonar")
        script.timeout(time: 10, unit: 'MINUTES') {
            while (sonarAnalysisStatus != 'SUCCESS') {
                if (sonarAnalysisStatus == 'FAILED') {
                    script.error "[JENKINS][ERROR] Sonar analysis finished with status: \'${sonarAnalysisStatus}\'"
                }
                def response = script.httpRequest acceptType: 'APPLICATION_JSON',
                        url: sonarReportMap.ceTaskUrl,
                        httpMode: 'GET',
                        quiet: true

                def content = script.readJSON text: response.content
                sonarAnalysisStatus = content.task.status
                script.println("[JENKINS][DEBUG] Current status: " + sonarAnalysisStatus)
            }
        }

        script.httpRequest acceptType: 'APPLICATION_JSON',
                    url: sonarJsonReportLink,
                    httpMode: 'GET',
                    outputFile: "${codereviewAnalysisRunDir}/.sonarqube/out/.sonar/sonar-report.json"
    }

    def sendReport(sonarURL, codereviewAnalysisRunDir) {
        script.dir("${codereviewAnalysisRunDir}") {
            script.sonarToGerrit inspectionConfig: [baseConfig: [projectPath: "${codereviewAnalysisRunDir}", sonarReportPath: "${codereviewAnalysisRunDir}/.sonarqube/out/.sonar/sonar-report.json"], serverURL: "${sonarURL}"],
                    notificationConfig: [commentedIssuesNotificationRecipient: 'NONE', negativeScoreNotificationRecipient: 'NONE'],
                    reviewConfig: [issueFilterConfig: [newIssuesOnly: false, changedLinesOnly: false, severity: 'CRITICAL']],
                    scoreConfig: [category: 'Sonar-Verified', noIssuesScore: +1, issuesScore: -1, issueFilterConfig: [severity: 'CRITICAL']]
        }
    }

    def sendSonarScan(sonarProjectName, codereviewAnalysisRunDir, buildTool, scannerCommand) {
        script.dir("${codereviewAnalysisRunDir}") {
                 script.withSonarQubeEnv('Sonar') {
                     script.sh """
                     ${scannerCommand} begin /k:${sonarProjectName} \
                     /k:${sonarProjectName} \
                     /n:${sonarProjectName} \
                     /d:sonar.cs.opencover.reportsPaths=${codereviewAnalysisRunDir}/*Tests*/*.xml
                     dotnet build ${buildTool.sln_filename}
                     ${scannerCommand} end
                 """
                 }
        }
    }
    def runSonarScannerDependsOnPlatformAndStrategy(context, platform, codereviewAnalysisRunDir, scannerCommand) {
        if (platform == "kubernetes" || context.codebase.config.strategy == "import") {
            sendSonarScan(context.codebase.name, codereviewAnalysisRunDir, context.buildTool, scannerCommand)
        } else {
            sendSonarScan("${context.codebase.name}:change-${context.git.changeNumber}-${context.git.patchsetNumber}", codereviewAnalysisRunDir, context.buildTool, scannerCommand)
            getSonarReportJson(context, codereviewAnalysisRunDir)
            sendReport(context.sonar.route, codereviewAnalysisRunDir)
        }
    }
    Script script

    void run(context) {
        def codereviewAnalysisRunDir = context.workDir
        def scannerHomePath = script.tool 'SonarScannerMSBuild'
        def scannerCommand =  "/home/jenkins/.dotnet/tools/dotnet-sonarscanner"
        if (context.job.type == "codereview") {
            runSonarScannerDependsOnPlatformAndStrategy(context, System.getenv("PLATFORM_TYPE"), codereviewAnalysisRunDir, scannerCommand)
        } else {
            sendSonarScan(context.codebase.name, codereviewAnalysisRunDir, context.buildTool, scannerCommand)
        }
        script.timeout(time: 10, unit: 'MINUTES') {
            def qualityGateResult = script.waitForQualityGate()
            if (qualityGateResult.status != 'OK')
                script.error "[JENKINS][ERROR] Sonar quality gate check has been failed with status " +
                        "${qualityGateResult.status}"
        }

        if (context.job.type == "build")
            new SonarCleanupApplicationLibrary(script: script).run(context)
    }
}
