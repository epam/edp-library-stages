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

package com.epam.edp.stages.impl.ci.impl.getversion


import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage

@Stage(name = "get-version", buildTool = ["gradle"], type = ProjectType.APPLICATION)
class GetVersionGradleApplication {
    Script script

    void run(context) {
        script.dir("${context.workDir}") {
            script.withCredentials([script.usernamePassword(credentialsId: "${context.nexus.credentialsId}",
                    passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
                context.application.version = script.sh(
                        script: """
                        set +x
                        ${context.buildTool.command} -PnexusLogin=${script.USERNAME} -PnexusPassword=${script.PASSWORD} properties -q | grep "version:" | awk '{print \$2}'    
                    """,
                        returnStdout: true
                ).trim().toLowerCase()
            }
            context.job.setDisplayName("${script.currentBuild.number}-${context.gerrit.branch}-${context.application.version}")
            context.application.buildVersion = "${context.application.version}-${script.BUILD_NUMBER}"
            context.application.deployableModuleDir = "${context.workDir}/build/libs"
        }
        script.println("[JENKINS][DEBUG] Artifact version - ${context.application.version}")
    }
}
