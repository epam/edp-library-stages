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

@Stage(name = "semi-auto-deploy-input")
class SemiAutoDeployInput {
    Script script

    def generateInputDataForDeployJob(context) {
        def autodeployTimeout = context.job.getParameterValue("AUTODEPLOY_TIMEOUT", "5")
        def LATEST_TAG = "latest"
        try {
            script.timeout(time: autodeployTimeout, unit: 'MINUTES') {
                setCodebaseVersion(context, LATEST_TAG)
            }
        } catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException ex) {
            if (ex.getCauses()[0].getUser().toString() == 'SYSTEM') {
                script.println("[JENKINS][DEBUG] AUTO_DEPLOY: STARTED")
                context.job.codebasesList.each() { codebase ->
                    codebase.version = LATEST_TAG
                    script.println("[JENKINS][DEBUG] ${codebase.name.toUpperCase().replaceAll("-", "_")}_VERSION: ${codebase.latest}")
                }
            } else {
                throw ex
            }
        }
    }
    def setCodebaseVersion(context, latestTag) {
        def userInputImagesToDeploy
        def deployJobParameters = []
        def STABLE_TAG = "stable"
        context.job.codebasesList.each() { codebase ->
            deployJobParameters.add(script.choice(choices: "${codebase.sortedTags.join('\n')}", description: '', name: "${codebase.name.toUpperCase().replaceAll("-", "_")}_VERSION"))
        }
        userInputImagesToDeploy = script.input id: 'userInput', message: 'Provide the following information', parameters: deployJobParameters
        script.println("[JENKINS][DEBUG] USERS_INPUT_IMAGES_TO_DEPLOY: ${userInputImagesToDeploy}")
        context.job.codebasesList.each() { codebase ->
            if (userInputImagesToDeploy instanceof java.lang.String) {
                codebase.version = userInputImagesToDeploy
                if (codebase.version.startsWith(latestTag))
                    codebase.version = latestTag
                if (codebase.version.startsWith(STABLE_TAG))
                    codebase.version = STABLE_TAG
            } else {
                userInputImagesToDeploy.each() { item ->
                    if (item.value.startsWith(latestTag)) {
                        userInputImagesToDeploy.put(item.key, latestTag)
                    }
                    if (item.value.startsWith(STABLE_TAG)) {
                        userInputImagesToDeploy.put(item.key, STABLE_TAG)
                    }
                }
                codebase.version = userInputImagesToDeploy["${codebase.name.toUpperCase().replaceAll("-", "_")}_VERSION"]
            }
            codebase.version = codebase.version ? codebase.version : latestTag
        }
    }
    void run(context) {
        generateInputDataForDeployJob(context)
    }
}
