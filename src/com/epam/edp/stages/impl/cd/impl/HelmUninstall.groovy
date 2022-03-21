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

@Stage(name = "helm-uninstall")
class HelmUninstall {
    Script script

    def cleanHelmReleases(deployProject) {
        try {
            script.sh("helm ls -a -q --namespace=${deployProject} | " +
                    "xargs -r helm --namespace=${deployProject} uninstall ")
        }
        catch (Exception ex) {
            script.println("[JENKINS][DEBUG] Unable to uninstall Helm release")
        }
    }

    void run(context) {
        cleanHelmReleases(context.job.deployProject)
    }
}
