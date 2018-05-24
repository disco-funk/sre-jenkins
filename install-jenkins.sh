#!/usr/bin/env bash

helm install --name sre-jenkins -f jenkins.yaml stable/jenkins
kubectl get pods -w