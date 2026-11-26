// CD de componentes JBoss: lo lanza el CI del micro (trigger_jboss_deploy_job) con COMPONENT_NAME y COMPONENT_PACKAGING.
// Un job por servidor JBoss (ej. pais/jboss-server/app1/dev/jboss-ui-deployment), con la config del SERVIDOR.
// Flujo: deployment info → undeploy (solo si ya existe) → deploy → verify. Standalone o domain según la librería que carga la config del servidor
// (jboss_cd_standalone o jboss_cd_domain: mismos nombres de steps).
properties([
    buildDiscarder(logRotator(numToKeepStr: log_rotator)),
    disableConcurrentBuilds(),
    parameters([
        string(name: 'COMPONENT_NAME',      defaultValue: '', description: 'Nombre del componente (lo envía el CI)'),
        string(name: 'COMPONENT_PACKAGING', defaultValue: '', description: 'Empaquetado: war, ear, jar... (lo envía el CI)')
    ])
])

// Estado del deployment antes de desplegar (get_deployment_info): [exists, name, enabled, status, running]
def deployment_info = [:]
// true si deploy_component desplegó de verdad (false si fue simulación con dry_run)
def deployed_now = false

timeout(time: execution.time, unit: execution.units) {

    node(jenkins_agent_label) {
        withFolderProperties {
            try {
                currentBuild.description = "${params.COMPONENT_NAME ?: '-'}.${params.COMPONENT_PACKAGING ?: '-'}"

                stage('JBoss: deployment info') {
                    deployment_info = get_deployment_info(params.COMPONENT_NAME)
                }

                stage('JBoss: undeploy component') {
                    if (deployment_info.exists) {
                        undeploy_component(params.COMPONENT_NAME, params.COMPONENT_PACKAGING)
                    } else {
                        println("[INFO]: '${params.COMPONENT_NAME}' no está desplegado: no hace falta undeploy")
                    }
                }

                stage('JBoss: deploy component') {
                    deployed_now = deploy_component(params.COMPONENT_NAME, params.COMPONENT_PACKAGING)
                }

                stage('JBoss: verify deployment') {
                    if (!deployed_now) {
                        println("[INFO]: Sin verificación: el deploy fue una simulación (dry_run)")
                    } else {
                        def after = get_deployment_info(params.COMPONENT_NAME)
                        if (!after.running) {
                            error("[ERROR]: ❌ '${params.COMPONENT_NAME}' no quedó funcionando en JBoss (existe=${after.exists}, ENABLED=${after.enabled}, STATUS=${after.status})")
                        }
                        println("[INFO]: ✅ Despliegue verificado: '${after.name}' ENABLED=true, STATUS=OK")
                    }
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
