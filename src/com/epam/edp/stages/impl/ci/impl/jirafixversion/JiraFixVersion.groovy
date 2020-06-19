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

package com.epam.edp.stages.impl.ci.impl.jirafixversion

import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage
import com.github.jenkins.lastchanges.pipeline.LastChangesPipelineGlobal
import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic
import hudson.FilePath

@Stage(name = "create-jira-fix-version", buildTool = ["maven", "npm", "dotnet", "gradle", "any"], type = [ProjectType.APPLICATION, ProjectType.AUTOTESTS, ProjectType.LIBRARY])
class JiraFixVersion {
    Script script

    def getChanges(workDir) {
        script.dir("${workDir}") {
            def publisher = new LastChangesPipelineGlobal(script).getLastChangesPublisher "LAST_SUCCESSFUL_BUILD", "SIDE", "LINE", true, true, "", "", "", "", ""
            publisher.publishLastChanges()
            return publisher.getLastChanges()
        }
    }

    def getJiraFixTemplate(platform) {
        script.println("[JENKINS][DEBUG] Getting JiraFixVersion CR template")
        def temp = platform.getJsonPathValue("cm", "jfv-template", ".data.jfv\\.json")
        script.println("[JENKINS][DEBUG] JiraFixVersion template has been fetched ${temp}")
        return new JsonSlurperClassic().parseText(temp)
    }

    def addCommitId(template, id) {
        if (template.spec.commits == "replace") {
            template.spec.commits = []
        }
        template.spec.commits.add(id)
    }

    def addTicketNumber(template, tickets) {
        if (template.spec.tickets == "replace") {
            template.spec.tickets = []
        }
        template.spec.tickets.addAll(tickets)
    }

    def parseJiraFixVersionTemplate(template, context, commits, pattern) {
        script.println("[JENKINS][DEBUG] Parsing JiraFixVersion template")
        template.metadata.name = "${context.codebase.config.name}-${context.codebase.isTag}".toLowerCase()
        template.spec.codebaseName = "${context.codebase.config.name}"
        for (commit in commits) {
            def info = commit.getCommitInfo()
            script.println("[JENKINS][DEBUG] Commit message ${info.getCommitMessage()}")
            def ticket = info.getCommitMessage().findAll(pattern)
            def id = info.getCommitId()
            if (!ticket) {
                script.println("[JENKINS][DEBUG] No found tickets in ${id} commit")
                continue
            }
            addCommitId(template, id)
            addTicketNumber(template, ticket)
        }
        return JsonOutput.toJson(template)
    }

    def createJiraFixVersionCR(platform, path) {
        script.println("[JENKINS][DEBUG] Trying to create JiraFixVersion CR")
        platform.apply(path.getRemote())
        script.println("[JENKINS][INFO] JiraFixVersion CR has been created")
    }

    def saveTemplateToFile(outputFilePath, template) {
        def jiraFixVersionTemplateFile = new FilePath(Jenkins.getInstance().
                getComputer(script.env['NODE_NAME']).
                getChannel(), outputFilePath)
        jiraFixVersionTemplateFile.write(template, null)
        return jiraFixVersionTemplateFile
    }

    def tryToCreateJiraFixVersionCR(workDir, platform, parsedTemplate) {
        if (new JsonSlurperClassic().parseText(parsedTemplate).spec.tickets == "replace") {
            script.println("[JENKINS][DEBUG] No changes. Skip creating JavaFixVersion CR")
            return
        }
        def filePath = saveTemplateToFile("${workDir}/jfv-template.json", parsedTemplate)
        createJiraFixVersionCR(platform, filePath)
    }

    void run(context) {
        try {
            def ticketNamePattern = context.codebase.config.ticketNamePattern
            script.println("[JENKINS][DEBUG] Ticket name pattern has been fetched ${ticketNamePattern}")
            def changes = getChanges(context.workDir)
            def commits = changes.getCommits()
            if (commits == null) {
                script.println("[JENKINS][INFO] No changes since last successful build. Skip creating JiraFixVersion CR")
            } else {
                def template = getJiraFixTemplate(context.platform)
                def parsedTemplate = parseJiraFixVersionTemplate(template, context, commits, ticketNamePattern)
                tryToCreateJiraFixVersionCR(context.workDir, context.platform, parsedTemplate)
            }
        } catch(Exception ex) {
            script.println("[JENKINS][WARNING] Couldn't correctly finish 'create-jira-fix0version' stage due to exception: ${ex}")
        }
    }

}