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
package com.epam.edp.stages.impl.ci.impl.buildimagekaniko

import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage
import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic
import hudson.FilePath

import java.text.DateFormat
import java.text.SimpleDateFormat

@Stage(name = "build-image-kaniko", buildTool = ["maven", "npm", "gradle", "dotnet"], type = [ProjectType.APPLICATION])
class BuildImageKaniko {
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
        if (context.codebase.config.versioningType == "edp") {
            parsedKanikoTemplateData.spec.containers[0].args[0] = "--destination=${dockerRegistryHost}/${resultImageName}:${context.codebase.buildVersion}"
        } else {
            parsedKanikoTemplateData.spec.containers[0].args[0] = "--destination=${dockerRegistryHost}/${resultImageName}:${context.git.branch}-${context.codebase.buildVersion}"
          }
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

    def setCodebaseImageStreamTemplate(outputFilePath, cbisName, fullImageName, context) {
        def cbisTemplateFilePath = new FilePath(Jenkins.getInstance().getComputer(script.env['NODE_NAME']).getChannel(), outputFilePath)
        def cbisTemplateData = context.platform.getJsonPathValue("cm", "cbis-template", ".data.cbis\\.json")
        def parsedCbisTemplateData = new JsonSlurperClassic().parseText(cbisTemplateData)
        parsedCbisTemplateData.metadata.name = cbisName
        parsedCbisTemplateData.spec.imageName = fullImageName

        def jsonData = JsonOutput.toJson(parsedCbisTemplateData)
        cbisTemplateFilePath.write(jsonData, null)
        return cbisTemplateFilePath
    }

    def updateCodebaseimagestreams(cbisName, repositoryName, imageTag, context) {
        def crApiGroup = "${context.job.getParameterValue("GIT_SERVER_CR_VERSION")}.edp.epam.com"
        if (!context.platform.checkObjectExists("cbis.${crApiGroup}", cbisName)) {
            script.println("[JENKINS][DEBUG] CodebaseImagestream not found. Creating new CodebaseImagestream")
            def cbisTemplateFilePath = setCodebaseImageStreamTemplate("${context.workDir}/cbis-template.json", cbisName, repositoryName, context)
            context.platform.apply(cbisTemplateFilePath.getRemote())
        }
        def cbisResource = context.platform.getJsonValue("cbis.${crApiGroup}", cbisName)
        def parsedCbisResource = new JsonSlurperClassic().parseText(cbisResource)
        def cbisTags = parsedCbisResource.spec.tags ? parsedCbisResource.spec.tags : []

        if (!cbisTags.find { it.name == imageTag }) {
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
            cbisTags.add(['name': imageTag, 'created': dateFormat.format(new Date())])
            def newCbisTags = JsonOutput.toJson(cbisTags)
            script.sh("kubectl patch --type=merge cbis.${crApiGroup} ${cbisName} -p '{\"spec\":{\"tags\":${newCbisTags}}}'")
        }
    }

    void run(context) {
        def dockerfilePath = new FilePath(Jenkins.getInstance().getComputer(script.env['NODE_NAME']).getChannel(),
                "${context.workDir}/Dockerfile")
        if (!dockerfilePath.exists()) {
            script.error("[JENKINS][ERROR] There is no Dockerfile in the root of the repository, we are not able to perform build image")
        }

        def resultImageName = "${context.codebase.name}-${context.git.branch.replaceAll("[^\\p{L}\\p{Nd}]+", "-")}"
        def buildconfigName = "build-${resultImageName}-${script.BUILD_NUMBER}"
        script.dir("${context.workDir}") {
            try {
                def dockerRegistryHost = context.platform.getJsonPathValue("edpcomponent", "docker-registry", ".spec.url")
                if (!dockerRegistryHost)
                    script.error("[JENKINS][ERROR] Couldn't get docker registry server")

                def kanikoTemplateFilePath = setKanikoTemplate("${context.workDir}/kaniko-template.json", buildconfigName, resultImageName, dockerRegistryHost, context)
                context.platform.apply(kanikoTemplateFilePath.getRemote())
                while (!context.platform.getObjectStatus("pod", buildconfigName)["initContainerStatuses"][0].state.keySet().contains("running")) {
                    script.println("[JENKINS][DEBUG] Waiting for init container in Kaniko is started")
                    script.sleep(5)
                }

                def deployableModuleDirFilepath = new FilePath(Jenkins.getInstance().getComputer(script.env['NODE_NAME']).getChannel(), context.codebase.deployableModuleDir)
                script.println(context.codebase.deployableModuleDir)
                script.println(deployableModuleDirFilepath)
                script.println(deployableModuleDirFilepath.list())
                deployableModuleDirFilepath.list().each() { item ->
                    if (item.getName() != "Dockerfile") {
                        script.println(item.getName())
                        context.platform.copyToPod("${context.codebase.deployableModuleDir}/${item.getName()}", "/tmp/workspace/", buildconfigName, null, "init-kaniko")
                    }
                }
                context.platform.copyToPod("Dockerfile", "/tmp/workspace", buildconfigName, null, "init-kaniko")

                while (context.platform.getObjectStatus("pod", buildconfigName).phase != "Succeeded") {
                    if (context.platform.getObjectStatus("pod", buildconfigName).phase == "Failed")
                        script.error("[JENKINS][ERROR] Build pod ${buildconfigName} failed")
                    script.println("[JENKINS][DEBUG] Waiting for build ${buildconfigName}")
                    script.sleep(10)
                }

                script.println("[JENKINS][DEBUG] Build config ${buildconfigName} for application ${context.codebase.name} has been completed")

                updateCodebaseimagestreams(resultImageName, "${dockerRegistryHost}/${resultImageName}",
                        "${context.git.branch}-${context.codebase.buildVersion}", context)
            }
            catch (Exception ex) {
                script.error("[JENKINS][ERROR] Building image for ${context.codebase.name} failed")
            }
            finally {
                def podToDelete = "build-${context.codebase.name}-${context.git.branch.replaceAll("[^\\p{L}\\p{Nd}]+", "-")}-${script.BUILD_NUMBER.toInteger() - 1}"
                context.platform.deleteObject("pod", podToDelete, true)
            }
        }
    }
}