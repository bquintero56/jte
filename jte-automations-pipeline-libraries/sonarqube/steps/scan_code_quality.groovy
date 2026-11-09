// Analiza el código con el scanner de Maven y publica el análisis en SonarQube (Community 9.9: un análisis por proyecto, sin ramas).
//   Servidor   : Sonar_server_SCAN_SERVER (withSonarQubeEnv inyecta URL y token configurados en Jenkins)
//   Project key: component_name (keyword de la config) = nombre del repositorio
//   JDK / Maven: propiedades de carpeta F_PIPELINE_JDK_TOOL_VERSION / F_PIPELINE_MAVEN_TOOL_VERSION
// Necesita las clases compiladas en target/ (va después de los unit tests y antes del build, que hace clean).
void call() {
    def sonar_server = 'Sonar_server_SCAN_SERVER'
    def scanner      = 'org.sonarsource.scanner.maven:sonar-maven-plugin:3.11.0.3922:sonar'   // versión fija compatible con SonarQube 9.9
    def project_key  = ''
    try { project_key = component_name } catch (MissingPropertyException e) { project_key = '' }

    if (!project_key) {
        error('❌ Falta component_name en keywords de la config (es el project key de SonarQube).')
    }

    try {
        withFolderProperties {
            def jdk_version = env.F_PIPELINE_JDK_TOOL_VERSION
            def mvn_version = env.F_PIPELINE_MAVEN_TOOL_VERSION
            def mvn_opts    = [artifactsPublisher(disabled: true), jacocoPublisher(disabled: true), junitPublisher(disabled: true)]

            withSonarQubeEnv(sonar_server) {
                withMaven(globalMavenSettingsConfig: config.settings_config ?: 'GLOBAL_MAVEN_SETTINGS', jdk: jdk_version, maven: mvn_version, options: mvn_opts, traceability: false) {
                    def status_code = sh(script: """#!/bin/sh
mvn ${scanner} -Dsonar.projectKey='${project_key}' -Dsonar.projectName='${project_key}'""", returnStatus: true)
                    if (status_code != 0) {
                        error("❌ Falló el análisis de SonarQube del proyecto '${project_key}' (código ${status_code}).")
                    }
                }
            }
            echo "✅ Análisis publicado en SonarQube: proyecto '${project_key}'"
        }
    }
    catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
        throw e
    }
    catch (Exception e) {
        error("${e.message ?: e.toString()}")
    }
}
