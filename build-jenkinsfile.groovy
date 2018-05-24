node {
    stage('Checkout') {
        checkout([$class: 'GitSCM', branches: [[name: '*/master']], userRemoteConfigs: [[url: 'https://github.com/disco-funk/sre-microservice']]])
    }

    stage('Build') {
        sh './gradlew build'
        sh 'ls -la'
    }
}