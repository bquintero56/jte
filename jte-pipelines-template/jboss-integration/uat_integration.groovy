// CI de componentes JBoss — job 'uat': rama uat → ambiente de UAT
// Flujo: checkout (GitLab) → build Maven → carpeta temporal en el servidor → copia del artefacto
// → dispara el job de CD (despliegue) → borra la carpeta temporal.
// Los 5 templates (default, dev, qa, uat, main) son casi iguales: cada uno agrega o quita etapas según el ambiente.
// Se van agregando las etapas a medida que se prueban.
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
    ]
])

timeout(time: execution.time, unit: execution.units) {

    node(jenkins_agent_label) {
        withFolderProperties {
            try {
                stage('GitLab: checkout') {
                    checkout_code(uat.short_name)
                }

                // PRÓXIMAS ETAPAS (se agregan una por una al probarlas):
                //   Maven: build binary            → build_component()
                //   JBoss: create temporary folder → create_temporary_folder()
                //   JBoss: copy artifact           → copy_artifact_on_server()
                //   Trigger parametrized job       → trigger_jboss_deploy_job(uat.deployment_project, ...)
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
