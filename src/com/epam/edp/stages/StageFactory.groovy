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

import com.epam.edp.stages.impl.ci.Stage as ciStage
import com.epam.edp.stages.impl.cd.Stage as cdStage
import groovy.io.FileType

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
            directory.eachFile(FileType.FILES) { file ->
                classesList.push(Class.forName("com.epam.edp.stages.impl.${directory.path.replace("${dir.path}/", "").replaceAll('/', '.')}."
                        + file.name.substring(0, file.name.length() - 7)))
            }
        }
        return classesList
    }

    @NonCPS
    def loadCustomStagesFromLib() {
        def classesList = []
        def res = Thread.currentThread().getContextClassLoader().getResources("com/epam/edp/customStages/impl")

        try {
            def dir = new File(res.nextElement().getFile())
            dir.eachDirRecurse() { directory ->
            directory.eachFile(FileType.FILES) { file ->
                classesList.push(Class.forName("com.epam.edp.customStages.impl.${directory.path.replace("${dir.path}/", "").replaceAll('/', '.')}."
                        + file.name.substring(0, file.name.length() - 7)))
                }
            }
            if (classesList.contains(null)) {
                script.println("[JENKINS][DEBUG] One or more files from custom stage are empty")
                classesList.removeAll([null])
            }
        } catch (java.util.NoSuchElementException ex) {
            script.println("[JENKINS][DEBUG] Not found custom stages from lib")
        }

        return classesList
    }

    def loadCustomStages(String directory) {
        def classesList = []
        def customStagesDir

        if (script.env['NODE_NAME'].equals("master")) {
            def stagesDir = new File(directory)
            customStagesDir = new FilePath(stagesDir)
        } else {
            customStagesDir = new FilePath(Jenkins.getInstance().getComputer(script.env['NODE_NAME']).getChannel(),
                    directory)
        }
        customStagesDir.list().each {
            classesList.push(script.load(it.getRemote()))
        }
        if (classesList.contains(null)) {
            script.println("[JENKINS][DEBUG] One or more files from custom stage are empty")
            classesList.removeAll([null])
        }
        return classesList
    }

    void add(clazz) {
        Annotation ciAnnotation = clazz.getAnnotation(ciStage)
        if (ciAnnotation) {
            for (tool in ciAnnotation.buildTool()) {
                for (app in ciAnnotation.type()) {
                    stages.put(ciKey(ciAnnotation.name(), tool, app.getValue()), clazz)
                }
            }
        }

        Annotation cdAnnotation = clazz.getAnnotation(cdStage)
        if (cdAnnotation)
            stages.put(cdKey(cdAnnotation.name()), clazz)
    }

    def getStage(name, buildTool = null, type = null) {
        def stageClass
        if (buildTool && type)
            stageClass = stages.get(ciKey(name, buildTool, type)) ?: stages.get(ciKey(name, "any", type))
        else
            stageClass = stages.find { it.key == cdKey(name) }?.value

        if (!stageClass) {
            script.error("[JENKINS][ERROR] There is no implementation for the stage: ${name} " +
                    "build tool: ${buildTool}, type: ${type}")
        }
        stageClass.newInstance(script: script)
    }

    private def ciKey(name, buildTool, type) {
        name + buildTool + type
    }

    private def cdKey(name) {
        name
    }
}
