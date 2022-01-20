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
package com.epam.edp.stages.impl.ci.impl.lint

import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage
import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic
import hudson.FilePath

import com.epam.edp.stages.impl.ci.impl.codebaseiamgestream.CodebaseImageStreams

@Stage(name = "dockerbuild-verify", buildTool = "any", type = [ProjectType.APPLICATION])
class BuildDockefileApplicationLibrary {
    Script script

    def setEnvVariable(envList, name, value, overwrite = false) {
        if (envList.find { it.name == name } && overwrite)
            envList.find { it.name == name }.value = value
        else
            envList.add(['name': name, 'value': value])
    }

    def setKanikoTemplate(outputFilePath, buildPodName, resultImageName, dockerRegistryHost, context) {
        def kanikoTemplateFilePath = new FilePath(Jenkins.getInstance().getComputer(script.env['NODE_NAME']).getChannel(), outputFilePath)
        def kanikoTemplateData = context.platform.getJsonPathValue("cm", "kaniko-template", ".data.kaniko\\.json")
        def parsedKanikoTemplateData = new JsonSlurperClassic().parseText(kanikoTemplateData)
        parsedKanikoTemplateData.metadata.name = buildPodName

        def awsCliInitContainer = parsedKanikoTemplateData.spec.initContainers.find { it.name == "init-repository" }
        if (awsCliInitContainer) {
            setEnvVariable(awsCliInitContainer.env, "REPO_NAME", resultImageName, true)
            setEnvVariable(awsCliInitContainer.env, "AWS_DEFAULT_REGION", getAwsRegion())
        }
        parsedKanikoTemplateData.spec.containers[0].args[0] = "--no-push"
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

    def checkBuildPodExist(podName, namespace) {
        def buildPod = script.sh(
            script: "kubectl get pod ${podName} -n ${namespace} --ignore-not-found=true",
            returnStdout: true).trim()
        if (buildPod == '') {
            return false
        }
        return true
    }

    void run(context) {
        context.codebase.deployableModuleDir = "${context.workDir}"
        def dockerfilePath = new FilePath(Jenkins.getInstance().getComputer(script.env['NODE_NAME']).getChannel(),
                "${context.workDir}/Dockerfile")
        if (!dockerfilePath.exists()) {
            script.error("[JENKINS][ERROR] There is no Dockerfile in the root of the repository, we are not able to perform build image")
        }

        def resultImageName = "${context.codebase.name}-${context.git.branch.replaceAll("[^\\p{L}\\p{Nd}]+", "-")}"
        def buildconfigName = "lint-${resultImageName}-${script.BUILD_NUMBER}"
        if (checkBuildPodExist(buildconfigName, context.job.ciProject)) {
            script.println("[JENKINS][DEBUG] Pod with the name ${buildconfigName} already exists. It will be removed.")
            context.platform.deleteObject("pod", buildconfigName, true)
        }
        script.dir("${context.workDir}") {
            try {
                def dockerRegistryHost = context.platform.getJsonPathValue("edpcomponent", "docker-registry", ".spec.url")
                if (!dockerRegistryHost)
                    script.error("[JENKINS][ERROR] Couldn't get docker registry server")

                def kanikoTemplateFilePath = setKanikoTemplate("${context.workDir}/kaniko-template.json", buildconfigName,
                        "${context.job.ciProject}/${context.codebase.name}", dockerRegistryHost, context)
                context.platform.apply(kanikoTemplateFilePath.getRemote())
                while (!context.platform.getObjectStatus("pod", buildconfigName)["initContainerStatuses"][0].state.keySet().contains("running")) {
                    script.println("[JENKINS][DEBUG] Waiting for the Kaniko init container to be started")
                    script.sleep(5)
                }

                def deployableModuleDirFilepath = new FilePath(Jenkins.getInstance().getComputer(script.env['NODE_NAME']).getChannel(), "${context.codebase.deployableModuleDir}")
                script.println("[JENKINS][DEBUG] Files to copy to kaniko - ${deployableModuleDirFilepath.list()}")
                deployableModuleDirFilepath.list().each() { item ->
                    if (item.getName() != "Dockerfile")
                        context.platform.copyToPod("${context.codebase.deployableModuleDir}/${item.getName()}", "/tmp/workspace/", buildconfigName, null, "init-kaniko")
                }
                context.platform.copyToPod("Dockerfile", "/tmp/workspace", buildconfigName, null, "init-kaniko")

                while (context.platform.getObjectStatus("pod", buildconfigName).phase != "Succeeded") {
                    if (context.platform.getObjectStatus("pod", buildconfigName).phase == "Failed")
                        script.error("[JENKINS][ERROR] Build pod ${buildconfigName} failed")
                    script.println("[JENKINS][DEBUG] Waiting for build ${buildconfigName}")
                    script.sleep(10)
                }
                script.println("[JENKINS][DEBUG] Build config ${buildconfigName} for application ${context.codebase.name} has been completed")
            }
            catch (Exception ex) {
                script.error("[JENKINS][ERROR] Building image for ${context.codebase.name} failed")
            }
            finally {
                def podToDelete = "lint-${context.codebase.name}-${context.git.branch.replaceAll("[^\\p{L}\\p{Nd}]+", "-")}-${script.BUILD_NUMBER.toInteger() - 1}"
                context.platform.deleteObject("pod", podToDelete, true)
            }
        }
    }
}