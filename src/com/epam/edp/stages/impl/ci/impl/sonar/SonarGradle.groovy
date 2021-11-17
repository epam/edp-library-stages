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
import org.apache.commons.lang.RandomStringUtils

@Stage(name = "sonar", buildTool = "gradle", type = [ProjectType.APPLICATION, ProjectType.AUTOTESTS, ProjectType.LIBRARY])
class SonarGradle {
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
                  cp -f build.gradle ${codereviewAnalysisRunDir};
                  for build in `find . -type d -name \'build\'`; do cp --parents -r \${build} ${codereviewAnalysisRunDir}; done
                  """
            }
        }
        SonarScanner sonarScanner = new SonarScanner(script);
        def buildTool = context.buildTool;
        def path = "build/sonar";
        def credentialsId = context.nexus.credentialsId;
        def codebaseName;
        def workDir;
        if (context.job.type == "codereview" && context.codebase.config.strategy != "import") {
            codebaseName = "${context.codebase.name}:change-${context.git.changeNumber}-${context.git.patchsetNumber}";
            workDir = codereviewAnalysisRunDir;
        } else {
            codebaseName = context.codebase.name;
            workDir = context.workDir;
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

            sonarScanner.waitForQualityGate()
            return
        }
        sonarScanner.sendSonarScanWithCredentials(workDir, credentialsId, scriptText)
        sonarScanner.waitForQualityGate()
    }
}