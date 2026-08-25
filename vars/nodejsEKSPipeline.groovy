def call (Map configMap){
    pipeline {
        agent any
            /* node {
                label 'Agent-node'
            } */
            
        environment {
            def appVersion = ""
        }
        stages {
            stage('Read Package Info') {
                steps {
                    script {
                        dir('piplines/${component}') {
                            def packageJson = readJSON file: 'package.json'
                            appVersion = packageJson.version
                            echo "The application version is: ${appVersion}"
                        }
                    }
                }
            }
        stage('Install Dependencies') {
                steps {
                    script {
                        dir('piplines/${component}') {
                        sh """
                            npm install
                        """
                        }
                    } 
                }
            }
            // this command gives us coverage report and test cases report, sonarqube access this to check quality gate
            stage('Unit tests') {
                steps {
                    script {
                        dir('piplines/${component}') {
                        sh """
                            npm test
                        """
                        }
                    } 
                }
            }
            /*
            stage('SonarQube Analysis') {
                steps {
                    dir('piplines/${component}') {
                    script {
                        withSonarQubeEnv('sonar-scanner') {
                            def scannerHome = tool 'sonar-8'
                            sh "${tool 'sonar-8'}/bin/sonar-scanner"
                                }
                    }
                    
                    }
                }
            }
            stage('SonarQube Quality Gate') {
                steps {
                    timeout(time: 10, unit: 'MINUTES') {
                        script {
                            def qg = waitForQualityGate() // Pauses pipeline
                            if (qg.status != 'OK') {
                                error "Pipeline aborted: ${qg.status}"
                            }
                        }
                    }
                }
            }
            */
             stage('library-scan') {
                steps {
                    script {
                        try{
                            withCredentials([string(credentialsId: 'github-token', variable: 'GH_TOKEN')]) {
                                sh '''
                                    set -e

                                    REPO="${org}/${component}"

                                    curl -s -L \
                                    -H "Accept: application/vnd.github+json" \
                                    -H "Authorization: Bearer ${GH_TOKEN}" \
                                    -H "X-GitHub-Api-Version: 2026-03-10" \
                                    "https://api.github.com/repos/${REPO}/dependabot/alerts?state=open" \
                                    -o alerts.json

                                    echo "---- Open Dependabot Alerts ----"
                                    jq -r '.[] | "\\(.number)\\t\\(.security_vulnerability.severity)\\t\\(.dependency.package.name)\\t\\(.security_advisory.ghsa_id)"' alerts.json

                                    HIGH_CRITICAL_COUNT=$(jq '[.[] | select(.security_vulnerability.severity == "high" or .security_vulnerability.severity == "critical")] | length' alerts.json)

                                    echo "High/Critical alert count: ${HIGH_CRITICAL_COUNT}"

                                    if [ "$HIGH_CRITICAL_COUNT" -gt 0 ]; then
                                        echo "❌ Found ${HIGH_CRITICAL_COUNT} High/Critical severity dependency alert(s). Failing build."
                                        exit 1
                                    else
                                        echo "✅ No High/Critical dependency alerts found."
                                    fi
                                '''
                            }
                            utils.updateCommitStatus('SUCCESS', 'Library scan passed', 'library-scan')
                        }
                        catch (Exception e){
                            utils.updateCommitStatus('FAILURE', 'Library scan failed', 'library-scan')
                            throw e
                        }
                    }
                }
            }
        }
    }
}