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

package com.epam.edp.tools.autotest

import groovy.json.JsonSlurperClassic
import hudson.FilePath

class AutotestRunner {

    Script script

    def autotest
    def nexusCredentialId
    def buildTool
    def workspace
    def ns

    AutotestRunner(script, autotest, nexusCredentialId, buildTool, workspace, ns) {
        this.script = script
        this.autotest = autotest
        this.nexusCredentialId = nexusCredentialId
        this.buildTool = buildTool
        this.workspace = workspace
        this.ns = ns
    }

    def execute() {
        script.dir(workspace) {
            checkout()
            runAutotests(workspace)
        }
    }

    private def checkout() {
        script.checkout([$class                           : 'GitSCM', branches: [[name: autotest.branch]],
                         doGenerateSubmoduleConfigurations: false, extensions: [],
                         submoduleCfg                     : [],
                         userRemoteConfigs                : [[credentialsId: autotest.gitServer.credentialsId,
                                                              url          : autotest.generateSSHLink()]]])
    }

    private def runAutotests(workspace) {
        def runJsonPath = "${workspace}/run.json"
        if (!script.fileExists(runJsonPath)) {
            throw new FileNotFoundException("There is no run.json file in the project ${autotest.name}. " +
                    "Can't define command to run autotests")
        }

        def jsonCommand = getRunJsonCommand(runJsonPath)
        def command = jsonCommand["${autotest.stageName}"]
        if (!command) {
            throw new NoSuchElementException("Haven't found '${autotest.stageName}' command in file run.json. " +
                    "It's mandatory to be specified, please check")
        }

        command += " -Dnamespace=${ns}"
        script.withCredentials(getNexusCredential()) {
            def nexusProperties = getNexusProperties(buildTool, script.USERNAME, script.PASSWORD)
            script.sh "${command} ${buildTool.properties} ${nexusProperties} ${buildTool.settings}"
        }
    }

    private def getNexusProperties(buildTool, username, password) {
        if (buildTool.getClass() == com.epam.edp.buildtool.Gradle) {
            return "-PnexusLogin=${username} -PnexusPassword=${password}"
        } else if (buildTool.getClass() == com.epam.edp.buildtool.Maven) {
            return "-Dartifactory.username=${username} -Dartifactory.password=${password}"
        }
        throw new IllegalStateException("Autotests doesn't support current build tool.")
    }

    private def getRunJsonCommand(commandFilePath) {
        def command = new JsonSlurperClassic().parseText(getCommand(commandFilePath))
        script.println("[JENKINS][DEBUG] Run.json commandsCommand ${command}")
        return command
    }

    private def getCommand(commandFilePath) {
        return script.env['NODE_NAME'].equals("master")
                ? new FilePath(new File(commandFilePath)).readToString()
                : new FilePath(Jenkins.getInstance().getComputer(script.env['NODE_NAME']).getChannel(), commandFilePath).readToString()
    }

    private def getNexusCredential() {
        return [
                script.usernamePassword(
                        credentialsId: nexusCredentialId,
                        passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME'
                )
        ]
    }

}
