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

package com.epam.edp.stages.impl.cd.impl

import com.epam.edp.stages.impl.cd.Stage
import org.apache.commons.lang.RandomStringUtils
import groovy.json.JsonSlurperClassic

@Stage(name = "deploy")
class Deploy {
    Script script

    def getBuildUserFromLog(context) {
        def jenkinsCred = "admin:${context.jenkins.token}".bytes.encodeBase64().toString()
        def jobUrl = "${context.job.buildUrl}".replaceFirst("${context.job.jenkinsUrl}", '')
        def response = script.httpRequest url: "http://jenkins.${context.job.ciProject}:8080/${jobUrl}consoleText",
                httpMode: 'GET',
                customHeaders: [[name: 'Authorization', value: "Basic ${jenkinsCred}"]]
        return script.sh(
                script: "#!/bin/sh -e\necho \"${response.content}\" | grep \"Approved by\" -m 1 | awk {'print \$3'}",
                returnStdout: true
        ).trim()
    }

    def getRefspec(codebase) {
        return codebase.versioningType == "edp" ?
                "refs/tags/build/${codebase.version}" :
                "refs/tags/${codebase.version}"
    }

    def cloneProject(context, codebase) {
        script.println("[JENKINS][DEBUG] Start fetching Git Server info for ${codebase.name} from ${codebase.gitServer} CR")
        def gitServerName = "gitservers.${context.job.crApiVersion}.edp.epam.com"

        script.println("[JENKINS][DEBUG] Git Server CR Version: ${context.job.crApiVersion}")
        script.println("[JENKINS][DEBUG] Git Server Name: ${gitServerName}")

        def autouser = context.platform.getJsonPathValue(gitServerName, codebase.gitServer, ".spec.gitUser")
        def host = context.platform.getJsonPathValue(gitServerName, codebase.gitServer, ".spec.gitHost")
        def sshPort = context.platform.getJsonPathValue(gitServerName, codebase.gitServer, ".spec.sshPort")
        def credentialsId = context.platform.getJsonPathValue(gitServerName, codebase.gitServer, ".spec.nameSshKeySecret")

        script.println("[JENKINS][DEBUG] autouser: ${autouser}")
        script.println("[JENKINS][DEBUG] host: ${host}")
        script.println("[JENKINS][DEBUG] sshPort: ${sshPort}")
        script.println("[JENKINS][DEBUG] credentialsId: ${credentialsId}")

        script.println("[JENKINS][DEBUG] Repository path: ${codebase.gitProjectPath}")

        def gitCodebaseUrl = "ssh://${autouser}@${host}:${sshPort}${codebase.gitProjectPath}"
        def refspec = getRefspec(codebase)

        try {
            script.checkout([$class                           : 'GitSCM', branches: [[name: "${refspec}"]],
                             doGenerateSubmoduleConfigurations: false, extensions: [],
                             submoduleCfg                     : [],
                             userRemoteConfigs                : [[credentialsId: "${credentialsId}",
                                                                  refspec      : "${refspec}",
                                                                  url          : "${gitCodebaseUrl}"]]])
        }
        catch (Exception ex) {
            script.unstable("[JENKINS][WARNING] Project ${codebase.name} cloning has failed with ${ex}\r\n" +
                    "[JENKINS][WARNING] Deploy will be skipped\r\n" +
                    "[JENKINS][WARNING] Check if tag ${refspec} exists in repository")
            script.currentBuild.setResult('UNSTABLE')
            script.currentBuild.description = "${script.currentBuild.description}\r\n${codebase.name} deploy failed"
            return false
        }
        script.println("[JENKINS][DEBUG] Project ${codebase.name} has been successfully cloned")
        return true
    }

    def deployCodebaseHelmTemplate(context, codebase, deployTemplatesPath) {

        def fullImageName = context.platform.createFullImageName(context.environment.config.dockerRegistryHost,
                context.job.ciProject, codebase.name)
        def parametersMap = [
                ['name': 'image.repository', 'value': fullImageName],
                ['name': 'image.tag', 'value': "${codebase.version.replaceAll("/", "-")}"],
        ]

        context.platform.deployCodebaseHelm(
                context.job.deployProject,
                "${deployTemplatesPath}",
                codebase,
                fullImageName,
                context.job.deployTimeout,
                parametersMap
        )
        setAnnotationToStageCR(context, codebase.name, codebase.version, context.job.ciProject)
    }

    def setAnnotationToStageCR(context, codebase, tag, namespace) {
        def annotationPrefix = "app.edp.epam.com/"
        def stageName = "${context.job.pipelineName}-${context.job.stageName}"
        script.sh("kubectl annotate --overwrite stages.v2.edp.epam.com ${stageName} -n ${namespace} ${annotationPrefix}${codebase}=${tag}")
        script.println("[JENKINS][DEBUG] Annotation has been added to the ${stageName} stage")
    }

    def getApplicationFromStageCR(context) {
        def stageData = script.sh(
            script: "kubectl get stages.v2.edp.epam.com ${context.job.pipelineName}-${context.job.stageName} -n ${context.job.ciProject} --output=json",
            returnStdout: true).trim()
        def stageJsonData = new JsonSlurperClassic().parseText(stageData)
        def deployedVersions = stageJsonData.metadata.annotations
        return deployedVersions
    }

