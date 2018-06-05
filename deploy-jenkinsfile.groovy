final def label = "worker-${UUID.randomUUID().toString()}"
final def region = "eu-west-2"
final def imageName = "sre-camp18"

def releaseVersion = "1.0.3"
def imageTag = ""

podTemplate(label: label,
        containers: [
             containerTemplate(name: 'docker', image: 'docker', command: 'cat', ttyEnabled: true),
             containerTemplate(name: 'helm', image: 'lachlanevenson/k8s-helm:latest', command: 'cat', ttyEnabled: true)
        ],
        volumes: [hostPathVolume(mountPath: '/var/run/docker.sock', hostPath: '/var/run/docker.sock')]) {

    node(label) {
        container('docker') {
            stage('Initialise') {
                parallel(
                    "Pull from AWS ECR" : {
                        withCredentials([string(credentialsId: 'aws_account_number', variable: 'awsAccountNumber')]) {
                            withAWS(credentials: 'aws_credentials') {
                                imageTag = "${awsAccountNumber}.dkr.ecr.${region}.amazonaws.com/${imageName}:${releaseVersion}"

                                sh ecrLogin()
                                sh "docker pull ${imageTag}"
                                sh "docker image ls"
                            }
                        }
                    },
                    "Install Git" : {
                        sh "apk update && apk add git"
                    }
                )
            }
        }

        container('helm') {
            sh "helm init"
            sh "git clone https://github.com/disco-funk/sre-helm"
            sh "ls -la"
        }
    }
}