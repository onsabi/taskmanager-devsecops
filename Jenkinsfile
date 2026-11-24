// =====================================================================
// Pipeline DevSecOps : GitHub -> Jenkins -> Maven -> SonarQube
//                      -> Dependency-Check -> Docker -> Trivy
//                      -> Docker Hub -> Kubernetes (Minikube) -> OWASP ZAP
//
// Credentials a creer dans Jenkins (Manage Jenkins > Credentials) :
//   - dockerhub-creds   : Username with password (Docker Hub)
//   - sonar-token       : Secret text (token SonarQube)
//   - nvd-api-key       : Secret text (cle NVD, optionnelle mais recommandee)
//   - kubeconfig        : Secret file (kubeconfig de Minikube)
// Serveur SonarQube configure dans Jenkins sous le nom "SonarQube".
// =====================================================================

pipeline {
    agent any

    tools {
        maven 'Maven-3.9'
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 60, unit: 'MINUTES')
    }

    triggers {
        // Declenche le pipeline a chaque push GitHub (webhook configure sur le depot)
        githubPush()
    }

    environment {
        APP_NAME       = 'taskmanager'
        APP_VERSION    = '1.0.0'
        DOCKERHUB_USER = 'onsabi'          // A remplacer par votre identifiant Docker Hub
        IMAGE_NAME     = "${DOCKERHUB_USER}/${APP_NAME}"
        IMAGE_TAG      = "${APP_VERSION}-${BUILD_NUMBER}"
        FULL_IMAGE     = "${IMAGE_NAME}:${IMAGE_TAG}"
        REPORTS_DIR    = 'reports'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
                sh 'mkdir -p ${REPORTS_DIR}'
            }
        }

        stage('Build & Tests unitaires') {
            steps {
                sh 'mvn -B clean verify'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
                    jacoco(execPattern: 'target/jacoco.exec', classPattern: 'target/classes', sourcePattern: 'src/main/java')
                }
            }
        }

        stage('SonarQube - Analyse du code (SAST)') {
            steps {
                withSonarQubeEnv('SonarQube') {
                    withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                        sh 'mvn -B sonar:sonar -Dsonar.token=$SONAR_TOKEN'
                    }
                }
            }
        }

        stage('Quality Gate') {
            steps {
                // Bloque le pipeline si le Quality Gate SonarQube echoue
                timeout(time: 10, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('OWASP Dependency-Check') {
            steps {
                withCredentials([string(credentialsId: 'nvd-api-key', variable: 'NVD_API_KEY')]) {
                    // failBuildOnCVSS=7 est configure dans le pom.xml : CVE >= 7 fait echouer le build
                    sh 'mvn -B org.owasp:dependency-check-maven:check'
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'target/dependency-check-report.*', allowEmptyArchive: true
                    publishHTML(target: [
                        allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true,
                        reportDir: 'target', reportFiles: 'dependency-check-report.html',
                        reportName: 'OWASP Dependency-Check'
                    ])
                }
            }
        }

        stage('Construction Docker') {
            steps {
                sh 'docker build -t ${FULL_IMAGE} -t ${IMAGE_NAME}:latest .'
            }
        }

        stage('Trivy - Scan de l\'image') {
            steps {
                script {
                    // Rapport complet (HIGH + CRITICAL), archive pour le rapport technique
                    sh """
                        docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
                          -v \$PWD/${REPORTS_DIR}:/out aquasec/trivy:0.56.0 image \
                          --severity HIGH,CRITICAL --ignore-unfixed \
                          --format table -o /out/trivy-report.txt ${FULL_IMAGE} || true
                    """
                    // Politique : toute vulnerabilite CRITICAL corrigeable bloque la publication
                    sh """
                        docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
                          aquasec/trivy:0.56.0 image \
                          --severity CRITICAL --ignore-unfixed --exit-code 1 ${FULL_IMAGE}
                    """
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: "${REPORTS_DIR}/trivy-report.txt", allowEmptyArchive: true
                }
            }
        }

        stage('Publication Docker Hub') {
            when {
                anyOf { branch 'main'; branch 'develop' }
            }
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub-creds',
                                                  usernameVariable: 'DH_USER',
                                                  passwordVariable: 'DH_PASS')]) {
                    sh 'echo $DH_PASS | docker login -u $DH_USER --password-stdin'
                    sh 'docker push ${FULL_IMAGE}'
                    sh 'docker push ${IMAGE_NAME}:latest'
                }
            }
            post {
                always {
                    sh 'docker logout || true'
                }
            }
        }

        stage('Deploiement Kubernetes (Minikube)') {
            when {
                branch 'main'
            }
            steps {
                withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG')]) {
                    sh '''
                        sed "s|DOCKER_IMAGE_PLACEHOLDER|${FULL_IMAGE}|" k8s/deployment.yaml > k8s/deployment.rendered.yaml
                        kubectl apply -f k8s/deployment.rendered.yaml
                        kubectl apply -f k8s/service.yaml
                        kubectl rollout status deployment/taskmanager --timeout=180s
                        kubectl get pods -l app=taskmanager
                    '''
                }
            }
        }

        stage('OWASP ZAP - Test dynamique (DAST)') {
            when {
                branch 'main'
            }
            steps {
                withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG')]) {
                    script {
                        // URL de l'application deployee dans Minikube
                        env.APP_URL = sh(script: 'minikube service taskmanager --url | head -n 1', returnStdout: true).trim()
                        // Baseline scan : exit code 2 = alertes trouvees (non bloquant, a analyser dans le rapport)
                        def zapStatus = sh(returnStatus: true, script: """
                            docker run --rm --network host -v \$PWD/${REPORTS_DIR}:/zap/wrk/:rw \
                              zaproxy/zap-stable zap-baseline.py \
                              -t ${APP_URL} -r zap-report.html -J zap-report.json -I
                        """)
                        echo "ZAP baseline termine (code ${zapStatus}) - voir le rapport zap-report.html"
                    }
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: "${REPORTS_DIR}/zap-report.*", allowEmptyArchive: true
                }
            }
        }
    }

    post {
        success {
            echo "Pipeline termine avec succes. Image publiee : ${FULL_IMAGE}"
        }
        failure {
            echo 'Pipeline en echec : consulter les rapports SonarQube, Dependency-Check et Trivy.'
        }
        cleanup {
            sh 'docker image rm ${FULL_IMAGE} || true'
            cleanWs(deleteDirs: true)
        }
    }
}
