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
import com.epam.edp.stages.impl.ci.impl.sonarcleanup.SonarCleanupApplication
import org.apache.commons.lang.RandomStringUtils

@Stage(name = "sonar", buildTool = ["maven"], type = [ProjectType.APPLICATION,ProjectType.AUTOTESTS])
class SonarMaven {
    Script script

    void run(context) {
        def codereviewAnalysisRunDir = context.workDir
        if (context.job.type == "codereview") {
            codereviewAnalysisRunDir = new File("${context.workDir}/../${RandomStringUtils.random(10, true, true)}")

            script.dir("${codereviewAnalysisRunDir}") {
                if (script.fileExists("${context.workDir}/target")) {
                    script.println("[JENKINS][DEBUG] Project with usual structure")
                    script.sh """
              export LANG=en_US.utf-8
              cd ${context.workDir}
              git config --local core.quotepath false
              IFS=\$'\\n';for i in \$(git diff --diff-filter=ACMR --name-only origin/master); \
                do cp --parents \"\$i\" ${codereviewAnalysisRunDir}/; done
              cp -f pom.xml ${codereviewAnalysisRunDir}/
              cp --parents -r src/test/ ${codereviewAnalysisRunDir}
              cp --parents -r target/ ${codereviewAnalysisRunDir}
              """
                } else {
                    script.println("[JENKINS][DEBUG] Multi-module project")
                    script.sh """
              mkdir -p ${codereviewAnalysisRunDir}/unittests
              cd ${context.workDir}
              IFS=\$'\\n';for i in \$(git diff --diff-filter=ACMR --name-only origin/master); \
                do cp --parents \"\$i\" ${codereviewAnalysisRunDir}/; done
              for directory in `find . -type d -name \'test\'`; do cp --parents -r \${directory} \
              ${codereviewAnalysisRunDir}/unittests; done
              for poms in `find . -type f -name \'pom.xml\'`; do cp --parents -r \${poms} \
              ${codereviewAnalysisRunDir}; done
              for targets in `find . -type d -name \'target\'`; do cp --parents -r \${targets} \
              ${codereviewAnalysisRunDir}; done
              """
                }

                script.withSonarQubeEnv('Sonar') {
                    script.withCredentials([script.usernamePassword(credentialsId: "${context.nexus.credentialsId}",
                            passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
                        script.sh "${context.buildTool.command} -Dartifactory.username=${script.USERNAME} -Dartifactory.password=${script.PASSWORD} " +
                                "sonar:sonar -Dsonar.analysis.mode=preview " +
                                "-Dsonar.report.export.path=sonar-report.json " +
                                "-Dsonar.branch=codereview -B -Dsonar.projectName=${context.codebase.name} " +
                                "-Dsonar.projectKey=${context.codebase.name}"
                    }
                }
                script.sonarToGerrit inspectionConfig: [baseConfig: [projectPath: ""],
                                                        serverURL: "${context.sonar.route}"],
                        notificationConfig: [commentedIssuesNotificationRecipient: 'NONE',
                                             negativeScoreNotificationRecipient: 'NONE'],
                        reviewConfig: [issueFilterConfig: [newIssuesOnly: false, changedLinesOnly: false,
                                                           severity: 'CRITICAL']],
                        scoreConfig: [category: 'Sonar-Verified', issueFilterConfig: [severity: 'CRITICAL']]
            }
        }

        script.dir("${codereviewAnalysisRunDir}") {
            script.withSonarQubeEnv('Sonar') {
                script.withCredentials([script.usernamePassword(credentialsId: "${context.nexus.credentialsId}",
                        passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
                    script.sh "${context.buildTool.command} -Dartifactory.username=${script.USERNAME} -Dartifactory.password=${script.PASSWORD} " +
                            "sonar:sonar " +
                            "-Dsonar.projectKey=${context.codebase.name} " +
                            "-Dsonar.projectName=${context.codebase.name} " +
                            "-Dsonar.branch=" +
                            "${context.job.type == "codereview" ? context.gerrit.changeName : context.gerrit.branch}"
                }
            }
            script.timeout(time: 10, unit: 'MINUTES') {
                def qualityGateResult = script.waitForQualityGate()
                if (qualityGateResult.status != 'OK')
                    script.error "[JENKINS][ERROR] Sonar quality gate check has been failed with status " +
                            "${qualityGateResult.status}"
            }
        }

        if (context.job.type == "build")
            new SonarCleanupApplication(script: script).run(context)
    }
}
