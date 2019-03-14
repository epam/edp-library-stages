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
import org.apache.commons.lang.RandomStringUtils


import com.epam.edp.stages.impl.cd.Stage

@Stage(name = "deploy")
class Deploy {
    Script script

    def getBuildUserFromLog(context) {
        def jenkinsCred = "admin:${context.jenkins.token}".bytes.encodeBase64().toString()
        def jobUrl = "${context.job.buildUrl}".replaceFirst("${context.job.jenkinsUrl}", '')
        def response = script.httpRequest url: "http://jenkins.${context.job.edpName}-edp-cicd:8080/${jobUrl}consoleText",
                httpMode: 'GET',
                customHeaders: [[name: 'Authorization', value: "Basic ${jenkinsCred}"]]
        return script.sh(
                script: "#!/bin/sh -e\necho \"${response.content}\" | grep \"Approved by\" -m 1 | awk {'print \$3'}",
                returnStdout: true
        ).trim()
    }

    def checkOpenshiftTemplateExists(templateName) {
        if (!script.openshift.selector("template", templateName).exists()) {
            script.println("[JENKINS][WARNING] Template which called ${templateName} doesn't exist in ${vars.projectPrefix}-edp-cicd namespace")
            return false
        }
        return true
    }

    def checkDeployment(context, object, type) {
        script.println("[JENKINS][DEBUG] Validate deployment - ${object.name} in ${context.job.deployProject}")
        try {
            script.openshiftVerifyDeployment apiURL: '', authToken: '', depCfg: "${object.name}",
                    namespace: "${context.job.deployProject}", verbose: 'false',
                    verifyReplicaCount: 'true', waitTime: '600', waitUnit: 'sec'
            if (type == 'application' && getDeploymentVersion(context,object) != object.currentDeploymentVersion) {
                script.println("[JENKINS][DEBUG] Deployment ${object.name} in project ${context.job.deployProject} has been rolled out")
                context.environment.updatedApplicaions.push(object)
            } else
                script.println("[JENKINS][DEBUG] New version of application ${object.name} hasn't been deployed, because the save version")
        }
        catch (Exception verifyDeploymentException) {
            if (type == "application" && object.currentDeploymentVersion != 0) {
                script.println("[JENKINS][WARNING] Rolling out of ${object.name} with version ${object.version} has been failed.\r\n" +
                        "[JENKINS][WARNING] Rolling back to the previous version")
                script.sh("oc -n ${context.job.deployProject} rollout undo dc ${object.name}")
                script.openshiftVerifyDeployment apiURL: '', authToken: '', depCfg: "${object.name}",
                        namespace: "${context.job.deployProject}", verbose: 'false',
                        verifyReplicaCount: 'true', waitTime: '600', waitUnit: 'sec'
                script.println("[JENKINS][WARNING] Rolling out of ${object.name} with version ${object.version} has been failed.")
            } else
                script.println("[JENKINS][WARNING] ${object.name} deploy has been failed. Reason - ${verifyDeploymentException}")
        }

    }

    def getDeploymentVersion(context, application) {
        def deploymentExists = script.sh(
                script: "oc -n ${context.job.deployProject} get dc ${application.name} --no-headers | awk '{print \$1}'",
                returnStdout: true
        ).trim()
        if (deploymentExists == "") {
            script.println("[JENKINS][WARNING] Deployment config ${application.name} doesn't exist in the project ${context.job.deployProject}\r\n" +
                    "[JENKINS][WARNING] We will roll it out")
            return null
        }
        def version = script.sh(
                script: "oc -n ${context.job.deployProject} get dc ${application.name} -o jsonpath=\'{.status.latestVersion}\'",
                returnStdout: true
        ).trim().toInteger()
        return (version)
    }

    def checkImageExists(context, object) {
        def imageExists = script.sh(
                script: "oc -n ${context.job.metaProject} get is ${object.name} --no-headers | awk '{print \$1}'",
                returnStdout: true
        ).trim()
        if (imageExists == "") {
            script.println("[JENKINS][WARNING] Image stream ${object.name} doesn't exist in the project ${context.job.metaProject}\r\n" +
                    "[JENKINS][WARNING] Deploy will be skipped")
            return false
        }

        def tagExist = script.sh(
                script: "oc -n ${context.job.metaProject} get is ${object.name} -o jsonpath='{.spec.tags[?(@.name==\"${object.version}\")].name}'",
                returnStdout: true
        ).trim()
        if (tagExist == "") {
            script.println("[JENKINS][WARNING] Image stream ${object.name} with tag ${object.version} doesn't exist in the project ${context.job.metaProject}\r\n" +
                    "[JENKINS][WARNING] Deploy will be skipped")
            return false
        }
        return true
    }

    def getNumericVersion(context, application) {
        def hash = script.sh(
                script: "oc -n ${context.job.metaProject} get is ${application.name} -o jsonpath=\'{@.spec.tags[?(@.name==\"${application.version}\")].from.name}\'",
                returnStdout: true
        ).trim()
        def tags = script.sh(
                script: "oc -n ${context.job.metaProject} get is ${application.name} -o jsonpath=\'{@.spec.tags[?(@.from.name==\"${hash}\")].name}\'",
                returnStdout: true
        ).trim().tokenize()
        tags.removeAll { it == "latest" }
        tags.removeAll { it == "stable" }
        script.println("[JENKINS][DEBUG] Application ${application.name} has the following numeric tag, which corresponds to tag ${application.version} - ${tags}")
        switch (tags.size()) {
            case 0:
                script.println("[JENKINS][WARNING] Application ${application.name} has no numeric version for tag ${application.version}\r\n" +
                        "[JENKINS][WARNING] Deploy will be skipped")
                return null
                break
            case 1:
                return (tags[0])
                break
            default:
                script.println("[JENKINS][WARNING] Application ${application.name} has more than one numeric tag for tag ${application.version}\r\n" +
                        "[JENKINS][WARNING] We will use the first one")
                return (tags[0])
                break
        }
    }

