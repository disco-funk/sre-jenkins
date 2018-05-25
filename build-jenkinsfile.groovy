final def label = "worker-${UUID.randomUUID().toString()}"
final def region = "eu-west-2"
final def imageName = "sre-camp18"

def releaseVersion = ""
def imageTag = ""

podTemplate(label: label,
        containers: [containerTemplate(name: 'docker', image: 'docker', command: 'cat', ttyEnabled: true)],
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
                    sh "docker build  --build-arg RELEASE_VERSION=${releaseVersion} -t ${imageTag} ."
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
    }
}