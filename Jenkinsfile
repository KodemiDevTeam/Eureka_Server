pipeline {

    agent any

    options {
        disableConcurrentBuilds()
        timeout(time: 1, unit: 'HOURS')
    }

    environment {
        JAVA_HOME = '/opt/java/openjdk'
        MAVEN_HOME = '/usr/share/maven'
        PATH = "/opt/java/openjdk/bin:/usr/share/maven/bin:/usr/bin:/bin:/usr/local/bin"

        SONAR_PROJECT_KEY  = 'UserService_Dev'
        SONAR_PROJECT_NAME = 'UserService_Dev'
    }

    stages {

        stage('Clean Workspace') {
            steps {
                cleanWs()
            }
        }

        /* ================= CHECKOUT ================= */

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        /* ================= TRIGGER INFO ================= */

        stage('Trigger Info') {
            steps {
                echo "Build triggered by: ${currentBuild.getBuildCauses()}"
            }
        }

        /* ================= DEBUG ================= */

        stage('Debug Workspace') {
            steps {
                sh '''
                    echo "===== WORKSPACE DEBUG ====="
                    pwd
                    ls -la
                    find . -name pom.xml
                '''
            }
        }

        /* ================= BUILD ================= */

        stage('Build (No Tests)') {
            steps {
                sh '''
                    echo "===== BUILD WITHOUT TESTS ====="
                    
                    mvn clean install \
                    -Dmaven.test.skip=true \
                    -Deureka.client.enabled=false \
                    -Dspring.cloud.discovery.enabled=false
                '''
            }
        }

        /* ================= SONAR ================= */

        stage('SonarQube Analysis (No Tests)') {
            steps {
                withSonarQubeEnv('SonarQube2') {
                    withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                        sh '''
                            echo "===== SONAR ANALYSIS ====="

                            mvn sonar:sonar \
                            -Dsonar.projectKey=$SONAR_PROJECT_KEY \
                            -Dsonar.projectName=$SONAR_PROJECT_NAME \
                            -Dsonar.login=$SONAR_TOKEN \
                            -Dsonar.coverage.exclusions=** \
                            -Dsonar.tests= \
                            -Dsonar.test.exclusions=**
                        '''
                    }
                }
            }
        }

        /* ================= QUALITY GATE ================= */

        stage('Quality Gate') {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: false
                }
            }
        }

        /* ================= SECURITY ================= */

        stage('OWASP Dependency Check') {
            steps {
                withCredentials([string(credentialsId: 'nvd-api-key', variable: 'NVD_KEY')]) {

                    sh 'echo "===== RUNNING OWASP CHECK ====="'

                    dependencyCheck(
                        additionalArguments: "--nvdApiKey ${NVD_KEY} --format CSV --out . --disableOssIndex",
                        odcInstallation: 'Default'
                    )
                }

                dependencyCheckPublisher pattern: 'dependency-check-report.csv'
            }
        }

        /* ================= ARCHIVE ================= */

        stage('Archive Reports') {
            steps {
                archiveArtifacts artifacts: 'dependency-check-report.csv',
                                 fingerprint: true
            }
        }
    }

    post {
        success {
            echo 'SUCCESS: Build + Sonar + OWASP completed (Webhook Triggered)'
        }
        failure {
            echo 'FAILED: Check logs'
        }        
        always {
            echo 'Pipeline execution finished'
        }
    }
}
