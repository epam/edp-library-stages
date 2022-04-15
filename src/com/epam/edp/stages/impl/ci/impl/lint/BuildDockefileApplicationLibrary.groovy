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
package com.epam.edp.stages.impl.ci.impl.lint

import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage
import hudson.FilePath
import org.yaml.snakeyaml.Yaml

import com.epam.edp.stages.impl.ci.impl.codebaseiamgestream.CodebaseImageStreams

@Stage(name = "dockerbuild-verify", buildTool = "any", type = [ProjectType.APPLICATION, ProjectType.LIBRARY])
class BuildDockefileApplicationLibrary {
    Script script

    def setEnvVariable(envList, name, value, overwrite = false) {
        if (envList.find { it.name == name } && overwrite)
            envList.find { it.name == name }.value = value.toString()
        else
            envList.add(['name': name, 'value': value.toString()])
    }

    def setKanikoTemplate(outputFilePath, buildPodName, resultImageName, dockerRegistryHost, context) {
        def kanikoTemplateFilePath = new FilePath(Jenkins.getInstance().getComputer(script.env['NODE_NAME']).getChannel(), outputFilePath)
        def kanikoTemplateYaml = context.platform.getJsonPathValue("cm", "kaniko-template", ".data.kaniko\\.yaml")
        def awsRegion = context.platform.getJsonPathValue("cm", "edp-config", ".data.aws_region")
        def parsedKanikoTemplateYaml= new Yaml().load(kanikoTemplateYaml)
        parsedKanikoTemplateYaml.metadata.name = buildPodName.toString()

        def awsCliInitContainer = parsedKanikoTemplateYaml.spec.initContainers.find { it.name == "init-repository" }
        if (awsCliInitContainer) {
            setEnvVariable(awsCliInitContainer.env, "REPO_NAME", resultImageName, true)
            setEnvVariable(awsCliInitContainer.env, "AWS_DEFAULT_REGION", awsRegion)
        }
        parsedKanikoTemplateYaml.spec.containers[0].args[0] = "--no-push".toString()
        def yamlData = new Yaml().dump(parsedKanikoTemplateYaml)
        kanikoTemplateFilePath.write(yamlData, null)
        return kanikoTemplateFilePath
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
        def dockerfilePath = new FilePath(Jenkins.getInstance().getComputer(script.env['NODE_NAME']).getChannel(),
                "${context.workDir}/Dockerfile")
        if (!dockerfilePath.exists()) {
            script.error("[JENKINS][ERROR] There is no Dockerfile in the root of the repository, we are not able to perform build image")
        }

        def resultImageName = "${context.codebase.name}-${context.git.branch.replaceAll("[^\\p{L}\\p{Nd}]+", "-")}"
        def buildconfigName = "lint-${resultImageName}-${script.BUILD_NUMBER}"
        if (checkBuildPodExist(buildconfigName, context.job.ciProject)) {
            script.println("[JENKINS][DEBUG] Pod with name ${buildconfigName} already exists. It will be removed.")
            context.platform.deleteObject("pod", buildconfigName, true)
        }
        script.dir("${context.workDir}") {
            try {
                def dockerRegistryHost = context.platform.getJsonPathValue("edpcomponent", "docker-registry", ".spec.url")
                if (!dockerRegistryHost)
                    script.error("[JENKINS][ERROR] Couldn't get docker registry server")

                def kanikoTemplateFilePath = setKanikoTemplate("${context.workDir}/kaniko-template.yaml", buildconfigName,
                        "${context.job.ciProject}/${context.codebase.name}", dockerRegistryHost, context)
                context.platform.apply(kanikoTemplateFilePath.getRemote())

                while (context.platform.getObjectStatus("pod", buildconfigName)["conditions"].find{it.type == 'PodScheduled'}.status != "True") {
                    script.println("[JENKINS][DEBUG] Waiting for the Kaniko pod to be scheduled")
                    script.sleep(5)
                }

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
                if(context.platform.getObjectStatus("pod", buildconfigName).phase == "Pending"){
                    context.platform.deleteObject("pod", buildconfigName, true)
                }

                def podToDelete = "lint-${resultImageName}-${script.BUILD_NUMBER.toInteger() - 1}"
                context.platform.deleteObject("pod", podToDelete, true)
            }
        }
    }
}