    def setAnnotationToJenkins(deployedVersions) {
        def summary = script.manager.createSummary("notepad.png")
        summary.appendText("Deployed versions:", false)
        deployedVersions.each { version ->
            if (version =~ /^app.edp.epam.com.*/) {
                def normalizedVersion = script.sh(script: "cut -d '/' -f2- <<< ${version} | tr '=' ':'", returnStdout: true).trim()
                summary.appendText("<li>${normalizedVersion}</li>", false)
            }
        }
        script.println("[JENKINS][DEBUG] Annotation has been added to this job description")
    }

    def createJenkinsArtifacts(deployedVersions, artifactName) {
        script.dir("${script.WORKSPACE}/artifacts") {
            deployedVersions.each { version ->
                if (version =~ /^app.edp.epam.com.*/) {
                    script.sh("cut -d '/' -f2- <<< ${version} | tr '=' ':' >> ${artifactName}")
                }
            }
        }
    }

    def getDockerRegistryInfo(context) {
        try {
            return context.platform.getJsonPathValue("edpcomponents", "docker-registry", ".spec.url")
        }
        catch (Exception ex) {
            script.println("[JENKINS][WARNING] Getting docker registry info failed. Reason:\r\n ${ex}")
            return null
        }
    }

    def deployCodebase(version, name, context, codebase) {
        def codebaseDir = "${script.WORKSPACE}/${RandomStringUtils.random(10, true, true)}/${name}"
        def deployTemplatesPath = "${codebaseDir}/${context.job.deployTemplatesDirectory}"
        script.dir("${codebaseDir}") {
            if (!cloneProject(context, codebase)) {
                if (codebase.name in context.job.applicationsToPromote)
                    context.job.applicationsToPromote.remove(codebase.name)
                return
            }
            try {
                deployCodebaseHelmTemplate(context, codebase, deployTemplatesPath)
                }

            catch (Exception ex) {
                script.unstable("[JENKINS][WARNING] Deployment of codebase ${name} has failed. Reason - ${ex}.")
                script.currentBuild.setResult('UNSTABLE')
                if (codebase.name in context.job.applicationsToPromote) {
                    context.job.applicationsToPromote.remove(codebase.name)
                }
                script.deleteDir()
            }
        }
        script.dir("${codebaseDir}") {
            script.deleteDir()
        }
    }

    def getNElements(entities, max_apps) {
        def tempEntityList = entities.stream()
                .limit(max_apps.toInteger())
                .collect()
        entities.removeAll(tempEntityList)

        return tempEntityList
    }

    void run(context) {

        def prevDeployedVersions = getApplicationFromStageCR(context)
        script.sh("rm -f ${script.WORKSPACE}/artifacts/*")
        createJenkinsArtifacts(prevDeployedVersions, "prev_versions.txt")

        if (context.job.buildUser == null || context.job.buildUser == "")
            context.job.buildUser = getBuildUserFromLog(context)

        if (context.job.buildUser != null && context.job.buildUser != "") {
            context.platform.createRoleBinding(context.job.buildUser, "admin", context.job.deployProject)
        }

        def deployCodebasesList = context.job.codebasesList.clone()
        while (!deployCodebasesList.isEmpty()) {
            def parallelCodebases = [:]
            def tempAppList = getNElements(deployCodebasesList, context.job.maxOfParallelDeployApps)
            tempAppList.each() { codebase ->
                if ((codebase.version == "No deploy") || (codebase.version == "noImageExists")) {
                    script.println("[JENKINS][WARNING] Application ${codebase.name} deploy skipped")
                    return
                }

                if (codebase.version == "latest") {
                    codebase.version = codebase.latest
                    script.println("[JENKINS][DEBUG] Latest tag equals to ${codebase.latest} version")
                    if (!codebase.version)
                        return
                }

                if (codebase.version == "stable") {
                    codebase.version = codebase.stable
                    script.println("[JENKINS][DEBUG] Stable tag equals to ${codebase.stable} version")
                    if (!codebase.version)
                        return
                }

                context.platform.addSccToUser(codebase.name, 'anyuid', context.job.deployProject)
                context.platform.createRoleBinding("system:serviceaccount:${context.job.deployProject}", "view", context.job.deployProject)

                context.environment.config.dockerRegistryHost = getDockerRegistryInfo(context)
                parallelCodebases["${codebase.name}"] = {
                    deployCodebase(codebase.version, codebase.name, context, codebase)
                }
            }
            script.parallel parallelCodebases
        }
        def currDeployedVersions = getApplicationFromStageCR(context)
        createJenkinsArtifacts(currDeployedVersions, "curr_versions.txt")
        script.sh("diff -u ${script.WORKSPACE}/artifacts/prev_versions.txt ${script.WORKSPACE}/artifacts/curr_versions.txt > ${script.WORKSPACE}/artifacts/diff_versions.txt || true")
        try{
            script.archiveArtifacts artifacts: "artifacts/*.txt", onlyIfSuccessful: true
        }catch(Exception e){
            script.println("[JENKINS][DEBUG] Jenkins artifacts not found")
            return 0;
        }
        setAnnotationToJenkins(currDeployedVersions)
    }
}