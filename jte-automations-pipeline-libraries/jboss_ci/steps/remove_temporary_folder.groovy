// Borra la carpeta temporal <JBOSS_TEMP_PATH>/<component_name> en cada servidor JBoss del ambiente.
// Va en el finally del template: se ejecuta siempre (éxito, fallo o abort) para no dejar carpetas en los servidores.
// NUNCA lanza error (en el finally taparía el error original del pipeline):
//   - si falla en un servidor, avisa con [WARN] y sigue con los demás
//   - al final, si quedó alguna carpeta sin borrar, marca el build UNSTABLE (si ya falló, sigue en FAILURE)
// Conexión: ssh <JBOSS_SSH_USER>@<host> con la llave del nodo de Jenkins.
void call() {
    def component_full_path = ''
    try {
        component_full_path = jboss_temporary_path()      // misma ruta validada que se usó para crear y copiar
    }
    catch (Exception e) {
        println("[WARN]: ⚠️ No se pudo armar la ruta de la carpeta temporal; no se borra nada: ${e.message ?: e.toString()}")
        return
    }

    // Usuario SSH: config.user (excepción por micro) o la propiedad de carpeta JBOSS_SSH_USER
    def ssh_user = String.valueOf(config.user ?: env.JBOSS_SSH_USER ?: '').trim()
    if (!ssh_user) {
        println('[WARN]: ⚠️ Falta el usuario SSH de JBoss (JBOSS_SSH_USER): no se puede borrar la carpeta temporal.')
        return
    }

    // 1. Nos aseguramos de tratar config.host como una lista
    def host_list = config.host instanceof String ? [config.host] : config.host
    def failed    = []

    println("[INFO]: Borrando la carpeta temporal ${component_full_path} en: ${host_list}")

    // 2. Iteramos sobre cada host para borrar la carpeta (rm -rf no falla si ya no existe)
    host_list.each { target_host ->
        try {
            sh """#!/bin/sh
ssh -o BatchMode=yes -o ConnectTimeout=15 ${ssh_user}@${target_host} 'rm -rf "${component_full_path}"'"""
            println("[INFO]: ✅ Carpeta temporal borrada en ${target_host}: ${component_full_path}")
        }
        catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
            throw e
        }
        catch (Exception e) {
            failed.add(target_host)
            println("[WARN]: ⚠️ No se pudo borrar la carpeta temporal en ${target_host}: ${e.message ?: e.toString()}")
        }
    }

    if (failed) {
        unstable("[WARN]: ⚠️ Quedó la carpeta temporal ${component_full_path} sin borrar en: ${failed}. Borrarla a mano.")
    }
    else {
        println("[INFO]: ✅ Carpeta temporal borrada en todos los servidores (${host_list.size()})")
    }
}
