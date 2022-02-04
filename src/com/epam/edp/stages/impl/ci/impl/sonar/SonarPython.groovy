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

package com.epam.edp.stages.impl.ci.impl.sonar


import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage
import com.epam.edp.stages.impl.ci.impl.sonarcleanup.SonarCleanup
import com.epam.edp.tools.SonarScanner
import org.apache.commons.lang.RandomStringUtils

@Stage(name = "sonar", buildTool = "python", type = [ProjectType.APPLICATION, ProjectType.LIBRARY])
class SonarPython {
    Script script

    void run(context) {
        def codereviewAnalysisRunDir = context.workDir
        if (context.job.type == "codereview") {
            codereviewAnalysisRunDir = new File("${context.workDir}/../${RandomStringUtils.random(10, true, true)}")

            script.dir("${codereviewAnalysisRunDir}") {
                script.sh """
                  export LANG=en_US.utf-8
                  cd ${context.workDir}
                  git config --local core.quotepath false
                  IFS=\$'\\n';for i in \$(git diff --diff-filter=ACMR --name-only origin/${context.git.branch}); \
                    do cp --parents \"\$i\" ${codereviewAnalysisRunDir}/; echo "file for scanner:" \"\$i\"/; done
                  """
            }
        }
        SonarScanner sonarScanner = new SonarScanner(script);
        def buildTool = context.buildTool;
        def path = ".scannerwork";
        def scannerHome = script.tool 'SonarQube Scanner';
        def codebaseName;
        def workDir;
        if (context.job.type == "codereview" && context.codebase.config.strategy != "import") {
            codebaseName = "${context.codebase.name}-${context.git.normalizedBranch}:change-${context.git.changeNumber}-${context.git.patchsetNumber}";
            workDir = codereviewAnalysisRunDir;
        } else {
            codebaseName = "${context.codebase.name}-${context.git.normalizedBranch}";
            workDir = context.workDir;
        }
        def scriptText = """ ${scannerHome}/bin/sonar-scanner \
                             -Dsonar.projectKey=${codebaseName} \
                             -Dsonar.projectName=${codebaseName} \
                             -Dsonar.language=py \
                             -Dsonar.sourceEncoding=UTF-8 """;
        if (context.job.type == "build") {
            new SonarCleanup(script: script).run(context)
        }
        if (context.job.type == "codereview" && context.codebase.config.strategy != "import") {
            sonarScanner.sendSonarScanWithoutCredentials(workDir, scriptText)

            def report = script.readProperties file: "${workDir}/${path}/report-task.txt"
            def ceTaskUrl = report.ceTaskUrl
            sonarScanner.waitForSonarAnalysis(ceTaskUrl)

            sonarScanner.waitForQualityGate()
            return
        }
        sonarScanner.sendSonarScanWithoutCredentials(workDir, scriptText)
        sonarScanner.waitForQualityGate()
    }
}