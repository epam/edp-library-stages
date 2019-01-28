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

package com.epam.edp.stages

import java.lang.annotation.Annotation
import hudson.FilePath

class StageFactory {
    Script script
    private def stages = [:]

    @NonCPS
    def loadEdpStages() {
        def classesList = []
        def res = Thread.currentThread().getContextClassLoader().getResources("com/epam/edp/stages/impl")
        def dir = new File(res.nextElement().getFile())
        dir.eachDirRecurse() { directory ->
            directory.eachFileRecurse {file ->
                classesList.push(Class.forName("com.epam.edp.stages.impl.${directory.name}."
                        + file.name.substring(0, file.name.length() - 7)))
            }
        }
        return classesList
    }

    def loadCustomStages(String directory) {
        def classesList = []
        def customStagesDir = new FilePath(Jenkins.getInstance().getComputer(script.env['NODE_NAME']).getChannel(),
                directory)

        customStagesDir.list().each {
            classesList.push(script.load(it.getRemote()))
        }
        return classesList
    }

    void add(clazz) {
        Annotation annotation = clazz.getAnnotation(Stage)
        for (tool in annotation.buildTool()) {
            for (app in annotation.type()) {
                stages.put(buildKey(annotation.name(), tool, app.getValue()), clazz)
            }
        }
    }

    def getStage(name, buildTool, type) {
        def stageClass = stages.find { it.key == buildKey(name, buildTool, type) }?.value
        if (!stageClass) {
            script.error("[JENKINS][ERROR] There are no implementation for stage: ${name} " +
                    "build tool: ${buildTool}, type: ${type}")
        }
        stageClass.newInstance(script: script)
    }

    private def buildKey(name, buildTool, type) {
        name + buildTool + type
    }
}
