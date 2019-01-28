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

package com.epam.edp.stages.impl.runbuildconfig

import com.epam.edp.stages.ProjectType
import com.epam.edp.stages.Stage

@Stage(name = "run-build-config", buildTool = ["dotnet","maven","gradle","dotnet"], type = ProjectType.APPLICATION)
class CommonApplication {
    Script script

    void run(context) {
        context.targetTags = [context.businissAppVersion, "latest"]

        script.openshift.withCluster() {
            script.openshift.withProject() {
                if (!script.openshift.selector("buildconfig", "${context.itemMap.name}").exists())
                    script.openshift.newBuild("--name=${context.itemMap.name}",
                            "--image-stream=s2i-${context.itemMap.language.toLowerCase()}", "--binary=true")

                script.dir(context.deployableModuleDir) {
                    script.sh "tar -cf ${context.itemMap.name}.tar *"
                    buildResult = script.openshift.selector("bc", "${context.itemMap.name}").
                            startBuild("--from-archive=${context.itemMap.name}.tar", "--wait=true")
                    resultTag = buildResult.object().status.output.to.imageDigest
                }
                script.println("[JENKINS][DEBUG] Build config ${context.itemMap.name} with result ${resultTag} " +
                        "has been completed")

                if (context.promoteImage) {
                    context.targetTags.each() { tagName ->
                        script.openshift.tag("${script.openshift.project()}/${context.itemMap.name}@${resultTag}",
                                "${context.targetProject}/${context.itemMap.name}:${tagName}")
                    }
                } else
                    script.println("[JENKINS][WARNING] Image wasn't promoted since there are no environments " +
                            "were added\r\n [JENKINS][WARNING] If your like to promote your images please add " +
                            "environment via your cockpit panel")
            }
        }
    }
}
