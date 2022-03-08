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

import com.epam.edp.Environment
import com.epam.edp.Jenkins
import com.epam.edp.Nexus
import com.epam.edp.stages.impl.cd.Stage

@Stage(name = "init")
class Init {

    Script script

    void run(context) {
        println("[JENKINS][DEBUG] running init stage")

        context.job.initDeployJob()

        context.nexus = new Nexus(context.job, context.platform, script)
        context.nexus.init()

        context.jenkins = new Jenkins(context.job, context.platform, script)
        context.jenkins.init()

        context.environment = new Environment(context.job.deployProject, context.platform, script)

        context.job.setDisplayName("${script.currentBuild.displayName}-${context.job.deployProject}")
    }
}