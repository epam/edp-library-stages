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

package com.epam.edp.stages.impl.ci.impl.tests


import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage
import hudson.FilePath
import groovy.json.*

@Stage(name = "tests", buildTool = "maven", type = ProjectType.AUTOTESTS)
class TestsMavenAutotest {
    Script script

    void run(context) {
        script.dir("${context.workDir}") {
            def runCommandFile = new FilePath(
                    Jenkins.getInstance().getComputer(script.env['NODE_NAME']).getChannel(),
                    "${context.workDir}/run.json"
            )
            if (!runCommandFile.exists())
                script.error "[JENKINS][ERROR] There is no run.json file in the project " +
                        "${context.git.project}. Can't define command to run autotests"

            def parsedRunCommandJson = new JsonSlurperClassic().parseText(runCommandFile.readToString())
            if (!("codereview" in parsedRunCommandJson.keySet()))
                script.error "[JENKINS][ERROR] Haven't found codereview command in file run.json. " +
                        "It's mandatory to be specified, please check"

            try {
                script.withCredentials([script.usernamePassword(credentialsId: "${context.nexus.credentialsId}",
                        passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
                    script.sh "${parsedRunCommandJson.codereview} ${context.buildTool.properties} -Dartifactory.username=${script.USERNAME} -Dartifactory.password=${script.PASSWORD} " +
                            "${context.buildTool.settings}"
                }
            }

            catch (Exception ex) {
                script.error "[JENKINS][ERROR] Tests have been failed with error - ${ex}"
            }
            finally {
                switch (context.codebase.config.testReportFramework.toLowerCase()) {
                    case "allure":
                        script.allure([
                                includeProperties: false,
                                reportBuildPolicy: 'ALWAYS',
                                results          : [[path: 'target/allure-results']]
                        ])
                        break
                    default:
                        script.println("[JENKINS][WARNING] Can't publish test results. Testing framework is unknown.")
                        break
                }
            }
        }
    }
}
