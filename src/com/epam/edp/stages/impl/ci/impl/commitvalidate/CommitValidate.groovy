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

package com.epam.edp.stages.impl.ci.impl.commitvalidate


import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage

@Stage(name = "commit-validate", buildTool = ["maven", "npm", "dotnet", "gradle", "any"], type = [ProjectType.APPLICATION, ProjectType.AUTOTESTS, ProjectType.LIBRARY])
class CommitValidate {
    Script script

    def getLastCommitMessage(workDir) {
        script.dir("${workDir}") {
            return script.sh(
                    script: "git log -1 --pretty=%B",
                    returnStdout: true
            )
        }
    }

    def isCommitMessageValid(msg, pattern) {
        return msg.find(/(?m)${pattern}/) != null
    }

    def run(context) {
        def pattern = context.codebase.config.commitMessagePattern
        script.println("[JENKINS][DEBUG] Pattern to validate commit message: ${pattern}")
        def msg = getLastCommitMessage(context.workDir)
        script.println("[JENKINS][DEBUG] Commit message to validate has been fetched:\n ${msg}")

        if (!isCommitMessageValid(msg, pattern)) {
            script.error "[JENKINS][ERROR] Commit message is invalid"
        }
    }
}
