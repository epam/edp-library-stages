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

package com.epam.edp.stages.impl.ci.impl.sonar


import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage
import com.epam.edp.stages.impl.ci.impl.sonarcleanup.SonarCleanup
import com.epam.edp.tools.SonarScanner

@Stage(name = "sonar", buildTool = "codenarc", type = [ProjectType.LIBRARY])
class SonarCodenarc {
    Script script

    void run(context) {
        SonarScanner sonarScanner = new SonarScanner(script);
        def buildTool = context.buildTool;
        def workDir = context.workDir;
        def path = "build/sonar";
        def credentialsId = context.nexus.credentialsId;
        def codebaseName;
        if (context.job.type == "codereview" && context.codebase.config.strategy != "import") {
            codebaseName = "${context.codebase.name}:change-${context.git.changeNumber}-${context.git.patchsetNumber}";
        } else {
            codebaseName = context.codebase.name;
        }
        def scriptText = """ ${buildTool.command} ${context.buildTool.properties} -PnexusLogin=LOGIN_REPLACE -PnexusPassword=PASSWORD_REPLACE \
                             sonarqube -Dsonar.projectKey=${codebaseName} \
                             -Dsonar.projectName=${codebaseName} """;
        if (context.job.type == "build") {
            new SonarCleanup(script: script).run(context)
        }
        if (context.job.type == "codereview" && context.codebase.config.strategy != "import") {
            sonarScanner.sendSonarScanWithCredentials(workDir, credentialsId, scriptText)

            def report = script.readProperties file: "${workDir}/${path}/report-task.txt"
            def ceTaskUrl = report.ceTaskUrl
            sonarScanner.waitForSonarAnalysis(ceTaskUrl)

            def url = "${context.sonar.route}/api/issues/search?componentKeys=${context.codebase.name}:change-${context.git.changeNumber}-${context.git.patchsetNumber}&branch=${context.git.branch}&resolved=false&facets=severities"
            sonarScanner.getSonarReportInJson(workDir, url, path)

            sonarScanner.sendStatusToGerrit(workDir, context.sonar.route, path)

            sonarScanner.waitForQualityGate()
            return
        }
        sonarScanner.sendSonarScanWithCredentials(workDir, credentialsId, scriptText)
        sonarScanner.waitForQualityGate()
    }
}