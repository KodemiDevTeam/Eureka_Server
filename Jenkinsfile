pipeline {

    agent any

    tools {
        maven 'Maven3'
    }

    options {
        disableConcurrentBuilds()
        timeout(time: 1, unit: 'HOURS')
    }

    environment {
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

        stage('Environment Check') {
            steps {
                sh '''
                    echo "======================================"
                    echo " ENVIRONMENT CHECK"
                    echo "======================================"

                    echo "===== JAVA ====="
                    java -version

                    echo "===== MAVEN ====="
                    mvn -version

                    echo "===== WORKSPACE ====="
                    pwd
                    ls -la

                    echo "===== POM FILES ====="
                    find . -name pom.xml -print
                '''
            }
        }

        stage('Build, Test & Coverage') {
            steps {

                dir('service-registry') {

                    script {

                        def buildStatus = sh(
                            script: '''
                                echo "======================================"
                                echo " BUILD + TEST + COVERAGE"
                                echo "======================================"

                                mvn -B clean verify \
                                    -Dmaven.test.failure.ignore=true \
                                    -Deureka.client.enabled=false \
                                    -Dspring.cloud.discovery.enabled=false
                            ''',
                            returnStatus: true
                        )

                        if (buildStatus != 0) {
                            currentBuild.result = 'UNSTABLE'
                            echo 'Maven returned a non-zero status. Continuing for report analysis.'
                        } else {
                            echo 'Maven build lifecycle completed successfully.'
                        }
                    }
                }
            }

            post {
                always {
                    junit(
                        testResults: 'service-registry/target/surefire-reports/*.xml',
                        allowEmptyResults: true
                    )
                }
            }
        }

        stage('Verify JaCoCo Report') {
            steps {

                dir('service-registry') {

                    sh '''
                        echo "======================================"
                        echo " VERIFY JACOCO REPORT"
                        echo "======================================"

                        echo "===== SEARCHING FOR JACOCO FILES ====="

                        find target -type f \
                            \\( \
                                -name "jacoco.xml" \
                                -o -name "jacoco.csv" \
                                -o -name "jacoco.exec" \
                                -o -path "*/jacoco/index.html" \
                            \\) \
                            -print || true

                        echo "===== CHECKING XML REPORT ====="

                        if [ -f target/site/jacoco/jacoco.xml ]; then

                            echo "SUCCESS: JaCoCo XML report found"
                            ls -lh target/site/jacoco/jacoco.xml

                        else

                            echo "ERROR: JaCoCo XML report was not generated"

                            echo "===== TARGET DIRECTORY CONTENT ====="
                            find target -maxdepth 5 -type f -print || true

                            exit 1

                        fi

                        echo "===== CHECKING HTML REPORT ====="

                        if [ -f target/site/jacoco/index.html ]; then

                            echo "SUCCESS: JaCoCo HTML report found"

                        else

                            echo "WARNING: JaCoCo HTML report not found"

                        fi
                    '''
                }
            }
        }

        stage('SonarQube Analysis') {
            steps {

                dir('service-registry') {

                    withSonarQubeEnv('SonarQube2') {

                        withCredentials([
                            string(
                                credentialsId: 'sonar-token',
                                variable: 'SONAR_TOKEN'
                            )
                        ]) {

                            sh '''
                                echo "======================================"
                                echo " SONARQUBE ANALYSIS"
                                echo "======================================"

                                mvn -B sonar:sonar \
                                    -Dsonar.projectKey=$SONAR_PROJECT_KEY \
                                    -Dsonar.projectName=$SONAR_PROJECT_NAME \
                                    -Dsonar.token=$SONAR_TOKEN \
                                    -Dsonar.java.binaries=target/classes \
                                    -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
                            '''
                        }
                    }
                }
            }
        }

        stage('Quality Gate') {
            steps {

                script {

                    try {

                        timeout(time: 10, unit: 'MINUTES') {

                            def qg = waitForQualityGate(
                                abortPipeline: false
                            )

                            echo "Quality Gate Status: ${qg.status}"

                            if (qg.status != 'OK') {
                                currentBuild.result = 'UNSTABLE'
                                echo 'Quality Gate did not pass.'
                            } else {
                                echo 'Quality Gate passed.'
                            }
                        }

                    } catch (Exception e) {

                        echo "Quality Gate check failed or timed out: ${e.message}"

                        currentBuild.result = 'UNSTABLE'
                    }
                }
            }
        }

        stage('OWASP Dependency Check') {
            steps {

                dir('service-registry') {

                    withCredentials([
                        string(
                            credentialsId: 'nvd-api-key',
                            variable: 'NVD_KEY'
                        )
                    ]) {

                        echo '===== RUNNING OWASP DEPENDENCY CHECK ====='

                        dependencyCheck(
                            additionalArguments: "--nvdApiKey ${NVD_KEY} --format CSV --out . --disableOssIndex",
                            odcInstallation: 'Default'
                        )
                    }

                    dependencyCheckPublisher(
                        pattern: 'dependency-check-report.csv'
                    )
                }
            }
        }

        stage('Archive Reports') {
            steps {

                archiveArtifacts(
                    artifacts: 'service-registry/dependency-check-report.csv, service-registry/target/site/jacoco/**, service-registry/target/surefire-reports/**',
                    fingerprint: true,
                    allowEmptyArchive: true
                )
            }
        }
    }

    post {

        success {
            echo 'SUCCESS: Build + Tests + Coverage + Sonar + OWASP completed'
        }

        unstable {
            echo 'UNSTABLE: Review tests, coverage, Quality Gate, or security findings'
        }

        failure {
            echo 'FAILED: Check the stage logs for the root cause'
        }

        always {
            echo 'Pipeline execution finished'
        }
    }
}
