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

package com.epam.edp.stages.impl.ci.impl.lint

import org.apache.commons.lang.RandomStringUtils
import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage

@Stage(name = "terraform-lint", buildTool = "terraform", type = [ProjectType.LIBRARY])
class LintTerraformLibrary {
    Script script

    void run(context) {
            script.dir("${context.workDir}") {
                script.ansiColor('xterm') {
                        script.sh """
                            if [ -f .terraform-version ]; then
                                tfenv install
                            else
                                tfenv install 0.14.5
                            fi
                            terraform init -backend=false
                        """.stripIndent()
                        script.sh (script: """
                            set -ex
                            terraform fmt -check -list=true -diff
                            tflint
                            terraform validate
                        """)
                }
            }
    }
}