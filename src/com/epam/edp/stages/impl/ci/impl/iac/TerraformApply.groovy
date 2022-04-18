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

package com.epam.edp.stages.impl.ci.impl.iac

import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage

@Stage(name = "terraform-apply", buildTool = "terraform", type = [ProjectType.LIBRARY])
class TerraformApply {
    Script script

    void run(context) {
        def awsCredentialsId = context.job.getParameterValue("AWS_CREDENTIALS", "aws.user")
        script.dir("${context.workDir}") {
            script.withCredentials([[
              $class: 'AmazonWebServicesCredentialsBinding',
              credentialsId: "${awsCredentialsId}",
              accessKeyVariable: 'AWS_ACCESS_KEY_ID',
              secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                    script.ansiColor('xterm') {
                        script.sh """
                            aws sts get-caller-identity
                            if [ -f .terraform-version ]; then
                                tfenv install
                            else
                                tfenv install 0.14.5
                            fi
                            terraform init
                            terraform plan -out=platform_results.tfplan
                        """
                        script.timeout(time: 30, unit: 'MINUTES') {
                            script.input "Do you want to apply terraform code for ${context.codebase.name} project?"
                        }
                        script.println("[JENKINS][DEBUG] Applying changes")
                        script.sh """
                            terraform apply platform_results.tfplan
                        """
                    }
              }

        }
    }
}