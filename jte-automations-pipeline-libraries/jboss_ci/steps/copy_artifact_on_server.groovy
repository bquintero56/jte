// Copia el artefacto del componente (ej. target/Test-Java.war) a la carpeta temporal <JBOSS_TEMP_PATH>/<component_name>
// en cada servidor JBoss del ambiente. Si falla en un servidor, detiene el pipeline (no se sigue con los demás).
//   Artefacto : único archivo *.<component_packaging> en <artifact_workspace_location> (default 'target')
//   Conexión  : ssh / scp <JBOSS_SSH_USER>@<host> con la llave del nodo de Jenkins
// Devuelve el nombre del artefacto copiado (ej. 'Test-Java.war').
String call() {
    def component_full_path = jboss_temporary_path()

    // Usuario SSH: config.user (excepción por micro) o la propiedad de carpeta JBOSS_SSH_USER
    def ssh_user = String.valueOf(config.user ?: env.JBOSS_SSH_USER ?: '').trim()
    if (!ssh_user) {
        error('[ERROR]: ❌ Falta el usuario SSH de JBoss: definir la propiedad de carpeta JBOSS_SSH_USER en la carpeta de Jenkins (o jboss_ci.user en la config).')
    }

    // Empaquetado: config.component_packaging o el keyword component_packaging
    def packaging = String.valueOf(config.component_packaging ?: '').trim()
    if (!packaging) {
        try { packaging = String.valueOf(component_packaging).trim() } catch (MissingPropertyException e) { packaging = '' }
    }
    if (!packaging) {
        error('[ERROR]: ❌ Falta component_packaging (war, ear, jar...) en jboss_ci o en keywords de la config.')
    }

    def location  = String.valueOf(config.artifact_workspace_location ?: 'target').trim().replaceFirst(/\/+$/, '')
    def host_list = config.host instanceof String ? [config.host] : config.host

    try {
        println("[INFO]: Buscando el artefacto *.${packaging} en ${location}")

        // 1. Buscamos el archivo una sola vez para todos los hosts (debe haber exactamente uno)
        def found = sh(script: """#!/bin/sh
find '${location}' -maxdepth 1 -type f -name '*.${packaging}' -exec basename {} \\;""", returnStdout: true).trim()

        if (!found) {
            error("[ERROR]: ❌ No se encontró ningún archivo *.${packaging} en ${location}. ¿Se ejecutó el build?")
        }
        if (found.contains('\n')) {
            error("[ERROR]: ❌ Hay más de un archivo *.${packaging} en ${location}; no se sabe cuál copiar:\n${found}")
        }

        def artifact_name     = found
        def local_source_path = "${location}/${artifact_name}"
        println("[INFO]: Artefacto: ${local_source_path} → ${component_full_path} en ${host_list}")

        // 2. Copiamos a cada servidor
        host_list.each { target_host ->
            println("[INFO]: Procesando servidor: ${target_host}")
            try {
                sh """#!/bin/sh
ssh -o BatchMode=yes -o ConnectTimeout=15 ${ssh_user}@${target_host} 'mkdir -p "${component_full_path}"'
scp -o BatchMode=yes -o ConnectTimeout=15 '${local_source_path}' '${ssh_user}@${target_host}:${component_full_path}/'"""
                println("[INFO]: ✅ ${artifact_name} copiado a ${target_host}:${component_full_path}")
            }
            catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
                throw e
            }
            catch (Exception e) {
                error("[ERROR]: ❌ No se pudo copiar a ${target_host}: ${e.message ?: e.toString()}")
            }
        }

        println("[INFO]: ✅ Artefacto copiado a todos los servidores (${host_list.size()})")
        return artifact_name
    }
    catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
        throw e
    }
    catch (Exception e) {
        error("${e.message ?: e.toString()}")
    }
}
