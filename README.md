# ISW2 Project - ML Module
Author: Andrea Cantarini

## Introduction
This project has the goal of retrieving releases information and collecting relative metrics about an open-source
project.

Specifically, two projects are considered separately: BookKeeper and ZooKeeper.

The project takes advantage of existing APIs and libraries.
- the [Jira API](https://issues.apache.org/jira/rest/api/2/search) is used to retrieve information about versions and tickets related to the projects analyzed
- the [CK](https://github.com/mauricioaniche/ck?tab=readme-ov-file) Java library is used to collect metrics related
to the project

## Steps
When executing the project on a given open-source project, the process follows these steps:
1. **versions retrieval**: project versions are retrieved from the Jira API and numbered
2. **tickets retrieval**: the same Jira API is used to retrieve information about tickets
3. **commits association**: by looking at the comment within, each commit from the project repository is associated with
one or more of the previously retrieved tickets
4. **fix version setting**: since not every ticket on Jira declares its fix version, then it is inferred from the associated commits
5. **proportion**: a proportion technique is used to compute the injected version of tickets not declaring it
6. **affected versions setting**: once all tickets have both injected and opening version, affected versions can be computed
7. **metric collection**: a suite of software metrics is evaluated for each project version; refer to the [metrics](#metrics) section to get
more info about them
8. **dataset creation**: once metrics are finally collected for each version, these are put inside a CSV dataset

## Metrics
The project considers the following set of metrics. Metrics are evaluated on each method.
- `LOC`: method lines of code
- `NStatement`: number of statements withing the method body
- `Age`: number of versions the method is present within, up to the current version
- `NAuth`: number of authors who worked on the method, normalized to the age of the method
- `NRev`: number of method revisions in its lifetime
- `LOCTouched`: number of lines of code edited (i.e., added and deleted) since the last version
- `NAuthRev`: numbers of authors who contributed to the last revision
- `Overload`: number of homonymous methods in the same class
- `Override`: number of total overridings experienced by the method
- `Churn`: sum, over the revisions, of the difference between added and deleted LOC
- `NSmells`: number of code smells withing the method body
- `Bugs`: number of bugs the method has beed subject to in the past
- `FanIn`: number of times the method is called within the project
- `FanOut`: number of times the method calls other methods within its body
- `NParams`: number of parameters in the method signature
- `NComments`: number of commented lines (excluding code) within the method body
- `HasJDoc`:  presence - or not - of Javadocs on the method