/* Copyright 2020 EPAM Systems.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.

See the License for the specific language governing permissions and
limitations under the License.*/

package com.epam.edp.stages.impl.ci.impl.tests


import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage
import hudson.FilePath

@Stage(name = "tests", buildTool = "python", type = [ProjectType.APPLICATION, ProjectType.LIBRARY])
class TestPythonApplicationLibrary {
    Script script

    void run(context) {
        script.dir("${context.workDir}") {

            def scriptName = "run_service.py"
            def pythonScript = new FilePath(Jenkins.getInstance().getComputer(script.env['NODE_NAME']).getChannel(),
                    "${context.workDir}/${scriptName}")
            if (pythonScript.exists())
                script.sh "python ${scriptName} &"
            else
                script.println("[JENKINS][DEBUG] Runnnig run_service.py has been skipped due to file is not found")

            try {
                script.sh "python setup.py pytest"
            }
            catch (Exception ex) {
                script.error("[JENKINS][DEBUG] Test stage hasn't passed")
            }
            finally {
                script.sh "pkill -9 python"
                script.allure([
                        includeProperties: false,
                        reportBuildPolicy: 'ALWAYS',
                        results          : [[path: 'target/allure-results']]
                ])
            }
        }
    }
}

