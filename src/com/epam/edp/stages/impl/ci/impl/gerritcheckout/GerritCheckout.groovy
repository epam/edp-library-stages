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

package com.epam.edp.stages.impl.ci.impl.gerritcheckout


import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage

@Stage(name = "gerrit-checkout", buildTool = ["maven", "npm", "dotnet","gradle", "any"], type = [ProjectType.APPLICATION, ProjectType.AUTOTESTS, ProjectType.LIBRARY])
class GerritCheckout {
    Script script

    def run(context) {
        script.dir("${context.workDir}") {
            script.checkout([$class                           : 'GitSCM', branches: [[name: "${context.git.changeName}"]],
                             doGenerateSubmoduleConfigurations: false, extensions: [],
                             submoduleCfg                     : [],
                             userRemoteConfigs                : [[refspec      : "${context.git.refspecName}:${context.git.changeName}",
                                                                  credentialsId: "${context.git.credentialsId}",
                                                                  url          : "${context.codebase.config.cloneUrl}"]]])
        }
        context.factory.loadCustomStages("${context.workDir}/stages").each() { context.factory.add(it) }
    }
}
