// CD de componentes JBoss: lo lanza el CI del micro (trigger_jboss_deploy_job) con COMPONENT_NAME y COMPONENT_PACKAGING.
// Un job por servidor JBoss (ej. pais/jboss-server/app1/dev/jboss-ui-deployment), con la config del SERVIDOR.
// Flujo: deployment info → undeploy (solo si ya existe; PENDIENTE) → deploy → verify. Standalone o domain según la librería que carga la config del servidor
// (jboss_cd_standalone o jboss_cd_domain: mismos nombres de steps).
properties([
    buildDiscarder(logRotator(numToKeepStr: log_rotator)),
    disableConcurrentBuilds(),
    parameters([
        string(name: 'COMPONENT_NAME',      defaultValue: '', description: 'Nombre del componente (lo envía el CI)'),
        string(name: 'COMPONENT_PACKAGING', defaultValue: '', description: 'Empaquetado: war, ear, jar... (lo envía el CI)')
    ])
])

// Resultado de get_deployment_info: true si el componente ya está desplegado en este JBoss
def component_deployed = false

timeout(time: execution.time, unit: execution.units) {

    node(jenkins_agent_label) {
        withFolderProperties {
            try {
                currentBuild.description = "${params.COMPONENT_NAME ?: '-'}.${params.COMPONENT_PACKAGING ?: '-'}"

                stage('JBoss: deployment info') {
                    component_deployed = get_deployment_info(params.COMPONENT_NAME)
                }

                stage('JBoss: undeploy component') {
                    if (component_deployed) {
                        // PENDIENTE: undeploy_component(params.COMPONENT_NAME). Mientras tanto se detiene: JBoss rechazaría el deploy por duplicado.
                        error("[ERROR]: ❌ '${params.COMPONENT_NAME}' ya está desplegado y el undeploy todavía no está implementado.")
                    }
                    println("[INFO]: '${params.COMPONENT_NAME}' no está desplegado: no hace falta undeploy")
                }

                stage('JBoss: deploy component') {
                    deploy_component(params.COMPONENT_NAME, params.COMPONENT_PACKAGING)
                }

                stage('JBoss: verify deployment') {
                    if (!get_deployment_info(params.COMPONENT_NAME)) {
                        error("[ERROR]: ❌ '${params.COMPONENT_NAME}' no quedó desplegado en JBoss")
                    }
                    println("[INFO]: ✅ Despliegue verificado: '${params.COMPONENT_NAME}'")
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
