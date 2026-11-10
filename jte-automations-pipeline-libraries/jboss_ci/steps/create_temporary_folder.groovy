// Crea (o recrea vacía) la carpeta temporal <path>/<component_name> en cada servidor JBoss del ambiente.
// Si falla en un servidor, detiene el pipeline (no se sigue con los demás).
// Conexión: ssh <user>@<host> con la llave del nodo de Jenkins.
void call() {
    def component_full_path = jboss_temporary_path()

    // config.host como lista, aunque venga un solo host como String
    def host_list = config.host instanceof String ? [config.host] : config.host
    if (!host_list) {
        error('❌ Falta jboss.host en la config: lista de servidores JBoss del ambiente.')
    }

    echo "Preparando la carpeta temporal ${component_full_path} en: ${host_list}"

    for (int i = 0; i < host_list.size(); i++) {
        def target_host = host_list[i]
        try {
            sh """#!/bin/sh
ssh -o BatchMode=yes -o ConnectTimeout=15 ${config.user}@${target_host} 'rm -rf "${component_full_path}" && mkdir -p "${component_full_path}"'"""
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
