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

package com.epam.edp.stages.impl.ci.impl.createbranch

import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage

@Stage(name = "create-branch", buildTool = ["maven", "npm", "dotnet", "gradle", "any"], type = [ProjectType.APPLICATION, ProjectType.AUTOTESTS, ProjectType.LIBRARY])
class CreateBranch {
    Script script

    void run(context) {
        script.dir("${context.workDir}") {
            script.sshagent (credentials: ["${context.git.credentialsId}"]) {
                try {
                    script.sh """
                        export GIT_SSH_COMMAND="ssh -o StrictHostKeyChecking=no"
                        export GIT_SSH_VARIANT=ssh
                        git config --local user.email ${context.git.autouser}@edp.ci-user
                        git config --local user.name ${context.git.autouser}
                        if [[ -z `git ls-remote --heads origin ${context.job.releaseName}` ]]; then
                            git branch ${context.job.releaseName} ${context.job.releaseFromCommitId}
                            git push --all
                        fi
                    """
                }
                catch (Exception ex) {
                    script.error "[JENKINS][ERROR] Create branch has failed with exception - ${ex}"
                }

            }
        }
    }
}
