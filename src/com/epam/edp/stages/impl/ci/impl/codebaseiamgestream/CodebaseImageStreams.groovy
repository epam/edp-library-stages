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

package com.epam.edp.stages.impl.ci.impl.codebaseiamgestream

import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic
import hudson.FilePath

import java.text.DateFormat
import java.text.SimpleDateFormat

class CodebaseImageStreams {
    Script script
    def context

    CodebaseImageStreams(context, script) {
        this.context = context
        this.script = script
    }

    def UpdateOrCreateCodebaseImageStream(cbisName, repositoryName, imageTag) {
        def crApi = "cbis.${this.context.job.getParameterValue("GIT_SERVER_CR_VERSION")}.edp.epam.com"
        editCbisTags(crApi, cbisName, imageTag)
    }

    def editCbisTags(crApi, cbisName, imageTag) {
        def cbisCr = this.context.platform.getJsonValue(crApi, cbisName)
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
        def newcbisTag = JsonOutput.toJson('name': imageTag, 'created': dateFormat.format(new Date()))
        def preparedcbisCr = new JsonSlurperClassic().parseText(cbisCr)

        if (preparedcbisCr.spec.tags == null) {
            script.println("[JENKINS][DEBUG] There're no tags in imageStream ${cbisName} ... the first one will be added.")
            script.sh("kubectl patch ${crApi} ${cbisName} --type=merge -p '{\"spec\":{\"tags\":[${newcbisTag}]}}'")
            return
        }
        def cbisTagsList = script.sh(
            script: "kubectl get ${crApi} ${cbisName} -n ${context.job.ciProject} --output=jsonpath={.spec.tags[*].name}",
            returnStdout: true).trim()
        if (!cbisTagsList.contains(imageTag)){
            script.println("[JENKINS][DEBUG] ImageStream ${cbisName} doesn't contain ${imageTag} tag ... it will be added.")
            script.sh("kubectl patch ${crApi} ${cbisName} --type json -p='[{\"op\": \"add\", \"path\": \"/spec/tags/-\", \"value\": ${newcbisTag} }]'")
        }
    }
}