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

@Stage(name = "automation-tests")
class AutomationTests {
    Script script

    void run(context) {
        def codebaseDir = "${script.WORKSPACE}/${RandomStringUtils.random(10, true, true)}/${context.job.autotestName}"
        script.dir("${codebaseDir}") {
            def gitCodebaseUrl = "ssh://${context.git.autouser}@${context.git.host}:${context.git.sshPort}/${context.job.autotestName}"

            script.checkout([$class                           : 'GitSCM', branches: [[name: "${context.job.autotestBranch}"]],
                             doGenerateSubmoduleConfigurations: false, extensions: [],
                             submoduleCfg                     : [],
                             userRemoteConfigs                : [[credentialsId: "${context.git.credentialsId}",
                                                                  url          : "${gitCodebaseUrl}"]]])

            if (!script.fileExists("${codebaseDir}/run.json"))
                script.error "[JENKINS][ERROR] There is no run.json file in the project ${context.job.autotestName}. " +
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
                script.sh "${runCommand} -B --settings ${context.buildTool.settings}"
            }
            catch (Exception ex) {
                script.error "[JENKINS][ERROR] Tests from ${context.job.autotestName} have been failed. Reason - ${ex}"
            }
            finally {
                switch ("${context.job.testReportFramework}") {
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

