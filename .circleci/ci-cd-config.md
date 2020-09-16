# Harness Config

## Requirements
- Repository Account like Github, Bitbucket or others.
- CircleCi Account.

To learn how to set up a cluster, see:
[Setup K8s Cluster](https://github.com/actionml/k8s-harness-private/blob/feature/ss/cluster_startup.md)

## Github
Clone this repository and push to your own repo.

## CircleCi

After create a account on CircleCi,
Go to *Workflows* and click `Add projects`

![1](https://user-images.githubusercontent.com/17029741/68023953-8560aa00-fc87-11e9-9db8-a204687b9220.png)

Select your project and click `Set Up Project`.

![2](https://user-images.githubusercontent.com/17029741/68023984-96112000-fc87-11e9-8694-95dc5f047888.png)

Follow the instructions on the next screen and click `Start building` to set CircleCi to listen updates on you work.

![3](https://user-images.githubusercontent.com/17029741/68024104-eab49b00-fc87-11e9-8153-119172cd1424.png)

After the first build, your work will be set on CircleCi.

![4](https://user-images.githubusercontent.com/17029741/68024271-657db600-fc88-11e9-96d5-fedba486d60c.png)

## Harness
In order to create the CI/CD configuration for your harness you will need to have your own  repository on docker hub (private or public). It is needed to push the image that is going to be used.
After creating your repoository in docker hub, follow these steps:

Click on setting icon on the right top of the page and go to *Project Settings*.
Click `Environment Variables` in the left and `Add Variables` in the right page and add the following variables:

* **DOCKER_PASSWORD** 
Password to login to Docker Hub

* **DOCKER_USERNAME** 
User to login to Docker Hub.

* **DOCKER_REPOSITORY** 
Image Repository on Docker Hub eg.: `actionml/harness`

Change your previous config.yml file with this [config.yml](https://github.com/actionml/harness/blob/develop/.circleci/config.yml) file.

Commit and push again.

Now your circleci is ready to watch changes in your repository, build and push new image version.