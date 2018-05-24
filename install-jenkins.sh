#!/usr/bin/env bash

helm delete sre-jenkins --purge
helm install --name sre-jenkins -f jenkins.yaml stable/jenkins
kubectl get pods -w