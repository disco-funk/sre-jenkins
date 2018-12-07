#!/usr/bin/env bash

helm delete sre-jenkins --purge
helm install --name sre-jenkins -f jenkins.yaml --namespace jenkins stable/jenkins
kubectl get pods -w --namespace jenkins