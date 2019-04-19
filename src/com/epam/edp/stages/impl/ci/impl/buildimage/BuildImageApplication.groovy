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

package com.epam.edp.stages.impl.ci.impl.buildimage

class BuildImageApplication {
    Script script

    void run(context) {
        def buildconfigName = "${context.application.name}-${context.gerrit.branch.replaceAll("[^\\p{L}\\p{Nd}]+", "-")}"
        context.application.imageBuildArgs.push("--name=${buildconfigName}")
        context.application.imageBuildArgs.push("--image-stream=s2i-${context.application.config.language.toLowerCase()}")
        def resultTag
        def targetTags = [context.application.buildVersion, "latest"]
        script.openshift.withCluster() {
            script.openshift.withProject() {
                if (!script.openshift.selector("buildconfig", "${buildconfigName}").exists())
                    script.openshift.newBuild(context.application.imageBuildArgs)

                script.dir(context.application.deployableModuleDir) {
                    script.sh "tar -cf ${context.application.name}.tar *"
                    def buildResult = script.openshift.selector("bc", "${buildconfigName}").startBuild(
                            "--from-archive=${context.application.name}.tar",
                            "--wait=true")
                    resultTag = buildResult.object().status.output.to.imageDigest
                }
                script.println("[JENKINS][DEBUG] Build config ${context.application.name} with result " +
                        "${buildconfigName}:${resultTag} has been completed")


                targetTags.each() { tagName ->
                    if (context.job.promoteImages) {
                        script.openshift.tag("${script.openshift.project()}/${buildconfigName}@${resultTag}",
                                "${context.job.envToPromote}/${buildconfigName}:${tagName}")
                    }
                    script.openshift.tag("${script.openshift.project()}/${buildconfigName}@${resultTag}",
                            "${script.openshift.project()}/${buildconfigName}:${tagName}")
                }
                if (!context.job.promoteImages) {
                    script.println("[JENKINS][WARNING] Image wasn't promoted since there are no environments " +
                            "were added\r\n [JENKINS][WARNING] If your like to promote your images please add " +
                            "environment via your cockpit panel")
                }
            }
        }
    }

}
