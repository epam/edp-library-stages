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

import com.epam.edp.buildtool.BuildToolFactory
import com.epam.edp.stages.impl.cd.Stage
import com.epam.edp.tools.autotest.Autotest
import com.epam.edp.tools.autotest.AutotestRunner
import com.epam.edp.tools.autotest.GitServer
import com.epam.edp.tools.autotest.report.ReportTool
import org.apache.commons.lang.RandomStringUtils

@Stage(name = "autotests")
class AutomationTests {

    Script script

    void run(context, runStageName = null) {
        def deployResult = script.currentBuild.currentResult
        if (deployResult == 'UNSTABLE') {
            script.error "[JENKINS][ERROR] Autotests will not be launched, because something went wrong during the deploy stage."
        }
        def qualityGate = getCurrentQualityGate(script, context.job.qualityGates, runStageName)
        def slave = getSlave(script, context.platform, context.job, qualityGate.autotest.name)

        script.node(slave) {
            def buildTool = initBuildTool(context, script, qualityGate.autotest.name)
            def gitServer = new GitServer()
            gitServer.init(context.platform, qualityGate.autotest.gitServer)
            script.println("[JENKINS][DEBUG] GitServer data is set up: ${gitServer.toString()}")

            def autotest = new Autotest(gitServer, qualityGate.autotest.git_url, qualityGate.autotest.name,
                    qualityGate.codebaseBranch.branchName, context.job.stageName)
            script.println("[JENKINS][DEBUG] Autotest data is set up: ${autotest.toString()}")

            def workspace = generateFileSystemPath(script, autotest.name)
            def testableNamespace = context.job.getParameterValue("TESTABLE_NAMESPACE", context.job.deployProject)
            def runner = new AutotestRunner(script, autotest, context.nexus.credentialsId, buildTool, workspace, testableNamespace)
            try {
                runner.execute()
            } catch (Exception ex) {
                script.error "[JENKINS][ERROR] Autotests from ${qualityGate.autotest.name} have failed. Reason - ${ex}"
            } finally {
                new ReportTool().getReportFramework(script, qualityGate.autotest.testReportFramework, workspace).generateReport()
            }
        }
    }

    private static def generateFileSystemPath(script, autotestName) {
        def workspace = "${script.WORKSPACE}/${RandomStringUtils.random(10, true, true)}/${autotestName}"
        script.println("[JENKINS][DEBUG] Autotests workspace - ${workspace}")
        return workspace
    }

    private static def getCurrentQualityGate(script, qualityGates, stepName) {
        def qualityGate = qualityGates.find { it.stepName == stepName }
        script.println("[JENKINS][DEBUG] Quality gate content - ${qualityGate}")
        return qualityGate
    }

    private static def getSlave(script, platform, job, autotestName) {
        def slave = platform.getJsonPathValue("codebases.${job.crApiVersion}.edp.epam.com",
                autotestName, ".spec.jenkinsSlave")
        script.println("[JENKINS][DEBUG] Autotests slave - ${slave}")
        return slave
    }

    private static def initBuildTool(context, script, autotestName) {
        def buildTool = context.platform.getJsonPathValue("codebases.${context.job.crApiVersion}.edp.epam.com",
                autotestName, ".spec.buildTool")
        context.buildTool = new BuildToolFactory().getBuildToolImpl(buildTool, script, context.nexus, context.job)
        context.buildTool.init()
        return context.buildTool
    }

}
