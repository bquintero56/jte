// Crea (o recrea vacía) la carpeta temporal <JBOSS_TEMP_PATH>/<component_name> en cada servidor JBoss del ambiente.
// Si falla en un servidor, detiene el pipeline (no se sigue con los demás).
// Conexión: ssh <JBOSS_SSH_USER>@<host> con la llave del nodo de Jenkins.
void call() {
    def component_full_path = jboss_temporary_path()

    // Usuario SSH: config.user (excepción por micro) o la propiedad de carpeta JBOSS_SSH_USER
    def ssh_user = String.valueOf(config.user ?: env.JBOSS_SSH_USER ?: '').trim()
    if (!ssh_user) {
        error('❌ Falta el usuario SSH de JBoss: definir la propiedad de carpeta JBOSS_SSH_USER en la carpeta de Jenkins (o jboss_ci.user en la config).')
    }

    // config.host como lista, aunque venga un solo host como String
    def host_list = config.host instanceof String ? [config.host] : config.host
    if (!host_list) {
        error('❌ Falta jboss_ci.host en la config: lista de servidores JBoss del ambiente.')
    }

    echo "Preparando la carpeta temporal ${component_full_path} en: ${host_list}"

    for (int i = 0; i < host_list.size(); i++) {
        def target_host = host_list[i]
        try {
            sh """#!/bin/sh
ssh -o BatchMode=yes -o ConnectTimeout=15 ${ssh_user}@${target_host} 'rm -rf "${component_full_path}" && mkdir -p "${component_full_path}"'"""
            echo "✅ Carpeta temporal lista en ${target_host}: ${component_full_path}"
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
