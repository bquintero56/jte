// CD de componentes JBoss: lo lanza el CI del micro (trigger_jboss_deploy_job) con COMPONENT_NAME y COMPONENT_PACKAGING.
// Un job por servidor/aplicación (ej. pais/jboss/app1/dev-deployment), con la config del SERVIDOR.
// PRUEBA: por ahora deploy_component() solo muestra los datos recibidos.
properties([
    buildDiscarder(logRotator(numToKeepStr: log_rotator)),
    disableConcurrentBuilds(),
    parameters([
        string(name: 'COMPONENT_NAME',      defaultValue: '', description: 'Nombre del componente (lo envía el CI)'),
        string(name: 'COMPONENT_PACKAGING', defaultValue: '', description: 'Empaquetado: war, ear, jar... (lo envía el CI)')
    ])
])

timeout(time: execution.time, unit: execution.units) {

    node(jenkins_agent_label) {
        withFolderProperties {
            try {
                currentBuild.description = "${params.COMPONENT_NAME ?: '-'}.${params.COMPONENT_PACKAGING ?: '-'}"

                stage('JBoss: deploy component') {
                    deploy_component()
                }
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
