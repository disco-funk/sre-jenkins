final def label = "worker-${UUID.randomUUID().toString()}"
final def helmPkgVersion = "0.1.3"

podTemplate(label: label,
        containers: [
             containerTemplate(name: 'docker', image: 'docker', command: 'cat', ttyEnabled: true),
             containerTemplate(name: 'helm', image: 'lachlanevenson/k8s-helm:latest', command: 'cat', ttyEnabled: true),
             containerTemplate(name: 'kubectl', image: 'lachlanevenson/k8s-kubectl:v1.8.8', command: 'cat', ttyEnabled: true)
        ],
        volumes: [hostPathVolume(mountPath: '/var/run/docker.sock', hostPath: '/var/run/docker.sock')]) {

    node(label) {
        container('docker') {
            container('helm') {
                stage('Helm upgrade') {
                    withCredentials([string(credentialsId: 'aws_account_number', variable: 'awsAccountNumber')]) {
                        withAWS(credentials: 'aws_credentials') {
                            sh ecrLogin()
                            sh "apk update && apk add git && helm init --upgrade"
                            sh "git clone https://github.com/disco-funk/sre-helm.git && cd sre-helm"
                            sh 'helm package --version ' + helmPkgVersion + ' $(pwd)/sre-helm/sre'
                            sh 'helm upgrade --install sre $(pwd)/sre-' + helmPkgVersion + '.tgz'
                        }
                    }
                }
            }
        }
    }
}