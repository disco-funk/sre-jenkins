def label = "worker-${UUID.randomUUID().toString()}"

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

            stage('Build') {
                sh './gradlew build'
            }

            stage('Docker Image') {
                sh "docker build -t sre-camp18 ."
                sh "docker tag sre-camp18:latest 870594606895.dkr.ecr.eu-west-2.amazonaws.com/sre-camp18:latest"
                sh "docker image ls"
            }
        }
    }
}