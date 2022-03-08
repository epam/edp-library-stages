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

package com.epam.edp.stages.impl.cd.impl

import com.epam.edp.stages.impl.cd.Stage
import groovy.json.JsonSlurperClassic

@Stage(name = "auto-deploy-input")
class AutoDeployInput {
    Script script

    void run(context) {
        def codebasesToDeploy = context.job.getParameterValue("CODEBASE_VERSION", "")
        if (!codebasesToDeploy?.trim()) {
            script.error("[JENKINS][ERROR] Codebase versions must be passed to job.")
        }
        script.println("[JENKINS][INFO] Payload to deploy: ${codebasesToDeploy}")

        def codebasesToDeployJson = new JsonSlurperClassic().parseText(codebasesToDeploy)
        context.job.codebasesList.each() { codebase ->
            def foundCodebase = codebasesToDeployJson.find{ it.codebase == codebase.name}
            codebase.version = foundCodebase ? foundCodebase.tag : "No deploy"
            script.println("[JENKINS][DEBUG] Deploy version for ${codebase.name} is ${codebase.version}")
        }
    }
}