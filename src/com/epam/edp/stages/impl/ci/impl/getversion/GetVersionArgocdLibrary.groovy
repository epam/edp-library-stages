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

package com.epam.edp.stages.impl.ci.impl.getversion


import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage

@Stage(name = "get-version", buildTool = "argocd", type = ProjectType.LIBRARY)
class GetVersionArgocdLibrary {
    Script script

    def updateVersions(workDir, codebase, git, job) {
        if (codebase.config.versioningType == "edp") {
            updateBuildNumber(codebase.config.name, git.branch.replaceAll(/\//, "-"), codebase.currentBuildNumber)
            codebase.vcsTag = "build/${codebase.version}"
            codebase.isTag = "${codebase.version}"
        } else {
            codebase.version = "${script.currentBuild.number}-${git.branch}"
        }

        job.setDisplayName("${script.currentBuild.number}-${git.branch}")
        codebase.isTag = "${script.currentBuild.number}-${git.branch}"
        codebase.vcsTag = codebase.isTag
    }

    def updateBuildNumber(codebaseName, branchName, buildNumber) {
        script.sh """kubectl patch codebasebranches.v2.edp.epam.com ${codebaseName}-${
            branchName
        } --subresource=status --type=merge -p '{\"status\": {\"build\": "${buildNumber}"}}'"""
    }

    void run(context) {
        updateVersions(context.workDir, context.codebase, context.git, context.job)
        context.codebase.deployableModuleDir = "${context.workDir}"
        script.println("[JENKINS][DEBUG] VCS tag - ${context.codebase.vcsTag}")
        script.println("[JENKINS][DEBUG] IS tag - ${context.codebase.isTag}")
    }
}
