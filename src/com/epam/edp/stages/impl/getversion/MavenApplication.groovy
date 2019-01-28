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

@Stage(name = "get-version", buildTool = ["maven"], type = ProjectType.APPLICATION)
class MavenApplication {
    Script script

    void run(context) {
        script.dir("${context.workDir}") {
            context.pomVersion = script.sh(
                    script: """
                        mvn org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate \
                        -Dexpression=project.version|grep -Ev '(^\\[|Download\\w+:)'
                    """,
                    returnStdout: true
            ).trim().toLowerCase()
            context.groupID = script.sh(
                    script: "mvn org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate -Dexpression=" +
                            "project.groupId | grep -Ev '(^\\[|Download\\w+:)'",
                    returnStdout: true
            ).trim()
            context.artifactID = script.sh(
                    script: "mvn org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate -Dexpression=" +
                            "project.artifactId | grep -Ev '(^\\[|Download\\w+:)'",
                    returnStdout: true
            ).trim()
            context.deployableModule = script.sh(
                    script: "cat pom.xml | grep -Poh '<deployable.module>\\K[^<]*' || echo \"\"",
                    returnStdout: true
            ).trim()
            context.businissAppVersion = "${context.pomVersion}-${script.BUILD_NUMBER}"
            script.println("[JENKINS][DEBUG] Deployable module: ${context.deployableModule}")
        }
        script.println("[JENKINS][DEBUG] Pom version - ${context.pomVersion}")
    }
}
