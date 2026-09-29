pipeline {
    agent {
        label 'oracle-host-agent'
    }

    environment {
        GITHUB_CREDS = credentials('github-package-creds')
    }

    stages {
        stage('Build and Verify') {
            when {
                anyOf {
                    changeRequest()
                    branch 'main'
                }
            }
            stages {
                stage('Prepare Maven Settings') {
                    steps {
                        sh 'chmod +x ./mvnw'
                        sh '''
                            cat << EOF > settings.xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">
    <servers>
        <server>
            <id>github</id>
            <username>${GITHUB_CREDS_USR}</username>
            <password>${GITHUB_CREDS_PSW}</password>
        </server>
    </servers>
</settings>
EOF
                        '''
                    }
                }

                stage('Lint and Format') {
                    steps {
                        sh './mvnw spotless:check checkstyle:check -s settings.xml'
                    }
                }

                stage('Unit & Integration Tests') {
                    steps {
                        sh './mvnw clean test -s settings.xml'
                    }
                }

                stage('SonarQube Analysis') {
                    steps {
                        withSonarQubeEnv('SonarQube') {
                            withEnv(["SONAR_USER_HOME=${env.WORKSPACE}/.sonar"]) {
                                sh 'rm -rf "${SONAR_USER_HOME}/cache" || true'
                                sh './mvnw org.sonarsource.scanner.maven:sonar-maven-plugin:sonar -Dsonar.projectKey=triage-desk-command-service -s settings.xml'
                            }
                        }

                        timeout(time: 5, unit: 'MINUTES') {
                            waitForQualityGate abortPipeline: true
                        }
                    }
                }

                stage('Package Application') {
                    when {
                        branch 'main'
                    }
                    steps {
                        sh './mvnw package -DskipTests -s settings.xml'
                    }
                }

                stage('Deploy to Server') {
                    when {
                        branch 'main'
                    }
                    steps {
                        sh '''
                            mkdir -p /home/ubuntu/triage-desk/command-service
                            rsync -av --exclude='.git' --exclude='target' --exclude='.sonar' ./ /home/ubuntu/triage-desk/command-service/
                            rm -rf /home/ubuntu/triage-desk/command-service/.sonar || true
                            mkdir -p /home/ubuntu/triage-desk/command-service/target
                            cp target/command-service.jar /home/ubuntu/triage-desk/command-service/target/

                            cd /home/ubuntu/triage-desk/command-service
                            docker compose down
                            docker compose up -d --build
                        '''
                    }
                }
            }
            post {
                always {
                    sh 'rm -f settings.xml || true'
                }
            }
        }
    }

    post {
        always {
            junit allowEmptyResults: true, testResults: 'target/*-reports/*.xml'
        }
        success {
            echo 'Pipeline completed successfully!'
        }
        failure {
            echo 'Pipeline failed. Check the logs.'

            mail to: 'eyad.m.sharkawy@gmail.com',
            subject: "FAILED: Job '${env.JOB_NAME}' [Build #${env.BUILD_NUMBER}]",
            body: "Your Jenkins pipeline failed on branch '${env.BRANCH_NAME}'. Check the logs at ${env.BUILD_URL}"
        }
    }
}
