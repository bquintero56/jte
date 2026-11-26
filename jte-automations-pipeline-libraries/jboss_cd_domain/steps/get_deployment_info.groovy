// Consulta en el JBoss DOMAIN el estado del deployment del componente a desplegar en EL server group del micro, con jboss-cli por SSH.
//   jboss-cli   : [sudo -u <run_as_user>] <jboss_home>/bin/jboss-cli.sh --connect [--controller=<JBOSS_CONTROLLER>] -u=... -p=...
//                 --command="deployment-info --name=<componente>* --server-group=<SERVER_GROUP>"
//   server group: parámetro SERVER_GROUP (lo envía el CI desde <ambiente>.server_group de la config del micro). Obligatorio: vacío → ❌.
//   controller  : parámetro JBOSS_CONTROLLER (lo envía el CI desde <ambiente>.jboss_controller de la config del micro, host:puerto;
//                 sin puerto → 9990). Vacío = sin --controller (localhost:9990 del servidor al que entra por SSH).
//   run_as_user : config.run_as_user (ej. 'jboss-eap') → antepone 'sudo -u <usuario>' a jboss-cli. Vacío = sin sudo.
//   jboss_home  : config.jboss_home (default '/opt/jboss-eap')
//   credencial  : usuario/clave de administración de JBoss = credencial Username with password config.cli_credentials_id (default 'jboss-cli')
//   SSH         : <JBOSS_SSH_USER>@<host_deploy> con la llave del nodo de Jenkins
// Salida de deployment-info --server-group (domain):
//   NAME           RUNTIME-NAME   STATE
//   Test-Java.war  Test-Java.war  enabled        (enabled = activo en el grupo; added = asignado pero deshabilitado; not added = no asignado)
// Devuelve un mapa (misma interfaz que jboss_cd_standalone.get_deployment_info):
//   exists  : true si el contenido <componente>.<empaquetado> existe en el domain (aunque no esté asignado al grupo)
//   name    : nombre del deployment (ej. 'Test-Java.war'), '' si no existe
//   enabled : true si STATE = enabled en el server group
//   status  : STATE en el server group (enabled, added, not added), '' si no existe
//   running : igual que enabled (desplegado y activo en el server group)
// Falla (❌) si falta SERVER_GROUP, si no se puede conectar a JBoss o si la autenticación falla.
Map call(String component_name) {
    def host           = String.valueOf(config.host_deploy ?: '').trim()
    def ssh_user       = String.valueOf(config.user ?: env.JBOSS_SSH_USER ?: '').trim()
    def jboss_home     = String.valueOf(config.jboss_home ?: '/opt/jboss-eap').trim().replaceFirst(/\/+$/, '')
    def credentials_id = String.valueOf(config.cli_credentials_id ?: 'jboss-cli').trim()
    def server_group   = String.valueOf(params.SERVER_GROUP ?: '').trim()

    if (!host) {
        error('[ERROR]: ❌ Falta jboss_cd_domain.host_deploy en la config del servidor.')
    }
    if (!ssh_user) {
        error('[ERROR]: ❌ Falta el usuario SSH de JBoss: definir la propiedad de carpeta JBOSS_SSH_USER (o jboss_cd_domain.user en la config).')
    }
    if (!component_name?.trim()) {
        error('[ERROR]: ❌ Falta el parámetro COMPONENT_NAME (este job lo lanza el CI del micro con COMPONENT_NAME y COMPONENT_PACKAGING).')
    }
    if (component_name.contains("'") || component_name.contains(' ')) {
        error("[ERROR]: ❌ Nombre de componente no válido para jboss-cli: '${component_name}'.")
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

    println("[INFO]: Consultando el estado de '${component_name}' en el server group '${server_group}' de JBoss domain (${host})")

    def cli_command = "deployment-info --name=${component_name}* --server-group=${server_group}"
    println("[INFO]: Comando enviado: ssh -o BatchMode=yes -o ConnectTimeout=15 ${ssh_user}@${host} \"echo p | ${run_as_prefix}${jboss_home}/bin/jboss-cli.sh --connect${controller_arg} -u=*** -p=*** --command='${cli_command}'\"")

    def output = ''
    withCredentials([usernamePassword(credentialsId: credentials_id,
                                      usernameVariable: 'JBOSS_CLI_USER',
                                      passwordVariable: 'JBOSS_CLI_PASSWORD')]) {
        // Usuario y clave como variables del SHELL (\$...): no se interpolan en Groovy ni quedan en el log.
        // 'echo p' acepta el certificado del servidor si jboss-cli lo pregunta. "|| true": la salida se analiza abajo.
        output = sh(script: """#!/bin/sh
ssh -o BatchMode=yes -o ConnectTimeout=15 ${ssh_user}@${host} "echo p | ${run_as_prefix}${jboss_home}/bin/jboss-cli.sh --connect${controller_arg} -u='\$JBOSS_CLI_USER' -p='\$JBOSS_CLI_PASSWORD' --command='${cli_command}'" 2>&1 || true""",
                    returnStdout: true).trim()
    }

    println("[INFO]: Salida de jboss-cli:\n${output}")

    // Errores de conexión / autenticación: no se puede decidir
    def lower = output.toLowerCase()
    if (lower.contains('failed to connect') || lower.contains('unable to connect') || lower.contains('connection refused') ||
        lower.contains('authentication failed') || lower.contains('unable to authenticate') || lower.contains('permission denied') ||
        lower.contains('no such file or directory')) {
        error("[ERROR]: ❌ No se pudo consultar JBoss en ${host} (conexión, autenticación o ruta de jboss-cli). Ver la salida arriba.")
    }

    def info  = [exists: false, name: '', enabled: false, status: '', running: false]
    def lines = output.split('\n')
    for (int i = 0; i < lines.length; i++) {
        def line    = lines[i].trim()
        def columns = line.split(/\s+/)
        // Nombre exacto: <componente>.<empaquetado> (ej. Test-Java.war); no confunde Test-Java con Test-Java-Admin.war
        if (columns[0].startsWith(component_name + '.')) {
            info.exists  = true
            info.name    = columns[0]
            info.status  = line.endsWith('not added') ? 'not added' : columns[columns.length - 1]
            info.enabled = info.status == 'enabled'
            info.running = info.enabled
            break
        }
    }

    if (!info.exists) {
        println("[INFO]: '${component_name}' no está desplegado en ${host}")
    }
    else if (info.running) {
        println("[INFO]: ✅ '${info.name}' desplegado y activo en el server group '${server_group}' (STATE=enabled)")
    }
    else {
        println("[WARN]: ⚠️ '${info.name}' existe pero NO está activo en el server group '${server_group}' (STATE=${info.status})")
    }
    return info
}
