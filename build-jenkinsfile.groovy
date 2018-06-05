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
                        checkout([$class: 'GitSCM', branches: [[name: '*/master']], userRemoteConfigs: [[url: 'https://github.com/disco-funk/sre-microservice']]])
                        final def parsedJson = readJSON file: './version.json'
                        final def snapshotVersion = parsedJson.version
                        releaseVersion = snapshotVersion.replace('0-SNAPSHOT', env.BUILD_NUMBER)
                    },
                    "Install JDK": {
                        sh 'apk --update add openjdk8'
                    }
                )

            }

            stage('Build Binary') {
                sh "./gradlew -PreleaseVersion=${releaseVersion} build"
            }

            stage('Build Docker Image') {
                withCredentials([string(credentialsId: 'aws_account_number', variable: 'awsAccountNumber')]) {
                    imageTag = "${awsAccountNumber}.dkr.ecr.${region}.amazonaws.com/${imageName}:${releaseVersion}"
                    sh "docker build --build-arg RELEASE_VERSION=${releaseVersion} -t ${imageTag} ."
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
            stage('Helm push chart') {
                withCredentials([string(credentialsId: 'aws_account_number', variable: 'awsAccountNumber')]) {
                    withCredentials([string(credentialsId: 'sre-jenkins', variable: 'githubToken')]) {
                        sh "apk update && apk add git && helm init --upgrade"
                        sh "git config --global user.email 'man@themoon.com'"
                        sh "git config --global user.name 'Helm User'"
                        sh 'export SRE_BASE=$(pwd) && echo $SRE_BASE'
                        sh 'mkdir $SRE_BASE/sre-helm'
                        sh 'mkdir $SRE_BASE/sre-helm-repo'
                        sh 'cd $SRE_BASE/sre-helm'
                        git(
                            url: "https://disco-funk:${githubToken}@github.com/disco-funk/sre-helm.git",
                            credentialsId: 'github'
                        )
                        sh "ls -la"
                        sh 'sed -i \'s/1.0.0-SNAPSHOT/' + releaseVersion + '/g\' $SRE_BASE/sre-helm/sre/values.yaml'
                        sh 'helm package --version=' + releaseVersion + ' $SRE_BASE/sre-helm/sre'
                        sh 'cd $SRE_BASE/sre-helm-repo'
                        git(
                                url: "https://disco-funk:${githubToken}@github.com/disco-funk/sre-helm-repo.git",
                                credentialsId: 'github'
                        )
                        sh 'mv $SRE_BASE/sre-helm/sre-' + releaseVersion + '.tgz $SRE_BASE/sre-helm-repo/docs/'
                        sh 'git add $SRE_BASE/sre-helm-repo/docs/sre-' + releaseVersion + '.tgz'
                        sh "git commit -m 'Jenkins automated push - new helm package version ${releaseVersion}'"
                        sh "git push --set-upstream origin master"
                    }
                }
            }
        }
    }
}