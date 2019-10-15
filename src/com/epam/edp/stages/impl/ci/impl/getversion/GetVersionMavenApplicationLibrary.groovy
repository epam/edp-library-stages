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

@Stage(name = "get-version", buildTool = ["maven"], type = [ProjectType.APPLICATION, ProjectType.LIBRARY])
class GetVersionMavenApplicationLibrary {
    Script script

    void run(context) {
        script.dir("${context.workDir}") {
            script.withCredentials([script.usernamePassword(credentialsId: "${context.nexus.credentialsId}",
                    passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
                context.codebase.version = script.sh(
                        script: """
                        ${context.buildTool.command} -Dartifactory.username=${script.USERNAME} -Dartifactory.password=${script.PASSWORD} \
                        org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate \
                        -Dexpression=project.version -B |grep -Ev '(^\\[|Download\\w+:)'
                    """,
                        returnStdout: true
                ).trim().toLowerCase()
            }
            context.codebase.deployableModule = script.sh(
                    script: "cat pom.xml | grep -Poh '<deployable.module>\\K[^<]*' || echo \"\"",
                    returnStdout: true
            ).trim()
            context.job.setDisplayName("${script.currentBuild.number}-${context.git.branch}-${context.codebase.version}")
            context.codebase.buildVersion = "${context.codebase.version}-${script.BUILD_NUMBER}"
            script.println("[JENKINS][DEBUG] Deployable module: ${context.codebase.deployableModule}")
            context.codebase.deployableModuleDir = context.codebase.deployableModule.isEmpty() ? "${context.workDir}/target" :
                    "${context.workDir}/${context.codebase.deployableModule}/target"
        }
        script.println("[JENKINS][DEBUG] Application version - ${context.codebase.version}")
    }
}
