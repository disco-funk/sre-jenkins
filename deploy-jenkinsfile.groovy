final def label = "worker-${UUID.randomUUID().toString()}"
final def region = "eu-west-2"
final def imageName = "sre-camp18"

def releaseVersion = env.SRE_MICROSERVICE_VERSION
def helmPackageVersion = "0.1.${env.BUILD_NUMBER}"
def imageTag = ""

podTemplate(label: label,
        containers: [
             containerTemplate(name: 'docker', image: 'docker', command: 'cat', ttyEnabled: true),
             containerTemplate(name: 'helm', image: 'lachlanevenson/k8s-helm:latest', command: 'cat', ttyEnabled: true),
             containerTemplate(name: 'kubectl', image: 'lachlanevenson/k8s-kubectl:v1.8.8', command: 'cat', ttyEnabled: true)
        ],
        volumes: [hostPathVolume(mountPath: '/var/run/docker.sock', hostPath: '/var/run/docker.sock')]) {

    node(label) {
        container('docker') {
            stage('Initialise') {
                withCredentials([string(credentialsId: 'aws_account_number', variable: 'awsAccountNumber')]) {
                    withAWS(credentials: 'aws_credentials') {
                        imageTag = "${awsAccountNumber}.dkr.ecr.${region}.amazonaws.com/${imageName}:${releaseVersion}"

                        sh ecrLogin()
                        sh "docker pull ${imageTag}"
                    }
                }
            }
        }

        container('helm') {
            stage('Helm upgrade') {
                withCredentials([string(credentialsId: 'aws_account_number', variable: 'awsAccountNumber')]) {
                    sh "apk update && apk add git && helm init --upgrade"
                    sh "git clone https://github.com/disco-funk/sre-helm.git && cd sre-helm"
                    sh 'sed -i \'s/1.0.0-SNAPSHOT/' + releaseVersion + '/g\' $(pwd)/sre-helm/sre/values.yaml'
                    sh 'helm package --version=' + helmPackageVersion + ' $(pwd)/sre-helm/sre'
                    sh 'helm upgrade --install sre $(pwd)/sre-' + helmPackageVersion + '.tgz'
                }
            }
        }
    }
}