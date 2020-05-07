package com.epam.edp.stages.impl.ci.impl.checksnapshot

import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage

@Stage(name = "chk-snapshot-dep", buildTool = ["maven"], type = [ProjectType.APPLICATION])
class CheckSnapshotDependenciesMaven {
    Script script

    void run(context) {
        def fileDependency = "dependency.xml"
        script.dir("${context.workDir}") {
            script.sh "mvn dependency:tree -Dincludes=:::*-SNAPSHOT -DoutputFile=${fileDependency}"
            def snapshotDependencies = script.readFile "${context.workDir}/${fileDependency}"
            if (snapshotDependencies.size() != 0) {
                script.error("[JENKINS][ERROR] Dependency SNAPSHOT fount in pom.xml: ${snapshotDependencies}")
            }
        }
    }
}