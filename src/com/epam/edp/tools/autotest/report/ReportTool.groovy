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

package com.epam.edp.tools.autotest.report

class Allure {

    Script script

    def allureReportPath = 'target/allure-results'

    Allure(script) {
        this.script = script
    }

    def generateReport() {
        script.println("[JENKINS][DEBUG] generating Allure reports")
        script.allure([
                includeProperties: false,
                jdk              : '',
                properties       : [],
                reportBuildPolicy: 'ALWAYS',
                results          : [[path: allureReportPath]]
        ])
    }

}

class Gatling {

    Script script

    Gatling(script) {
        this.script = script
    }

    def generateReport() {
        script.println("[JENKINS][DEBUG] generating Gatling reports")
        script.gatlingArchive()
    }

}

def getReportFramework(script, name) {
    if ("allure" == name) {
        return new Allure(script)
    } else ("gatling" == name) {
        return new Gatling(script)
    }
    script.println("[JENKINS][WARNING] Can't publish test results. Testing framework is undefined.")
}