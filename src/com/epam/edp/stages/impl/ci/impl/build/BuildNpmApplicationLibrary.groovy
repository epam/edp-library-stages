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

package com.epam.edp.stages.impl.ci.impl.build


import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage
import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic

@Stage(name = "build", buildTool = "npm", type = [ProjectType.APPLICATION, ProjectType.LIBRARY])
class BuildNpmApplicationLibrary {
    Script script

    void run(context) {
        script.dir("${context.workDir}") {
            script.withCredentials([script.usernamePassword(credentialsId: "${context.nexus.credentialsId}",
                    passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
                def url = context.buildTool.groupRepository
                def registryUrl = url.substring(url.indexOf("/"), url.length())
                def upBase64 = "${script.USERNAME}:${script.PASSWORD}".bytes.encodeBase64().toString()
                script.sh(script: """
                    set +x

                    npm set registry ${url} --location project
                    npm set ${registryUrl}:always-auth true --location project
                    npm set ${registryUrl}:email ${script.USERNAME}@example.com --location project
                    npm set ${registryUrl}:_auth ${upBase64} --location project
                """)
            }

            script.sh "npm install && npm run build:prod"
        }
    }
}
