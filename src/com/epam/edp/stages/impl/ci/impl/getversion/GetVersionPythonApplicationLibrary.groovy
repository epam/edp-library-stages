/* Copyright 2020 EPAM Systems.

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

@Stage(name = "get-version", buildTool = ["python"], type = [ProjectType.APPLICATION, ProjectType.LIBRARY])
class GetVersionPythonApplicationLibrary {
    Script script

    def updateBuildNumber(context) {
        script.dir("${context.workDir}") {
            script.sh "sed -i 's/\\(__version__\\s*=\\s*\\).*/\\1\"${context.codebase.buildVersion}\"/' version/__init__.py"
        }
    }
    def updateVersions(context){
        if (context.codebase.config.versioningType == "edp") {
            script.dir("${context.workDir}") {
                script.sh """ kubectl patch codebasebranches.v2.edp.epam.com ${context.codebase.config.name}-${context.git.branch.replaceAll(/\//, "-")} --type=merge -p '{\"status\": {\"build\": "${context.codebase.currentBuildNumber}"}}'
                """
            }
            context.codebase.vcsTag = "build/${context.codebase.version}"
            context.codebase.isTag = "${context.codebase.version}"
        } else {
            script.dir("${context.workDir}") {
                context.codebase.version = script.sh(
                        script: """
                            python setup.py version | sed -n 2p
                        """,
                        returnStdout: true
                ).trim().toLowerCase()
            }
        context.job.setDisplayName("${script.currentBuild.number}-${context.git.branch}-${context.codebase.version}")
        context.codebase.buildVersion = "${context.codebase.version}-${script.BUILD_NUMBER}"
        context.codebase.vcsTag = "${context.git.branch}-${context.codebase.buildVersion}"
        context.codebase.isTag = "${context.git.branch}-${context.codebase.buildVersion}"
        }
    }

    void run(context) {
        updateVersions(context)
        context.codebase.deployableModuleDir = "${context.workDir}"
        if (!context.codebase.version.toLowerCase().contains("snapshot"))
            updateBuildNumber(context)
        script.println("[JENKINS][DEBUG] Application version - ${context.codebase.version}")
        script.println("[JENKINS][DEBUG] VCS tag - ${context.codebase.vcsTag}")
        script.println("[JENKINS][DEBUG] IS tag - ${context.codebase.isTag}")
    }
}
