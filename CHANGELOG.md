<a name="unreleased"></a>
## [Unreleased]

### Features

- Update Makefile changelog target [EPMDEDP-8218](https://jiraeu.epam.com/browse/EPMDEDP-8218)
- Provide the ability to customize the deploy pipeline [EPMDEDP-8313](https://jiraeu.epam.com/browse/EPMDEDP-8313)
- Add helm-docs code-review stage [EPMDEDP-8329](https://jiraeu.epam.com/browse/EPMDEDP-8329)
- Enable stages for library [EPMDEDP-8341](https://jiraeu.epam.com/browse/EPMDEDP-8341)
- Add GetVersion stage for Container Library [EPMDEDP-8341](https://jiraeu.epam.com/browse/EPMDEDP-8341)
- Add copy-secret pipeline stage [EPMDEDP-8469](https://jiraeu.epam.com/browse/EPMDEDP-8469)
- Implement helm-uninstall step [EPMDEDP-8532](https://jiraeu.epam.com/browse/EPMDEDP-8532)
- Implement semi-auto-deploy-input stage with timeout and manual mode [EPMDEDP-8584](https://jiraeu.epam.com/browse/EPMDEDP-8584)
- Update Kaniko stage for using Kaniko template in yaml format [EPMDEDP-8620](https://jiraeu.epam.com/browse/EPMDEDP-8620)
- Add git-tag stage for autotests [EPMDEDP-8920](https://jiraeu.epam.com/browse/EPMDEDP-8920)
- Use auth token to access NPM registry deployed on Nexus [EPMDEDP-8970](https://jiraeu.epam.com/browse/EPMDEDP-8970)

### Bug Fixes

- Add normalizedBranch parameter for naming projects in sonar [EPMDEDP-8283](https://jiraeu.epam.com/browse/EPMDEDP-8283)
- Fix changelog generation in GH Release Action [EPMDEDP-8468](https://jiraeu.epam.com/browse/EPMDEDP-8468)
- Define workdir for dockerbuild-verify stage [EPMDEDP-8832](https://jiraeu.epam.com/browse/EPMDEDP-8832)
- Align dockerbuild-verify stage to kaniko template format [EPMDEDP-8832](https://jiraeu.epam.com/browse/EPMDEDP-8832)
- Replace / to - in image tag on OKD build stage [EPMDEDP-8883](https://jiraeu.epam.com/browse/EPMDEDP-8883)
- Fix jira metadata for default versionning for kaniko [EPMDEDP-8958](https://jiraeu.epam.com/browse/EPMDEDP-8958)
- Pass email for nexus NPM regisrty during login stage [EPMDEDP-8970](https://jiraeu.epam.com/browse/EPMDEDP-8970)

### Code Refactoring

- Wipe the workspace after the Deploy stage [EPMDEDP-7683](https://jiraeu.epam.com/browse/EPMDEDP-7683)
- Wipe the workspace after the Deploy stage [EPMDEDP-7683](https://jiraeu.epam.com/browse/EPMDEDP-7683)
- Create separate sonar project for each branch build [EPMDEDP-8012](https://jiraeu.epam.com/browse/EPMDEDP-8012)
- Get aws_region variable from configmap [EPMDEDP-8164](https://jiraeu.epam.com/browse/EPMDEDP-8164)
- The refspec value must be defined early [EPMDEDP-8168](https://jiraeu.epam.com/browse/EPMDEDP-8168)
- Deploy stage must use valid image tag [EPMDEDP-8168](https://jiraeu.epam.com/browse/EPMDEDP-8168)
- Remove unused functional [EPMDEDP-8168](https://jiraeu.epam.com/browse/EPMDEDP-8168)
- CD-operator responsible to create new project on cluster [EPMDEDP-8168](https://jiraeu.epam.com/browse/EPMDEDP-8168)
- Update image tag modification [EPMDEDP-8168](https://jiraeu.epam.com/browse/EPMDEDP-8168)
- Remove unused functional [EPMDEDP-8168](https://jiraeu.epam.com/browse/EPMDEDP-8168)

### Routine

- Fix grammatical errors in Jenkins libraries [EPMDEDP-8205](https://jiraeu.epam.com/browse/EPMDEDP-8205)
- Update release flow [EPMDEDP-8227](https://jiraeu.epam.com/browse/EPMDEDP-8227)
- Update changelog [EPMDEDP-8227](https://jiraeu.epam.com/browse/EPMDEDP-8227)
- Update release template [EPMDEDP-8227](https://jiraeu.epam.com/browse/EPMDEDP-8227)
- Use kaniko-docker agent for hadolint check [EPMDEDP-8341](https://jiraeu.epam.com/browse/EPMDEDP-8341)
- Add check if Kaniko pod is scheduled [EPMDEDP-8624](https://jiraeu.epam.com/browse/EPMDEDP-8624)
- Use awscli instead awscliv2 [EPMDEDP-8808](https://jiraeu.epam.com/browse/EPMDEDP-8808)


<a name="v2.12.0"></a>
## [v2.12.0] - 2022-01-21
### Features

- Use ct.yaml config on helm-lint stage [EPMDEDP-8227](https://jiraeu.epam.com/browse/EPMDEDP-8227)

### Code Refactoring

- Refactor ecr-to-docker CI stage [EPMDEDP-7974](https://jiraeu.epam.com/browse/EPMDEDP-7974)
- Use SSH Agent Jenkins Plugin instead of eval ssh-agent [EPMDEDP-8026](https://jiraeu.epam.com/browse/EPMDEDP-8026)

### Routine

- Update release CI pipelines [EPMDEDP-7847](https://jiraeu.epam.com/browse/EPMDEDP-7847)
- Add release temaplate for GitHub [EPMDEDP-8227](https://jiraeu.epam.com/browse/EPMDEDP-8227)


<a name="v2.11.0"></a>
## [v2.11.0] - 2021-12-07
### Features

- Provide the ability to configure hadolint check [EPMDEDP-7485](https://jiraeu.epam.com/browse/EPMDEDP-7485)
- Copy sonar-project.properties in Go Codereview [EPMDEDP-7743](https://jiraeu.epam.com/browse/EPMDEDP-7743)

### Bug Fixes

- Remove whitespace in git-tag stage [EPMDEDP-7847](https://jiraeu.epam.com/browse/EPMDEDP-7847)

### Code Refactoring

- Rename stage promote-images-ecr to promote-images [EPMDEDP-7378](https://jiraeu.epam.com/browse/EPMDEDP-7378)
- Remove sonar-gerrit notifications [EPMDEDP-7799](https://jiraeu.epam.com/browse/EPMDEDP-7799)

### Routine

- Align stages with SonarQube 8.9.3 version [EPMDEDP-7409](https://jiraeu.epam.com/browse/EPMDEDP-7409)
- Bump version to 2.11.0 [EPMDEDP-7847](https://jiraeu.epam.com/browse/EPMDEDP-7847)
- Align to release process [EPMDEDP-7847](https://jiraeu.epam.com/browse/EPMDEDP-7847)
- Add changelog [EPMDEDP-7847](https://jiraeu.epam.com/browse/EPMDEDP-7847)

### Documentation

- Update the links on GitHub [EPMDEDP-7781](https://jiraeu.epam.com/browse/EPMDEDP-7781)


<a name="v2.10.0"></a>
## [v2.10.0] - 2021-12-06

<a name="v2.9.1"></a>
## [v2.9.1] - 2021-12-06

<a name="v2.9.0"></a>
## [v2.9.0] - 2021-12-06

<a name="v2.8.2"></a>
## [v2.8.2] - 2021-12-06

<a name="v2.8.1"></a>
## [v2.8.1] - 2021-12-06

<a name="v2.8.0"></a>
## v2.8.0 - 2021-12-06
### Reverts

- [EPMDEDP-5584]: Fix build image kaniko stage
- [EPMDEDP-5352]: Apply crds to cluster during deploy


[Unreleased]: https://github.com/epam/edp-library-stages/compare/v2.12.0...HEAD
[v2.12.0]: https://github.com/epam/edp-library-stages/compare/v2.11.0...v2.12.0
[v2.11.0]: https://github.com/epam/edp-library-stages/compare/v2.10.0...v2.11.0
[v2.10.0]: https://github.com/epam/edp-library-stages/compare/v2.9.1...v2.10.0
[v2.9.1]: https://github.com/epam/edp-library-stages/compare/v2.9.0...v2.9.1
[v2.9.0]: https://github.com/epam/edp-library-stages/compare/v2.8.2...v2.9.0
[v2.8.2]: https://github.com/epam/edp-library-stages/compare/v2.8.1...v2.8.2
[v2.8.1]: https://github.com/epam/edp-library-stages/compare/v2.8.0...v2.8.1
