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

package com.epam.edp.stages.impl.cd.impl

import com.epam.edp.stages.impl.cd.Stage
import com.epam.edp.stages.impl.ci.impl.codebaseiamgestream.CodebaseImageStreams
import org.apache.commons.lang.RandomStringUtils
import groovy.json.JsonSlurperClassic

@Stage(name = "promote-images")
class PromoteImages {
    Script script

    def getCodebaseTagFromAnnotation(codebaseName, stageName, namespace) {
        def annotationPrefix = "app.edp.epam.com/"
        def stageData = script.sh(
            script: "kubectl get stages.v2.edp.epam.com ${stageName} -n ${namespace} --output=json",
            returnStdout: true).trim()
        def stageJsonData = new JsonSlurperClassic().parseText(stageData)
        def codebaseTag = stageJsonData.metadata.annotations."${annotationPrefix}${codebaseName}"
        return codebaseTag
    }

    void run(context) {
        script.openshift.withCluster() {
            script.openshift.withProject() {
                context.job.codebasesList.each() { codebase ->
                    def codebaseTag = getCodebaseTagFromAnnotation(codebase.name, "${context.job.pipelineName}-${context.job.stageName}", context.job.ciProject)
                    if ((codebase.name in context.job.applicationsToPromote) && (codebaseTag != null)) {
                        script.openshift.tag("${codebase.inputIs}:${codebaseTag}",
                                "${codebase.outputIs}:${codebaseTag}")

                        context.workDir = new File("/tmp/${RandomStringUtils.random(10, true, true)}")
                        context.workDir.deleteDir()

                        def dockerRegistryHost = context.platform.getJsonPathValue("edpcomponent", "docker-registry", ".spec.url")
                        if (!dockerRegistryHost) {
                            script.error("[JENKINS][ERROR] Couldn't get docker registry server")
                        }

                        new CodebaseImageStreams(context, script)
                                .UpdateOrCreateCodebaseImageStream(codebase.outputIs, "${dockerRegistryHost}/${codebase.outputIs}", codebaseTag)

                        script.println("[JENKINS][INFO] Image ${codebase.inputIs}:${codebaseTag} has been promoted to ${codebase.outputIs}")
                    }
                }
            }
        }
    }
}

