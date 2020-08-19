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

package com.epam.edp.stages.impl.ci.impl.builddockerfileimage

import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage

@Stage(name = "build-image-from-dockerfile", buildTool = ["maven","gradle", "any"], type = [ProjectType.APPLICATION,ProjectType.AUTOTESTS])
class BuildDockerfileImageAny {
    Script script

    void run(context) {
        context.codebase.imageBuildArgs = []
        context.codebase.imageBuildArgs.push("--binary=true")
        context.codebase.imageBuildArgs.push("--to-docker=true")
        new BuildDockerfileImageApplication(script: script).run(context)
    }
}
