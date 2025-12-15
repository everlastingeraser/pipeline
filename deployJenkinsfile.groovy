pipeline {
    agent any

    parameters {
        string(name: 'nexusArtifactUrl', defaultValue: '', description: 'URL to get artifact from nexus')
    }

    stages {
        stage('Download from Nexus') {
            environment {
                NEXUS_CREDS = credentials('nexus')
            }
            steps {
                sh "wget --user=${NEXUS_CREDS_USR} --password=${NEXUS_CREDS_PSW} -O distr.jar ${params.nexusArtifactUrl}"
            }

        }

        stage('Deploy') {
            steps {
                sshPublisher(publishers: [
                        sshPublisherDesc(
                                configName: 'prod',
                                transfers: [
                                        sshTransfer(
                                                sourceFiles: 'distr.jar',
                                                remoteDirectory: '/home/admin',
                                                execCommand: 'nohup java -jar distr.jar &'
                                        )
                                ]
                        )
                ])
            }
        }
    }

    post {
        always {
            script {
                deleteDir()
            }
        }
    }
}
