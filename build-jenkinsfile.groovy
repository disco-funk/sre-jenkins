final def label = "worker-${UUID.randomUUID().toString()}"
final def region = "eu-west-2"
final def imageName = "sre-camp18"

def releaseVersion = ""
def imageTag = ""

podTemplate(label: label,
        containers: [containerTemplate(name: 'docker', image: 'docker', command: 'cat', ttyEnabled: true),
                     containerTemplate(name: 'helm', image: 'lachlanevenson/k8s-helm:latest', command: 'cat', ttyEnabled: true),
                     containerTemplate(name: 'kubectl', image: 'lachlanevenson/k8s-kubectl:v1.8.8', command: 'cat', ttyEnabled: true)],
        volumes: [hostPathVolume(mountPath: '/var/run/docker.sock', hostPath: '/var/run/docker.sock')]) {

    node(label) {
        container('docker') {
            stage('Initialise') {
                parallel(
                    "Checkout SCM": {
                        dir('sre-microservice') {
                            git url: 'https://github.com/disco-funk/sre-microservice'
                            final def parsedJson = readJSON file: './version.json'
                            final def snapshotVersion = parsedJson.version
                            releaseVersion = snapshotVersion.replace('0-SNAPSHOT', env.BUILD_NUMBER)
                        }
                    },
                    "Install JDK": {
                        sh 'apk --update add openjdk8'
                    }
                )

            }

            stage('Build Binary') {
                dir('sre-microservice') {
                    sh "./gradlew -PreleaseVersion=${releaseVersion} build"
                }
            }

            stage('Build Docker Image') {
                withCredentials([string(credentialsId: 'aws_account_number', variable: 'awsAccountNumber')]) {
                    dir('sre-microservice') {
                        imageTag = "${awsAccountNumber}.dkr.ecr.${region}.amazonaws.com/${imageName}:${releaseVersion}"
                        sh "docker build --build-arg RELEASE_VERSION=${releaseVersion} -t ${imageTag} ."
                    }
                }
            }

            stage('Push to AWS ECR') {
                withCredentials([string(credentialsId: 'aws_account_number', variable: 'awsAccountNumber')]) {
                    withAWS(credentials: 'aws_credentials') {
                        sh ecrLogin()
                        sh "docker push ${imageTag}"
                    }
                }
            }
        }

        container('helm') {
            stage('Helm Package Chart') {
                withCredentials([string(credentialsId: 'aws_account_number', variable: 'awsAccountNumber')]) {
                    withCredentials([string(credentialsId: 'sre-jenkins', variable: 'githubToken')]) {
                        sh "apk update && apk add git && helm init --upgrade"
                        sh 'git config --global user.email "man@themoon.com" && git config --global user.name "Helm User"'
                        dir('sre-helm') {
                            git url: "https://disco-funk:${githubToken}@github.com/disco-funk/sre-helm.git"
                            sh "sed -i 's/1.0.0-SNAPSHOT/${releaseVersion}/g' sre/values.yaml"
                            sh "helm package --version=${releaseVersion} ./sre"
                            sh "mv sre-${releaseVersion}.tgz /tmp/"
                        }

                        dir('sre-helm-repo') {
                            git url: "https://disco-funk:${githubToken}@github.com/disco-funk/sre-helm-repo.git"
                            sh "mv /tmp/sre-${releaseVersion}.tgz ./docs/"
                            sh "helm repo index --merge ./docs/"
                            sh "git remote -v && ls -la && git status"
                            sh "git add docs/* && git commit -am 'Jenkins automated push - new helm package version ${releaseVersion}' && git push -u origin master"
                        }
                    }
                }
            }
        }
    }
}