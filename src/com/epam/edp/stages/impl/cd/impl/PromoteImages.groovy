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

package com.epam.edp.stages.impl.cd.impl

import com.epam.edp.stages.impl.cd.Stage

@Stage(name = "promote-images")
class PromoteImages {
    Script script

    void run(context) {
        script.openshift.withCluster() {
            script.openshift.withProject() {
                context.job.codebasesList.each() { codebase ->
                    if ((codebase.name in context.job.applicationsToPromote) && (codebase.version != "No deploy") && (codebase.version != "noImageExists"))
                    {
                        script.openshift.tag("${context.job.promotion.sourceProject}/${codebase.normalizedName}:${codebase.version}",
                                "${context.job.promotion.sourceProject}/${codebase.outputIs}:${codebase.version}")

                        script.println("[JENKINS][INFO] Image ${codebase.normalizedName}:${codebase.version} has been promoted to ${codebase.outputIs}")
                    }
                }
            }
        }
    }
}

