// Despliega en el JBoss DOMAIN, en el server group del micro, el artefacto que dejó el CI en <JBOSS_TEMP_PATH>/<COMPONENT_NAME>.
//   Artefacto   : único *.<component_packaging> en la carpeta temporal DEL SERVIDOR (jboss-cli corre en el servidor, ruta absoluta)
//   Comando     : [sudo -u <run_as_user>] <jboss_home>/bin/jboss-cli.sh --connect [--controller=<JBOSS_CONTROLLER>] -u=... -p=...
//                 --command="deploy <archivo> --server-groups=<SERVER_GROUP>"   (para standalone usar jboss_cd_standalone)
//   server group: parámetro SERVER_GROUP (lo envía el CI desde <ambiente>.server_group de la config del micro). Obligatorio: vacío → ❌.
//   controller  : parámetro JBOSS_CONTROLLER (lo envía el CI desde <ambiente>.jboss_controller de la config del micro, host:puerto;
//                 sin puerto → 9990). Vacío = sin --controller (localhost:9990 del servidor al que entra por SSH).
//   run_as_user : config.run_as_user (ej. 'jboss-eap') → antepone 'sudo -u <usuario>' a jboss-cli. Vacío = sin sudo.
//                 Ese usuario debe poder LEER el artefacto de la carpeta temporal.
//   jboss-cli   : <jboss_home>/bin/jboss-cli.sh (default /opt/jboss-eap), credencial config.cli_credentials_id (default 'jboss-cli')
//   SSH         : <JBOSS_SSH_USER>@<host_deploy> con la llave del nodo de Jenkins
// Si el componente ya estaba desplegado, antes debe correr undeploy_component (si no, JBoss rechaza el deploy por duplicado).
// Devuelve true si desplegó (misma interfaz que jboss_cd_standalone; aquí no hay dry_run todavía).
Boolean call(String component_name, String component_packaging) {
    def host           = String.valueOf(config.host_deploy ?: '').trim()
    def ssh_user       = String.valueOf(config.user ?: env.JBOSS_SSH_USER ?: '').trim()
    def jboss_home     = String.valueOf(config.jboss_home ?: '/opt/jboss-eap').trim().replaceFirst(/\/+$/, '')
    def credentials_id = String.valueOf(config.cli_credentials_id ?: 'jboss-cli').trim()
    def temp_base      = String.valueOf(env.JBOSS_TEMP_PATH ?: '').trim().replaceFirst(/\/+$/, '')
    def name           = String.valueOf(component_name ?: '').trim()
    def packaging      = String.valueOf(component_packaging ?: '').trim()
    def server_group   = String.valueOf(params.SERVER_GROUP ?: '').trim()

    // Validaciones
    if (!name || !packaging) {
        error('[ERROR]: ❌ Faltan los parámetros COMPONENT_NAME / COMPONENT_PACKAGING (este job lo lanza el CI del micro).')
    }
    if (name.contains('/') || name.contains('..') || name.contains(' ') || name.contains("'")) {
        error("[ERROR]: ❌ COMPONENT_NAME no válido: '${name}'.")
    }
    if (!temp_base || !temp_base.startsWith('/')) {
        error("[ERROR]: ❌ Falta la propiedad de carpeta JBOSS_TEMP_PATH (ruta absoluta, la misma que usa el CI).")
    }
    if (!ssh_user) {
        error('[ERROR]: ❌ Falta el usuario SSH de JBoss: definir la propiedad de carpeta JBOSS_SSH_USER (o jboss_cd_domain.user en la config).')
    }
    if (!server_group) {
        error('[ERROR]: ❌ Falta el parámetro SERVER_GROUP: definir <ambiente>.server_group en la config del micro (ej. server_group = \'main-server-group\').')
    }
    if (!server_group.matches('[A-Za-z0-9._-]+')) {
        error("[ERROR]: ❌ SERVER_GROUP no válido para jboss-cli: '${server_group}'.")
    }

    // --controller (JBOSS_CONTROLLER del micro)
    def controller = String.valueOf(params.JBOSS_CONTROLLER ?: '').trim()
    if (controller && !controller.matches('[A-Za-z0-9._-]+(:[0-9]{1,5})?')) {
        error("[ERROR]: ❌ JBOSS_CONTROLLER no válido: '${controller}' (formato host:puerto, ej. 10.0.0.5:9990).")
    }
    if (controller && !controller.contains(':')) {
        controller = controller + ':9990'
    }
    def controller_arg = controller ? " --controller=${controller}" : ''

    // sudo -u <run_as_user>
    def run_as = String.valueOf(config.run_as_user ?: '').trim()
    if (run_as && !run_as.matches('[A-Za-z0-9._-]+')) {
        error("[ERROR]: ❌ jboss_cd_domain.run_as_user no válido: '${run_as}'.")
    }
    def run_as_prefix = run_as ? "sudo -u ${run_as} " : ''

    def temp_dir = "${temp_base}/${name}"

    // 1. Artefacto en la carpeta temporal del servidor (debe haber exactamente uno)
    println("[INFO]: Buscando el artefacto *.${packaging} en ${host}:${temp_dir}")
    def found = sh(script: """#!/bin/sh
ssh -o BatchMode=yes -o ConnectTimeout=15 ${ssh_user}@${host} 'ls -1 "${temp_dir}"/*.${packaging} 2>/dev/null' || true""",
                   returnStdout: true).trim()

    if (!found) {
        error("[ERROR]: ❌ No hay ningún *.${packaging} en ${host}:${temp_dir}. ¿El CI copió el artefacto a este servidor?")
    }
    if (found.contains('\n')) {
        error("[ERROR]: ❌ Hay más de un *.${packaging} en ${host}:${temp_dir}; no se sabe cuál desplegar:\n${found}")
    }
    def artifact = found

    // 2. Deploy con jboss-cli (ruta absoluta del archivo en el servidor)
    def cli_command = "deploy ${artifact} --server-groups=${server_group}"
    println("[INFO]: Desplegando ${artifact} en el server group '${server_group}' de JBoss domain (${host})")

    println("[INFO]: Comando enviado: ssh -o BatchMode=yes -o ConnectTimeout=15 ${ssh_user}@${host} \"echo p | ${run_as_prefix}${jboss_home}/bin/jboss-cli.sh --connect${controller_arg} -u=*** -p=*** --command='${cli_command}'\"")

    def status_code = 0
    withCredentials([usernamePassword(credentialsId: credentials_id,
                                      usernameVariable: 'JBOSS_CLI_USER',
                                      passwordVariable: 'JBOSS_CLI_PASSWORD')]) {
        // Usuario y clave como variables del SHELL (\$...): no se interpolan en Groovy ni quedan en el log
        status_code = sh(script: """#!/bin/sh
ssh -o BatchMode=yes -o ConnectTimeout=15 ${ssh_user}@${host} "echo p | ${run_as_prefix}${jboss_home}/bin/jboss-cli.sh --connect${controller_arg} -u='\$JBOSS_CLI_USER' -p='\$JBOSS_CLI_PASSWORD' --command='${cli_command}'" 2>&1""",
                         returnStatus: true)
    }

    if (status_code != 0) {
        error("[ERROR]: ❌ Falló el deploy de ${artifact} en el server group '${server_group}' de ${host} (código ${status_code}). Ver la salida de jboss-cli arriba.")
    }

    println("[INFO]: ✅ ${artifact} desplegado en el server group '${server_group}' (${host})")
    return true
}
