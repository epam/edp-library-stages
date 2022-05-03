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

package com.epam.edp.stages.impl.ci.impl.compile

import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage
import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic

@Stage(name = "compile", buildTool = "npm", type = [ProjectType.APPLICATION, ProjectType.LIBRARY])
class CompileNpmApplicationLibrary {
    Script script

    void run(context) {
        script.dir("${context.workDir}") {
            script.withCredentials([script.usernamePassword(credentialsId: "${context.nexus.credentialsId}",
                    passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
                def url = "${context.buildTool.groupRepository}-/user/org.couchdb.user:${script.USERNAME}"
                def requestBody = JsonOutput.toJson([
                        name: script.USERNAME,
                        password: script.PASSWORD
                ])
                def response = script.httpRequest url: url,
                        httpMode: 'PUT',
                        contentType: 'APPLICATION_JSON',
                        requestBody: requestBody

                def registryUrl = url.substring(url.indexOf("/"), url.length())
                def token = new JsonSlurperClassic().parseText(response.content).token
                script.sh(script: """
                    set +x
                    npm set registry ${context.buildTool.groupRepository}

                    echo :always-auth=true\n >> .npmrc
                    echo ${registryUrl}:_authToken=${token}\\n >> .npmrc
                """)
            }

            script.sh "npm install && npm run build:clean"
        }
    }
}