    def cloneProject(context, application) {
        def gitApplicationUrl = "ssh://${context.gerrit.autouser}@${context.gerrit.host}:${context.gerrit.sshPort}/${application.name}"

        script.checkout([$class                           : 'GitSCM', branches: [[name: "refs/tags/${application.version}"]],
                  doGenerateSubmoduleConfigurations: false, extensions: [],
                  submoduleCfg                     : [],
                  userRemoteConfigs                : [[credentialsId: "${context.gerrit.credentialsId}",
                                                       refspec      : "refs/tags/${application.version}",
                                                       url          : "${gitApplicationUrl}"]]])
        script.println("[JENKINS][DEBUG] Project ${application.name} has been successfully cloned")
    }

    def deployConfigMapTemplate(context, application, deployTemplatesPath) {
        def templateName = application.name + '-deploy-config-' + context.job.stageWithoutPrefixName
        if (!checkTemplateExists(templateName, deployTemplatesPath))
            return

        script.sh("oc -n ${context.job.deployProject} process -f ${deployTemplatesPath}/${templateName}.yaml " +
                "--local=true -o json | oc -n ${context.job.deployProject} apply -f -")
        script.println("[JENKINS][DEBUG] Config map with name ${templateName}.yaml for application ${application.name} has been deployed")
    }

    def deployApplicationTemplate(context, application, deployTemplatesPath) {
        application.currentDeploymentVersion = getDeploymentVersion(context, application)
        def templateName = "${application.name}-install-${context.job.stageWithoutPrefixName}"

        if (application.need_database)
            script.sh("oc adm policy add-scc-to-user anyuid -z ${application.name} -n ${context.job.deployProject}")

        if (!checkTemplateExists(templateName, deployTemplatesPath)) {
            script.println("[JENKSIN][INFO] Trying to find out default template ${application.name}.yaml")
            templateName = application.name
            if (!checkTemplateExists(templateName, deployTemplatesPath))
                return
        }
        script.sh("oc -n ${context.job.deployProject} process -f ${deployTemplatesPath}/${templateName}.yaml " +
                "-p IMAGE_NAME=${context.job.metaProject}/${application.name} " +
                "-p APP_VERSION=${application.version} " +
                "-p NAMESPACE=${context.job.deployProject} " +
                "--local=true -o json | oc -n ${context.job.deployProject} apply -f -")

        checkDeployment(context, application, 'application')
    }

    def checkTemplateExists(templateName, deployTemplatesPath) {
        def templateYamlFile = new File("${deployTemplatesPath}/${templateName}.yaml")
        if (!templateYamlFile.exists()) {
            script.println("[JENKINS][WARNING] Template file which called ${templateName}.yaml doesn't exist in ${deployTemplatesPath} in the repository")
            return false
        }
        return true
    }

    void run(context) {
        script.openshift.withCluster() {
            if (!script.openshift.selector("project", context.job.deployProject).exists()) {
                script.openshift.newProject(context.job.deployProject)
                def groupList = ["${context.job.edpName}-edp-super-admin", "${context.job.edpName}-edp-admin"]
                groupList.each() { group ->
                    script.sh("oc adm policy add-role-to-group admin ${group} -n ${context.job.deployProject}")
                }
                script.sh("oc adm policy add-role-to-group view ${context.job.edpName}-edp-view -n ${context.job.deployProject}")
            }

            if (context.job.buildUser == null || context.job.buildUser == "")
                context.job.buildUser = getBuildUserFromLog(context)

            if (context.job.buildUser != null && context.job.buildUser != "") {
                script.sh("oc adm policy add-role-to-user admin ${context.job.buildUser} -n ${context.job.deployProject}")
            }

            context.job.servicesList.each() { service ->
                if (!checkOpenshiftTemplateExists(service.name))
                    return

                script.sh("oc adm policy add-scc-to-user anyuid -z ${service.name} -n ${context.job.deployProject}")
                script.sh("oc -n ${context.job.edpName}-edp-cicd process ${service.name} " +
                        "-p SERVICE_IMAGE=${service.image} " +
                        "-p SERVICE_VERSION=${service.version} " +
                        "-o json | oc -n ${context.job.deployProject} apply -f -")
                checkDeployment(context, service, 'service')
            }

            context.job.applicationsList.each() { application ->
                if (!checkImageExists(context, application))
                    return

                if (application.version =~ "stable|latest") {
                    application.version = getNumericVersion(context, application)
                    if (!application.version)
                        return
                }

                script.sh("oc adm policy add-role-to-user view system:serviceaccount:${context.job.deployProject}:${application.name} -n ${context.job.deployProject}")
                def appDir = "${script.WORKSPACE}/${RandomStringUtils.random(10, true, true)}/${application.name}"
                def deployTemplatesPath = "${appDir}/${context.job.deployTemplatesDirectory}"
                script.dir("${appDir}") {
                    cloneProject(context, application)
                    deployConfigMapTemplate(context, application, deployTemplatesPath)
                    try {
                        deployApplicationTemplate(context, application, deployTemplatesPath)
                    }
                    catch (Exception ex) {
                        script.println("[JENKINS][WARNING] Deployment of application ${application.name} has been failed. Reason - ${ex}.")
                        script.currentBuild.result = 'UNSTABLE'
                    }
                }
            }
            script.println("[JENKINS][DEBUG] Applications that have been updated - ${context.environment.updatedApplicaions}")
        }
    }
}

