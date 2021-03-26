#!/bin/sh

# == Install docker composer
apk add --no-cache --update py-pip python3-dev libffi-dev openssl-dev gcc git libc-dev make curl curl-dev
pip install docker-compose==1.12.0
docker-compose version 

# == Install Kubectl
curl -LO https://storage.googleapis.com/kubernetes-release/release/`curl -s https://storage.googleapis.com/kubernetes-release/release/stable.txt`/bin/linux/amd64/kubectl
chmod +x ./kubectl
mv ./kubectl /usr/local/bin/kubectl

# == Configure K8s Cluster
kubectl config set-credentials admin --username=${K8S_ADMIN_USER} --password=${K8S_ADMIN_PASS}
kubectl config set-cluster k8s.cluster --insecure-skip-tls-verify=true --server=${K8S_SERVER}
kubectl config set-context k8s.cluster --user=admin --namespace=default --cluster=k8s.cluster
kubectl config use-context k8s.cluster
