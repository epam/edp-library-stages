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

package com.epam.edp.stages.impl.ci.impl.ecrtodocker

import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage
import groovy.json.JsonSlurperClassic

@Stage(name = "ecr-to-docker", buildTool = "any", type = [ProjectType.APPLICATION])
class EcrToDocker {
    Script script

    def getDockerhubToken (username, accessToken){
        def dockerhubTokenRaw = script.httpRequest url: "https://hub.docker.com/v2/users/login",
                            httpMode: "POST",
                            contentType: 'APPLICATION_JSON',
                            requestBody: "{\"username\": \"${username}\",\"password\": \"${accessToken}\"}",
                            consoleLogResponseBody: false,
                            validResponseCodes: '200'
        def dockerhubToken = new groovy.json.JsonSlurperClassic().parseText(dockerhubTokenRaw.content).token
        return dockerhubToken
    }

    def getDockerhubImageExistanse (repo, token, tag) {
        def response = script.httpRequest url: "https://hub.docker.com/v2/repositories/${repo}/tags/${tag}",
                            httpMode: 'GET',
                            customHeaders: [[name: 'Authorization', value: "Bearer ${token}"]],
                            consoleLogResponseBody: false,
                            validResponseCodes: '200,404'
        return response.status
    }

    void run(context) {
        def ecr = context.platform.getJsonPathValue("edpcomponent", "docker-registry", ".spec.url")
        def ecrRepo = context.platform.getJsonPathValue("edpcomponent", "docker-registry", ".metadata.namespace")
        def cbImage = context.codebase.config.name

        script.node("edp-helm") {
            script.dir("${context.workDir}") {
                script.openshift.withCluster() {
                    script.openshift.withProject() {
                        def awsRegion = script.openshift.selector("cm", "edp-config").object().data.aws_region
                        def secretName = "dockerhub-credentials"
                        def secretExist = script.openshift.selector("secrets", secretName).exists()

                        if (!secretExist) {
                            script.println("[INFO] Codebase ${context.codebase.name} haven't dockerhub secret it will not pushed to Dockerhub \n" +
                                    "[INFO] To push codebase to DockerHub add secret ")
                            return
                        }

                        def secretObject = script.openshift.selector("secrets", secretName).object()

                        byte[] usernameByte = secretObject.data.username.decodeBase64()
                        byte[] accessTokenByte = secretObject.data.accesstoken.decodeBase64()
                        byte[] dockerAccountByte = secretObject.data.account.decodeBase64()

                        def dockerAccount = new String(dockerAccountByte)
                        def dockerhubRepo = "${dockerAccount}/" + "${context.codebase.name}"
                        def dockerhubUrl = 'docker.io/' + "${dockerhubRepo}"
                        def accessToken = new String(accessTokenByte)
                        def username = new String(usernameByte)

                        def dockerhubToken = getDockerhubToken (username, accessToken)
                        def imageExist = getDockerhubImageExistanse(dockerhubRepo,dockerhubToken,context.codebase.isTag)

                        if (imageExist == 200) {
                            script.println("[INFO] Image ${dockerhubRepo}:${context.codebase.isTag} already exists in Docker Hub")
                            return
                        }

                        script.println("[INFO] Image ${dockerhubRepo}:${context.codebase.isTag} was not found in Docker Hub, start copying")
                        script.sh """
                                set +x
                                awsv2 ecr get-login-password --region ${awsRegion} | crane auth login ${ecr} -u AWS --password-stdin
                                crane auth login index.docker.io -u ${username} -p ${accessToken}
                                set -ex
                                crane cp ${ecr}/${ecrRepo}/${cbImage}:${context.codebase.isTag} ${dockerhubUrl}:${context.codebase.isTag}
                                """
                    }
                }
            }
        }
    }
}