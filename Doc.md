# Harness Deployment

## Requirements
- kubernetes cluster
- CircleCi Project

To learn how to set up a cluster, see:
[Setup K8s Cluster](https://github.com/actionml/k8s-harness-private/blob/feature/ss/cluster_startup.md)

## Harness
To deploy harness to kubernetes cluster, go to the project settings in circleci and add the following variables:

* **DOCKER_PASSWORD** 
Password to login Docker Hub

* **DOCKER_USERNAME** 
User to login Docker Hub.

* **K8S_ADMIN_PASS**
K8s cluster administrator user.
* **K8S_ADMIN_USER**
K8s cluster administrator password.
* **K8S_SERVER**
Kubernetes cluster api endpoint.