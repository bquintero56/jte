// CI de componentes JBoss — job 'dev': rama dev → ambiente de desarrollo
// Flujo: checkout (GitLab) → build Maven → carpeta temporal en el servidor → copia del artefacto
// → dispara el job de CD (despliegue) → borra la carpeta temporal.
// El estado del pipeline se informa en GitLab (running → success / failed / canceled) junto al commit.
// Los 5 templates (default, dev, qa, uat, main) son casi iguales: cada uno agrega o quita etapas según el ambiente.
// Se van agregando las etapas a medida que se prueban.
def checkout_done = false

timeout(time: execution.time, unit: execution.units) {

    node(jenkins_agent_label) {
        withFolderProperties {
            // Dentro de withFolderProperties para leer GITLAB_CONNECTION (propiedad de la carpeta 'mexico')
            properties([
                buildDiscarder(logRotator(numToKeepStr: log_rotator)),
                disableConcurrentBuilds(),
                durabilityHint('PERFORMANCE_OPTIMIZED'),
                gitLabConnection(gitLabConnection: env.GITLAB_CONNECTION ?: ''),
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

            try {
                stage('GitLab: checkout') {
                    checkout_code(dev.short_name)
                    checkout_done = true
                }
                update_gitlab_status('running')

                // PRÓXIMAS ETAPAS (se agregan una por una al probarlas):
                //   Maven: build binary            → build_component()
                //   JBoss: create temporary folder → create_temporary_folder()
                //   JBoss: copy artifact           → copy_artifact_on_server()
                //   Trigger parametrized job       → trigger_jboss_deploy_job(dev.deployment_project, ...)
                //   JBoss: remove temporary folder (en el finally, para que se borre siempre)

                update_gitlab_status('success')
            }
            catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
                if (checkout_done) { update_gitlab_status('canceled') }
                throw e     // conserva el estado ABORTED
            }
            catch (Exception e) {
                if (checkout_done) { update_gitlab_status('failed') }
                error("${e.message ?: e.toString()}")
            }
            finally {
                clean_job_workspace()
            }
        }
    }
}
