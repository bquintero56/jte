// Orquestador de webhooks de GitLab: recibe el evento de cualquier proyecto,
// arma la ruta del job en Jenkins (grupo/repo/rama) y lo ejecuta.
def evento = [:]
def ruta   = ''

timeout(time: execution.time, unit: execution.units) {

    node(jenkins_agent_label) {
        withFolderProperties {
            try {
                stage('Webhook: registrar disparador GitLab') {
                    programar_webhook()
                }

                stage('Webhook: leer evento') {
                    evento = leer_evento_webhook()
                }

                if (!evento.desdeWebhook) {
                    echo 'Sin evento de GitLab: no se ejecuta ningún job.'
                } else if (evento.ramaBorrada) {
                    echo "Se borró la rama '${evento.rama}' en ${evento.proyecto}: no se ejecuta ningún job."
                } else {
                    currentBuild.description = "${evento.proyecto} @ ${evento.rama}"

                    stage('Orquestación: armar ruta del job') {
                        ruta = armar_ruta_job(evento)
                    }

                    stage('Orquestación: ejecutar job') {
                        ejecutar_job(ruta, evento.rama)
                    }
                }
            }
            catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
                throw e     // conserva el estado ABORTED / FAILURE del job hijo
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
