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

package com.epam.edp.stages.impl.getversion

import com.epam.edp.stages.ProjectType
import com.epam.edp.stages.Stage

@Stage(name = "get-version", buildTool = ["gradle"], type = ProjectType.APPLICATION)
class GradleApplication {
    Script script

    void run(context) {
        script.dir("${context.workDir}") {
            script.withCredentials([script.usernamePassword(credentialsId: "${context.nexus.credentialsId}",
                    passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
                context.artifactID = script.sh(
                        script: """
                        ${context.buildTool.command} -PnexusLogin=${script.USERNAME} -PnexusPassword=${script.PASSWORD} \
                        properties|egrep "rootProject: root project "|awk -F "'" '{print \$2}' 
                    """,
                        returnStdout: true
                ).trim()

                context.artifactVersion = script.sh(
                        script: """
                        ${context.buildTool.command} -PnexusLogin=${script.USERNAME} -PnexusPassword=${script.PASSWORD} \
                        properties|egrep "version: "|awk '{print \$2}'    
                    """,
                        returnStdout: true
                ).trim().toLowerCase()

                context.groupID = script.sh(
                        script: """
                    ${context.buildTool.command.command} -PnexusLogin=${script.USERNAME} -PnexusPassword=${script.PASSWORD} \
                    properties|egrep \"group: \"|awk '{print \$2}'
                """,
                        returnStdout: true
                ).trim()
            }
            context.deployableModule = "${context.artifactID}".trim()
            context.businissAppVersion = "${context.artifactVersion}-${script.BUILD_NUMBER}"
            script.println("[JENKINS][DEBUG] Deployable module: ${context.deployableModule}")
        }
        script.println("[JENKINS][DEBUG] Artifact version - ${context.artifactVersion}")
    }
}
