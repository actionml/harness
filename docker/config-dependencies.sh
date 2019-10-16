#!/bin/sh

# == Install docker composer
apk add --no-cache --update py-pip python-dev libffi-dev openssl-dev gcc libc-dev make curl curl-dev
pip install docker-compose==1.12.0
docker-compose version 

# == Install Kubectl
curl -LO https://storage.googleapis.com/kubernetes-release/release/`curl -s https://storage.googleapis.com/kubernetes-release/release/stable.txt`/bin/linux/amd64/kubectl
chmod +x ./kubectl
mv ./kubectl /usr/local/bin/kubectl


# == Configure K8s Cluster
# kubectl config set-credentials admin --username=${K8S_ADMIN_USER} --password=${K8S_ADMIN_PASS}
# kubectl config set-cluster k8s.sitespect --insecure-skip-tls-verify=true --server=${K8S_SERVER}
# kubectl config set-context k8s.sitespect --user=admin --namespace=default --cluster=k8s.sitespect
# kubectl config use-context k8s.sitespect

kubectl config set-credentials admin --username=${K8S_ADMIN_USER_DEVOPS} --password=${K8S_ADMIN_PASS_DEVOPS}
kubectl config set-cluster devops.dev --insecure-skip-tls-verify=true --server=${K8S_SERVER_DEVOPS}
kubectl config set-context devops.dev --user=admin --namespace=default --cluster=devops.dev
kubectl config use-context devops.dev


