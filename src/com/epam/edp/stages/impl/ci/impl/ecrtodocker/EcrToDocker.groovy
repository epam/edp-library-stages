/* Copyright 2021 EPAM Systems.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.

See the License for the specific language governing permissions and
limitations under the License.*/

package com.epam.edp.stages.impl.ci.impl.ecrtodocker

import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage

@Stage(name = "ecr-to-docker", buildTool = "any", type = [ProjectType.APPLICATION])
class EcrToDocker {
    Script script

    void run(context) {
        def dockerCredentialsId = context.job.getParameterValue(context, "dockerCredentialsId")
        def repoCredentialsId = context.job.getParameterValue(context, "repoCredentialsId")
        def edpComponent = "edpcomponent"
        def dockerRegistry = "docker-registry"
        def ecr = context.platform.getJsonPathValue(edpComponent, dockerRegistry, ".spec.url")
        def ecrRepo = context.platform.getJsonPathValue(edpComponent, dockerRegistry, ".metadata.namespace")
        def cbImage = context.codebase.config.name
        def cbTag = context.codebase.buildVersion
        def codebaseBranch = getCodebaseBranch(context.codebase.config.codebase_branch, context.git.branch)
        def cbBranch = codebaseBranch.branchName

        script.node("edp-helm") {
           script.dir("${context.workDir}") {
              script.withCredentials([script.usernamePassword(credentialsId: "${dockerCredentialsId}",
                usernameVariable: 'DOCKER_LOGIN', passwordVariable: 'DOCKER_PASS')]) {
                        script.sh """
                            set -ex
                            awsv2 ecr get-login-password --region eu-central-1 | crane auth login ${ecr} -u AWS --password-stdin
                            crane auth login index.docker.io -u ${script.DOCKER_LOGIN} -p ${script.DOCKER_PASS}
                            """
                }
              script.withCredentials([script.string(credentialsId: "${repoCredentialsId}", variable: 'DH_REPO')]) {
                        script.sh """
                            if curl --silent -f --head -lL https://hub.docker.com/v2/repositories/${script.DH_REPO}/${cbImage}/tags/${cbTag}/ > /dev/null; then
                                echo "${cbImage}:${cbTag} already exists in Docker Hub"
                                exit 1
                            else
                                echo "${cbImage}:${cbTag} was not found in Docker Hub, start copying"
                                crane cp ${ecr}/${ecrRepo}/${cbImage}-${cbBranch}:${cbTag} index.docker.io/${script.DH_REPO}/${cbImage}:${cbTag}
                            fi
                            """
                }
           }
        }
    }
    @NonCPS
    def private getCodebaseBranch(codebaseBranch, gitBranchName) {
        return codebaseBranch.stream().filter({
           it.branchName == gitBranchName
        }).findFirst().get()
    }
}