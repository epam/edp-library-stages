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

@Stage(name = "copy-secrets")
class CopySecrets {
  Script script

  void run(context) {
    def deployProject = context.job.deployProject
    script.node("edp-helm") {
            script.sh(script: """
                 secrets=(\$(kubectl get secrets -l 'app.edp.epam.com/use=cicd' --no-headers -o=custom-columns=NAME:.metadata.name))
                  for secret in \${secrets[@]}; do
                        kubectl get secret \$secret -o json | \
                        jq 'del(.data.namespace,.metadata.namespace,.metadata.resourceVersion,.metadata.uid,.metadata.ownerReferences,.metadata.managedFields) | .metadata.creationTimestamp=null' | \
                        kubectl -n "${deployProject}" apply -f -
                  done
             """)
    }
  }
}
