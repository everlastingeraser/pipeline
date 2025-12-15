pipeline {
    agent any

    parameters {
        string(name: 'branchName', defaultValue: 'main', description: 'Branch name to build from')
        string(name: 'serviceName', defaultValue: 'demoapp', description: 'Service (module) name')
    }

    tools {
        jdk 'jdk17'
    }

    stages {
        stage('Environment Check') {
            steps {
                sh """
                    java -version
                    mvn -version
                    pwd
                    echo "JAVA_HOME: ${JAVA_HOME}"
                """
            }
        }

        stage('git clone') {
            steps {
                git branch: params.branchName, url: 'https://github.com/everlastingeraser/demo-app.git'
            }
        }

        stage('Build service') {
            steps {
                sh "mvn -B -DskipTests clean package"
            }
        }


        stage('SonarQube Analysis') {
            environment {
                SONAR_HOST = "http://sonarqube:9000"
                SONAR_TOKEN = credentials('sonar_token')
            }
            steps {
                sh "mvn sonar:sonar -Dsonar.projectKey=Demoapp -Dsonar.host.url=$SONAR_HOST -Dsonar.token=$SONAR_TOKEN -Dsonar.exclusions=**/*.java"
            }
        }

        stage('Run test') {
            steps {
                sh "mvn test"
            }
        }

        stage('Generate Allure report') {
            steps {
                allure includeProperties: false, jdk: '', resultPolicy: 'LEAVE_AS_IS', results: [[path: "target/allure-results"]]
            }
        }

        stage('Upload to Nexus') {
            environment {
                NEXUS_PROTOCOL = 'http'
                NEXUS_URL = 'nexus:8081'
                NEXUS_CREDENTIALS_ID = 'nexus'
                GROUP_ID = 'com.example'
                NEXUS_REPO = 'maven-releases'
            }
            steps {
                script {
                    def pom = readMavenPom file: 'pom.xml'
                    def artifactId = pom.artifactId
                    def version = pom.version
                    
                    def jarFile = findFiles(glob: "target/${artifactId}-${version}.jar")[0]
                    
                    def artifactVersion = "1.0.${env.BUILD_NUMBER}"

                    nexusArtifactUploader(
                            nexusVersion: 'nexus3',
                            protocol: env.NEXUS_PROTOCOL,
                            nexusUrl: env.NEXUS_URL,
                            groupId: env.GROUP_ID,
                            version: artifactVersion,
                            credentialsId: env.NEXUS_CREDENTIALS_ID,
                            repository: env.NEXUS_REPO,
                            artifacts: [
                                    [
                                            artifactId: artifactId, classifier: '', file: jarFile.path, type: 'jar'
                                    ]
                            ]
                    )

                    def groupIdUrl = env.GROUP_ID.replace(".", "/")
		    def distUrl = "${env.NEXUS_PROTOCOL}://${env.NEXUS_URL}/repository/${env.NEXUS_REPO}/${groupIdUrl}/${artifactId}/${artifactVersion}/${artifactId}-${artifactVersion}.jar"

                    print(
                            """
                            ==========================
                            distr url: \n${distUrl}
                            ==========================
                        """
                    )
                }
            }
        }
    }

    post {
        always {
            script {
                sh "ls"
                deleteDir()
                sh "ls"
            }
        }
    }
}
