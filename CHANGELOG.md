<a name="unreleased"></a>
## [Unreleased]


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
