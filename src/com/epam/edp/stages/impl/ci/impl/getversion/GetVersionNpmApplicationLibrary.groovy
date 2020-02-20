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

@Stage(name = "get-version", buildTool = ["npm"], type = [ProjectType.APPLICATION, ProjectType.LIBRARY])
class GetVersionNpmApplicationLibrary {
    Script script
    def setVersionToArtifact(buildNumber, context) {
       def newBuildNumber = ++buildNumber
       script.sh """
            sed -i "/version/c\\  \\"version\\": \\"${context.codebase.config.startFrom}-${newBuildNumber}\\"," package.json
        """

       return "${context.codebase.config.startFrom}-${newBuildNumber}"
    }

    def updateCodebaseBranchCR(buildNumber, context) {
        def newBuildNumber = ++buildNumber
        script.sh"""
            kubectl patch codebasebranches.v2.edp.epam.com ${context.codebase.config.name}-${context.git.branch} --type=merge -p '{\"spec\": {\"build\": "${newBuildNumber}"}}'
        """
    }

    void run(context) {
        script.dir("${context.workDir}") {
            if (context.codebase.config.versioningType == "edp") {
                context.codebase.version = setVersionToArtifact(context.codebase.config.codebase_branch.build_number.get(0).toInteger(), context)
                context.codebase.buildVersion = "${context.codebase.version}"

                updateCodebaseBranchCR(context.codebase.config.codebase_branch.build_number.get(0).toInteger(), context)
            } else {
                context.codebase.version = script.sh(
                    script: """
                        node -p "require('./package.json').version"
                    """, returnStdout: true
                    ).trim().toLowerCase()
                context.codebase.buildVersion = "${context.codebase.version}-${script.BUILD_NUMBER}"
            }
        }
        context.job.setDisplayName("${script.currentBuild.number}-${context.git.branch}-${context.codebase.version}")
        context.codebase.deployableModuleDir = "${context.workDir}"
        script.println("[JENKINS][DEBUG] Application version - ${context.codebase.version}")
    }
}
