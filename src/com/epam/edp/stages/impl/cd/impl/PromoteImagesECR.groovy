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

@Stage(name = "promote-images-ecr")
class PromoteImagesECR {
    Script script

    void run(context) {
        def dockerRegistryHost = context.platform.getJsonPathValue("edpcomponent", "docker-registry", ".spec.url")
        if (!dockerRegistryHost)
            script.error("[JENKINS][ERROR] Couldn't get docker registry server")

        context.job.codebasesList.each() { codebase ->
            if ((codebase.name in context.job.applicationsToPromote) && (codebase.version != "No deploy") && (codebase.version != "noImageExists")) {
                context.workDir = new File("/tmp/${RandomStringUtils.random(10, true, true)}")
                context.workDir.deleteDir()

                script.dir("${context.workDir}") {
                    new CodebaseImageStreams(context, script)
                            .UpdateOrCreateCodebaseImageStream(codebase.outputIs, "${dockerRegistryHost}/${context.job.ciProject}/${codebase.outputIs}", codebase.version)
                }

                script.println("[JENKINS][INFO] Image ${codebase.inputIs}:${codebase.version} has been promoted to ${codebase.outputIs}")
            }
        }
    }
}