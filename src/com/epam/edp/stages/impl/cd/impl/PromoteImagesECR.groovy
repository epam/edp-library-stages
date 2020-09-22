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
package com.epam.edp.stages.impl.cd.impl

import com.epam.edp.stages.impl.cd.Stage
import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic
import hudson.FilePath
import org.apache.commons.lang.RandomStringUtils

import com.epam.edp.stages.impl.ci.impl.codebaseiamgestream.CodebaseImageStreams

@Stage(name = "promote-images-ecr")
class PromoteImagesECR {
    Script script

    def setEnvVariable(envList, name, value, overwrite = false) {
        if (envList.find { it.name == name } && overwrite)
            envList.find { it.name == name }.value = value
        else
            envList.add(['name': name, 'value': value])
    }

    def setKanikoTemplate(outputFilePath, buildPodName, resultImageName, dockerRegistryHost, context, codebase) {
        def kanikoFile = new File(outputFilePath)
        def kanikoTemplateFilePath = new FilePath(kanikoFile)

        def kanikoTemplateData = context.platform.getJsonPathValue("cm", "kaniko-template", ".data.kaniko\\.json")
        def parsedKanikoTemplateData = new JsonSlurperClassic().parseText(kanikoTemplateData)
        parsedKanikoTemplateData.metadata.name = buildPodName

        def awsCliInitContainer = parsedKanikoTemplateData.spec.initContainers.find { it.name == "init-repository" }
        if (awsCliInitContainer) {
            setEnvVariable(awsCliInitContainer.env, "REPO_NAME", resultImageName, true)
            setEnvVariable(awsCliInitContainer.env, "AWS_DEFAULT_REGION", getAwsRegion())
        }

        parsedKanikoTemplateData.spec.containers[0].args[0] = "--destination=${dockerRegistryHost}/${resultImageName}:${codebase.version}"
        def jsonData = JsonOutput.toJson(parsedKanikoTemplateData)
        kanikoTemplateFilePath.write(jsonData, null)
        return kanikoTemplateFilePath
    }

    def getAwsRegion() {
        try {
            def response = script.httpRequest timeout: 10, url: 'http://169.254.169.254/latest/dynamic/instance-identity/document'
            def parsedMetadata = new JsonSlurperClassic().parseText(response.content)
            return parsedMetadata.region
        }
        catch (Exception ex) {
            return null
        }
    }

    void run(context) {
        def dockerRegistryHost = context.platform.getJsonPathValue("edpcomponent", "docker-registry", ".spec.url")
        if (!dockerRegistryHost)
            script.error("[JENKINS][ERROR] Couldn't get docker registry server")

        context.job.codebasesList.each() { codebase ->
            if ((codebase.name in context.job.applicationsToPromote) && (codebase.version != "No deploy") && (codebase.version != "noImageExists")) {
                context.workDir = new File("/tmp/${RandomStringUtils.random(10, true, true)}")
                context.workDir.deleteDir()

                def sourceImageName = "${codebase.inputIs}:${codebase.version}"
                def buildconfigName = "promote-${codebase.outputIs}-${script.BUILD_NUMBER}"

                def dockerfileContents = "FROM ${dockerRegistryHost}/${sourceImageName}"
                def dockerfilePath = new FilePath(new File("${context.workDir}/Dockerfile"))
                dockerfilePath.write(dockerfileContents, null)

                script.dir("${context.workDir}") {
                    try {
                        def kanikoTemplateFilePath = setKanikoTemplate("${context.workDir}/kaniko-template.json", buildconfigName, codebase.outputIs, dockerRegistryHost, context, codebase)
                        context.platform.apply(kanikoTemplateFilePath.getRemote())
                        while (!context.platform.getObjectStatus("pod", buildconfigName)["initContainerStatuses"][0].state.keySet().contains("running")) {
                            script.println("[JENKINS][DEBUG] Waiting for init container in Kaniko is started")
                            script.sleep(5)
                        }

                        context.platform.copyToPod("Dockerfile", "/tmp/workspace", buildconfigName, null, "init-kaniko")

                        while (context.platform.getObjectStatus("pod", buildconfigName).phase != "Succeeded") {
                            if (context.platform.getObjectStatus("pod", buildconfigName).phase == "Failed")
                                script.error("[JENKINS][ERROR] Build pod ${buildconfigName} failed")
                            script.println("[JENKINS][DEBUG] Waiting for build ${buildconfigName}")
                            script.sleep(10)
                        }

                        script.println("[JENKINS][DEBUG] Promote ${buildconfigName} for application ${codebase.name} has been completed")

                        new CodebaseImageStreams(context, script)
                                .UpdateOrCreateCodebaseImageStream(codebase.outputIs, "${dockerRegistryHost}/${codebase.outputIs}", codebase.version)
                    }
                    catch (Exception ex) {
                        script.println("[JENKINS][ERROR] Trace: ${ex.getStackTrace().collect { it.toString() }.join('\n')}")
                        script.error("[JENKINS][ERROR] Promoting image for ${codebase.name} failed\r\n Exception - ${ex}")
                    }
                    finally {
                        def podToDelete = "promote-${codebase.outputIs}-${script.BUILD_NUMBER.toInteger() - 1}"
                        context.platform.deleteObject("pod", podToDelete, true)
                    }
                }

                script.println("[JENKINS][INFO] Image ${codebase.inputIs}:${codebase.version} has been promoted to ${codebase.outputIs}")
            }
        }
    }
}