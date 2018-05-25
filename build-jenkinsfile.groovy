final def label = "worker-${UUID.randomUUID().toString()}"
final def version = "latest"
final def region = "eu-west-2"
final def imageName = "sre-camp18"

podTemplate(label: label,
        containers: [containerTemplate(name: 'docker', image: 'docker', command: 'cat', ttyEnabled: true)],
        volumes: [hostPathVolume(mountPath: '/var/run/docker.sock', hostPath: '/var/run/docker.sock')]) {

    node(label) {
        container('docker') {
            stage('Checkout') {
                parallel(
                    checkout: {
                        checkout([$class: 'GitSCM', branches: [[name: '*/master']], userRemoteConfigs: [[url: 'https://github.com/disco-funk/sre-microservice']]])
                    },
                    installJDK: {
                        sh 'apk --update add openjdk8'
                    }
                )

            }

            stage('Build Binary') {
                sh './gradlew build'
            }

            stage('Push to AWS ECR') {
                withCredentials([string(credentialsId: 'aws_account_number', variable: 'awsAccountNumber')]) {
                    def imageTag = "${awsAccountNumber}.dkr.ecr.${region}.amazonaws.com/${imageName}:${version}"
                    sh "docker build -t ${imageTag} ."
                    sh "docker image ls"

                    withAWS(credentials: 'aws_credentials') {
                        ecrLogin()
                        sh "docker push ${imageTag}"
                    }
                }
            }
        }
    }
}