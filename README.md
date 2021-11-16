# EDP Library Stages

| :heavy_exclamation_mark: Please refer to [EDP documentation](https://epam.github.io/edp-install/) to get the notion of the main concepts and guidelines. |
| --- |

The **EDP Library Stages** repository describes the specific stages and their realization in frames of a specific pipeline. Every stage is presented as an individual Groovy file in a corresponding repository. Such single responsibility realization allows rewriting of one essential stage without changing the whole pipeline.

The [EDP Library Pipelines](https://github.com/epam/edp-library-pipelines/blob/master/README.md#edp-library-pipelines) repository describes the general structure of the code review, build, and deploy pipelines. Every pipeline has a set of stages that are consumed from a pipeline`s parameters of a user and can be redefined as well.

If EDP pipelines are not enough for the CI/CD needs, it is possible to add a custom stage. To do this, a user creates the stage, adds it to the application repository, thus extending the EDP Pipelines Framework by customization, realization, and redefinition of the user stages. In such a case, the priority goes to the user stages.

### Related Articles

- [Customize CI Pipeline](https://epam.github.io/edp-install/user-guide/customize-ci-pipeline/)
- [Customize CD Pipeline](https://epam.github.io/edp-install/user-guide/customize-cd-pipeline/)

>_**NOTE**: To get more accurate information on the CI/CD customization, please refer to the [admin-console](https://github.com/epam/edp-admin-console/tree/master#edp-admin-console) repository._
