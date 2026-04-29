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

        SONAR_PROJECT_KEY  = 'Eureka_Server'
        SONAR_PROJECT_NAME = 'Eureka_Server'
    }

    stages {

        stage('Clean Workspace') {
            steps {
                cleanWs()
            }
        }

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Trigger Info') {
            steps {
                echo "Build triggered by: ${currentBuild.getBuildCauses()}"
            }
        }

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

        /* ================= BUILD + TEST ================= */
        stage('Build & Test (with Coverage)') {
            steps {
                // Step into the folder where the pom.xml actually lives
                dir('service-registry') {
                    catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                        sh '''
                            echo "===== BUILD + TEST ====="
                            mvn clean verify \
                            -Deureka.client.enabled=false \
                            -Dspring.cloud.discovery.enabled=false
                        '''
                    }
                }
            }
        }

        /* ================= CHECK JACOCO ================= */
        stage('Check JaCoCo Report') {
            steps {
                dir('service-registry') {
                    sh '''
                        echo "===== CHECKING JACOCO ====="
                        ls -la target/site/jacoco || echo "JaCoCo NOT FOUND"
                    '''
                }
            }
        }

        /* ================= SONAR ================= */
        stage('SonarQube Analysis') {
            steps {
                dir('service-registry') {
                    withSonarQubeEnv('SonarQube2') {
                        withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                            sh '''
                                echo "===== SONAR ANALYSIS ====="
                                mvn sonar:sonar \
                                -Dsonar.projectKey=$SONAR_PROJECT_KEY \
                                -Dsonar.projectName=$SONAR_PROJECT_NAME \
                                -Dsonar.login=$SONAR_TOKEN \
                                -Dsonar.java.binaries=target/classes \
                                -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
                            '''
                        }
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
                dir('service-registry') {
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
        }

        /* ================= ARCHIVE ================= */
        stage('Archive Reports') {
            steps {
                // Pointing specifically to the files inside the subfolder
                archiveArtifacts artifacts: 'service-registry/dependency-check-report.csv',
                                 fingerprint: true
                                 
                junit allowEmptyResults: true, 
                      testResults: 'service-registry/target/surefire-reports/*.xml'
            }
        }
    }

    post {
        success {
            echo 'SUCCESS: Build + Tests + Sonar + OWASP completed'
        }
        unstable {
            echo 'UNSTABLE: Tests failed or Quality Gate not passed'
        }
        failure {
            echo 'FAILED: Check logs'
        }
        always {
            echo 'Pipeline execution finished'
        }
    }
}
