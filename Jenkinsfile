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

        DEPLOY_HOST = '35.159.11.66'
        DEPLOY_USER = 'ubuntu'
        DEPLOY_DIR  = '/opt/eureka'
        APP_PORT    = '8761'
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

                        find target -type f \
                            \\( \
                                -name "jacoco.xml" \
                                -o -name "jacoco.csv" \
                                -o -name "jacoco.exec" \
                                -o -path "*/jacoco/index.html" \
                            \\) \
                            -print || true

                        if [ -f target/site/jacoco/jacoco.xml ]; then

                            echo "SUCCESS: JaCoCo XML report found"
                            ls -lh target/site/jacoco/jacoco.xml

                        else

                            echo "ERROR: JaCoCo XML report was not generated"

                            find target -maxdepth 5 -type f -print || true

                            exit 1
                        fi

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

                    withSonarQubeEnv('sonarscanner') {

                        withCredentials([
                            string(
                                credentialsId: 'sonartk',
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

        

        stage('Archive Reports') {
            steps {

                archiveArtifacts(
                    artifacts: 'service-registry/dependency-check-report.csv, service-registry/target/site/jacoco/**, service-registry/target/surefire-reports/**',
                    fingerprint: true,
                    allowEmptyArchive: true
                )
            }
        }

        stage('Prepare Deployment Artifact') {
            steps {

                dir('service-registry') {

                    sh '''
                        echo "======================================"
                        echo " PREPARING DEPLOYMENT ARTIFACT"
                        echo "======================================"

                        echo "===== AVAILABLE JARS ====="
                        find target -maxdepth 1 -type f -name "*.jar" -print

                        JAR_FILE=$(find target -maxdepth 1 \
                            -type f \
                            -name "*.jar" \
                            ! -name "*.original" \
                            | head -n 1)

                        if [ -z "$JAR_FILE" ]; then
                            echo "ERROR: No deployable JAR found"
                            exit 1
                        fi

                        echo "Selected JAR: $JAR_FILE"

                        cp "$JAR_FILE" target/eureka-server.jar

                        ls -lh target/eureka-server.jar
                    '''
                }
            }
        }

        stage('Deploy to AWS EC2') {
            steps {

                sshagent(credentials: ['eureka-deploy-key']) {

                    sh '''
                        echo "======================================"
                        echo " DEPLOYING EUREKA TO AWS EC2"
                        echo "======================================"

                        scp \
                            -o StrictHostKeyChecking=no \
                            service-registry/target/eureka-server.jar \
                            $DEPLOY_USER@$DEPLOY_HOST:$DEPLOY_DIR/eureka-server.jar
                    '''
                }
            }
        }

        stage('Start Eureka Server') {
            steps {

                sshagent(credentials: ['eureka-deploy-key']) {

                    sh '''
                        echo "======================================"
                        echo " STARTING EUREKA SERVER"
                        echo "======================================"

                        ssh \
                            -o StrictHostKeyChecking=no \
                            $DEPLOY_USER@$DEPLOY_HOST \
                            "
                                cd $DEPLOY_DIR

                                if [ -f eureka.pid ]; then
                                    OLD_PID=\\$(cat eureka.pid)

                                    if kill -0 \\$OLD_PID 2>/dev/null; then
                                        echo Stopping previous Eureka process
                                        kill \\$OLD_PID || true
                                        sleep 5
                                    fi
                                fi

                                nohup java -jar eureka-server.jar \
                                    > eureka.log 2>&1 < /dev/null &

                                echo \\$! > eureka.pid

                                echo Eureka PID:
                                cat eureka.pid
                            "
                    '''
                }
            }
        }

        stage('Deployment Health Check') {
            steps {

                sshagent(credentials: ['eureka-deploy-key']) {

                    sh '''
                        echo "======================================"
                        echo " EUREKA HEALTH CHECK"
                        echo "======================================"

                        echo "Waiting for Eureka startup..."

                        sleep 30

                        ssh \
                            -o StrictHostKeyChecking=no \
                            $DEPLOY_USER@$DEPLOY_HOST \
                            "
                                curl --fail --silent \
                                http://localhost:$APP_PORT \
                                > /dev/null
                            "

                        echo "SUCCESS: Eureka Server is responding"
                    '''
                }
            }
        }
    }

    post {

        success {
            echo 'SUCCESS: CI pipeline completed and Eureka Server deployed to AWS EC2'
        }

        unstable {
            echo 'UNSTABLE: Review tests, coverage, Quality Gate, or security findings'
        }

        failure {
            echo 'FAILED: Check the failed stage logs'
        }

        always {
            echo 'Pipeline execution finished'
        }
    }
}
