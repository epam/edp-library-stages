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

package com.epam.edp.stages.impl.ci.impl.gittag


import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage

import com.epam.edp.stages.impl.ci.impl.codebaseiamgestream.CodebaseImageStreams


@Stage(name = "git-tag", buildTool = ["gradle", "maven", "dotnet", "npm", "any"], type = [ProjectType.APPLICATION, ProjectType.AUTOTESTS, ProjectType.LIBRARY])
class GitTagApplicationLibrary {
    Script script

    void run(context) {
        script.dir("${context.workDir}") {
            script.sshagent (credentials: ["${context.git.credentialsId}"]) {
                script.sh """
                export GIT_SSH_COMMAND="ssh -o StrictHostKeyChecking=no"
                export GIT_SSH_VARIANT=ssh
                git config --global user.email ${context.git.autouser}@edp.ci-user
                git config --global user.name ${context.git.autouser}
                git tag -a ${context.codebase.vcsTag} -m 'Tag is added automatically by ${context.git.autouser} user'
                git push --tags"""
            }
            def resultImageName = "${context.codebase.name}-${context.git.branch.replaceAll("[^\\p{L}\\p{Nd}]+", "-")}"
            def dockerRegistryHost = context.platform.getJsonPathValue("edpcomponent", "docker-registry", ".spec.url")
            new CodebaseImageStreams(context, script)
                .UpdateOrCreateCodebaseImageStream(resultImageName, "${dockerRegistryHost}/${resultImageName}", context.codebase.isTag)
        }
    }
}
