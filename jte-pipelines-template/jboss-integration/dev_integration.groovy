// CI de componentes JBoss — job 'dev': rama dev → ambiente de desarrollo
// Flujo: checkout (GitLab) → build Maven → carpeta temporal en el servidor → copia del artefacto
// → dispara el job de CD (despliegue) → borra la carpeta temporal.
// Cada etapa (gitlab_stage) informa su estado en GitLab junto al commit (conexión GitLab en properties).
// Los 5 templates (default, dev, qa, uat, main) son casi iguales: cada uno agrega o quita etapas según el ambiente.
// Se van agregando las etapas a medida que se prueban.
// PRUEBAS: por ahora dev solo hace checkout → build → carpeta temporal → copia del artefacto → job de CD (sin compile, unit test ni checkstyle).
properties([
    buildDiscarder(logRotator(numToKeepStr: log_rotator)),
    disableConcurrentBuilds(),
    durabilityHint('PERFORMANCE_OPTIMIZED'),
    [
        $class                    : 'EnvInjectJobProperty',
        info                      : [
            loadFilesFromMaster: false,
            propertiesContent  : job_environments
        ],
        keepBuildVariables        : true,
        keepJenkinsSystemVariables: true,
        on                        : true
    ],
    gitLabConnection(gitLabConnection: 'api-gitlab_connection_java_repositorie')   // conexión GitLab (System) para el estado por etapa
])

// Parámetros para el job de CD (despliegue) y si se espera a que termine
def wait_job_execution = true
def jboss_pipeline_parameters = [
    string(name: 'COMPONENT_NAME',      value: component_name),
    string(name: 'COMPONENT_PACKAGING', value: component_packaging)
]

timeout(time: execution.time, unit: execution.units) {

    node(jenkins_agent_label) {
        withFolderProperties {
            try {
                gitlab_stage('GitLab: checkout') {
                    checkout_code(dev.short_name)
                }

                gitlab_stage('Maven: build binary') {
                    build_component()
                }

                gitlab_stage('JBoss: create temporary folder') {
                    create_temporary_folder()
                }

                gitlab_stage('JBoss: copy artifact') {
                    copy_artifact_on_server()
                }

                gitlab_stage('JBoss: trigger deploy job') {
                    trigger_jboss_deploy_job(dev.deployment_project, jboss_pipeline_parameters, wait_job_execution)
                }

                // PRÓXIMAS ETAPAS (se agregan una por una al probarlas):
                //   JBoss: remove temporary folder (en el finally, para que se borre siempre)
            }
            catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
                throw e     // conserva el estado ABORTED
            }
            catch (Exception e) {
                error("${e.message ?: e.toString()}")
            }
            finally {
                clean_job_workspace()
            }
        }
    }
}
