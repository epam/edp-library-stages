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

class Autotest {

    def gitServer
    def name
    def projectPath
    def branch
    def stageName

    Autotest(gitServer, projectPath, name, branch, stageName) {
        this.gitServer = gitServer
        this.projectPath = projectPath
        this.name = name
        this.branch = branch
        this.stageName = stageName
    }

    def generateSSHLink() {
        def path = projectPath?.trim() ? projectPath : "/${name}"
        return "ssh://${gitServer.user}@${gitServer.host}:${gitServer.sshPort}${path}"
    }

    @NonCPS
    String toString() {
        return "gitServer - ${gitServer}, " +
                "name - ${name}, " +
                "projectPath - ${projectPath}, " +
                "branch - ${branch}, " +
                "stageName - ${stageName}"

    }
}