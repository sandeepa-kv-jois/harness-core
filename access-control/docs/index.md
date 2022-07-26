Access Control
Overview
Access Control is built to provide intra-account fine grained Access Control for multiple principals (User, User Groups and Service Accounts) on multiple resources (Pipelines, Secrets, etc). It has been designed based on Role Based Access Control (RBAC) while keeping in mind the hierarchical structure of Harness NG. The model is explained in detail here - Access Control Model.

Access Control Service is a separate microservice in Harness. The service boundaries for Access Control are explained here - Access Control Service Boundaries. As a microservice, access control is dependent on other services as well as some common libraries. The dependencies are mentioned here - Access Control Dependencies. A detailed technical specification on the broad internals of the service can be found here - Access Control Technical Specification.

Local Development with Access Control
Common setup instructions are present in the root README.md of the repository. Once you have successfully completed the above steps, please follow the steps mentioned in this document - Local Development with Access Control.

Onboarding new Resources and Permissions
Please follow the following guide to

Onboard new permissions and managed roles
Onboard new Resources in Resource Group
The proces to maintain permissions is explained here - Permissions Management

Building, Testing and Releasing Access Control
The details about building, testing and releasing Access Control is mentioned here - Building, Testing and Releasing Access Control.

Incident and Operations Playbook
The details about different dashboards and alerts are mentioned here - Access Control Ops Playbook
