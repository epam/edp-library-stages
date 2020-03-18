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
import groovy.json.JsonSlurperClassic
import hudson.FilePath
import com.epam.edp.buildtool.BuildToolFactory

@Stage(name = "autotests")
class AutomationTests {
    Script script

    def generateSshLink(context, qualityGate) {
        def commonSshLinkPart = "ssh://${context.job.autouser}@${context.job.host}:${context.job.sshPort}"
        return qualityGate.autotest.gitProjectPath?.trim() ?
                "${commonSshLinkPart}${qualityGate.autotest.gitProjectPath}" :
                "${commonSshLinkPart}/${qualityGate.autotest.name}"
    }

    void run(context) {
        def qualityGate = context.job.qualityGates.find{it.stepName == context.stepName}
        script.println("[JENKINS][DEBUG] Quality gate content - ${qualityGate}")

        script.node(qualityGate.autotest.build_tool.toLowerCase()) {
            context.buildTool = new BuildToolFactory().getBuildToolImpl(qualityGate.autotest.build_tool, script, context.nexus)
            context.buildTool.init()
            context.job.setGitServerDataToJobContext(qualityGate.autotest.gitServer)

            def codebaseDir = "${script.WORKSPACE}/${RandomStringUtils.random(10, true, true)}/${qualityGate.autotest.name}"
            script.dir("${codebaseDir}") {
                def gitCodebaseUrl = generateSshLink(context, qualityGate)

                script.checkout([$class                           : 'GitSCM', branches: [[name: "${qualityGate.codebaseBranch.branchName}"]],
                                 doGenerateSubmoduleConfigurations: false, extensions: [],
                                 submoduleCfg                     : [],
                                 userRemoteConfigs                : [[credentialsId: "${context.job.credentialsId}",
                                                                      url          : "${gitCodebaseUrl}"]]])

                if (!script.fileExists("${codebaseDir}/run.json"))
                    script.error "[JENKINS][ERROR] There is no run.json file in the project ${qualityGate.autotest.name}. " +
                            "Can't define command to run autotests"

                def runCommandFile = ""
                if (script.env['NODE_NAME'].equals("master")) {
                    def jsonFile = new File("${codebaseDir}/run.json")
                    runCommandFile = new FilePath(jsonFile).readToString()
                } else {
                    runCommandFile = new FilePath(
                            Jenkins.getInstance().getComputer(script.env['NODE_NAME']).getChannel(),
                            "${codebaseDir}/run.json").readToString()
                }

                def parsedRunCommandJson = new JsonSlurperClassic().parseText(runCommandFile)

                if (!(context.job.stageName in parsedRunCommandJson.keySet()))
                    script.error "[JENKINS][ERROR] Haven't found ${context.job.stageName} command in file run.json. " +
                            "It's mandatory to be specified, please check"

                def runCommand = parsedRunCommandJson["${context.job.stageName}"]
                try {
                    script.withCredentials([script.usernamePassword(credentialsId: "${context.nexus.credentialsId}",
                            passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
                        script.sh "${runCommand} ${context.buildTool.properties} -Dartifactory.username=${script.USERNAME} -Dartifactory.password=${script.PASSWORD} " +
                            "-B --settings ${context.buildTool.settings}"
                    }
                }
                catch (Exception ex) {
                    script.error "[JENKINS][ERROR] Tests from ${qualityGate.autotest.name} have been failed. Reason - ${ex}"
                }
                finally {
                    switch ("${qualityGate.autotest.testReportFramework}") {
                        case "allure":
                            script.allure([
                                    includeProperties: false,
                                    jdk              : '',
                                    properties       : [],
                                    reportBuildPolicy: 'ALWAYS',
                                    results          : [[path: 'target/allure-results']]
                            ])
                            break
                        default:
                            script.println("[JENKINS][WARNING] Can't publish test results. Testing framework is undefined.")
                            break
                    }
                }
            }
        }
    }
}

