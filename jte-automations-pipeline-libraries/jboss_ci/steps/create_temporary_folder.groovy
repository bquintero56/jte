// Crea (o recrea vacía) la carpeta temporal <JBOSS_TEMP_PATH>/<component_name> en cada servidor JBoss del ambiente.
// Si falla en un servidor, detiene el pipeline (no se sigue con los demás).
// Conexión: ssh <JBOSS_SSH_USER>@<host> "sudo -n -u <JBOSS_RUN_AS_USER> ..." con la llave del nodo de Jenkins.
// La carpeta queda creada por el usuario de JBoss (dueño correcto para el despliegue).
void call() {
    def component_full_path = jboss_temporary_path()

    // Usuario SSH: config.user (excepción por micro) o la propiedad de carpeta JBOSS_SSH_USER
    def ssh_user = String.valueOf(config.user ?: env.JBOSS_SSH_USER ?: '').trim()
    if (!ssh_user) {
        error('❌ Falta el usuario SSH de JBoss: definir la propiedad de carpeta JBOSS_SSH_USER en la carpeta de Jenkins (o jboss_ci.user en la config).')
    }

    // Usuario de JBoss (sudo -u): config.run_as_user (excepción por micro) o la propiedad de carpeta JBOSS_RUN_AS_USER
    def run_as_user = String.valueOf(config.run_as_user ?: env.JBOSS_RUN_AS_USER ?: '').trim()
    if (!run_as_user) {
        error('❌ Falta el usuario de JBoss: definir la propiedad de carpeta JBOSS_RUN_AS_USER en la carpeta de Jenkins (o jboss_ci.run_as_user en la config).')
    }

    // config.host como lista, aunque venga un solo host como String
    def host_list = config.host instanceof String ? [config.host] : config.host
    if (!host_list) {
        error('❌ Falta jboss_ci.host en la config: lista de servidores JBoss del ambiente.')
    }

    echo "Preparando la carpeta temporal ${component_full_path} en: ${host_list}"

    host_list.each { target_host ->
        try {
            sh """#!/bin/sh
ssh -o BatchMode=yes -o ConnectTimeout=15 ${ssh_user}@${target_host} "sudo -n -u ${run_as_user} rm -rf '${component_full_path}' && sudo -n -u ${run_as_user} mkdir -p '${component_full_path}'"
"""
            echo "✅ Carpeta temporal lista en ${target_host}: ${component_full_path} (usuario ${run_as_user})"
        }
        catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
            throw e
        }
        catch (Exception e) {
            error("❌ No se pudo preparar la carpeta temporal en ${target_host}: ${e.message ?: e.toString()}")
        }
    }

    echo "✅ Carpeta temporal lista en todos los servidores (${host_list.size()})"
}
