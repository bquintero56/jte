// Consulta en el JBoss del servidor (config.host_deploy) si el componente está desplegado, con jboss-cli por SSH.
//   jboss-cli   : <jboss_home>/bin/jboss-cli.sh --connect -u=... -p=... --command="deployment-info --name=<componente>*"
//   jboss_home  : config.jboss_home (default '/opt/jboss-eap')
//   credencial  : usuario/clave de administración de JBoss = credencial Username with password config.cli_credentials_id (default 'jboss-cli')
//   SSH         : <JBOSS_SSH_USER>@<host_deploy> con la llave del nodo de Jenkins
// Devuelve true si hay un deployment cuyo nombre empieza con el componente (ej. Test-Java.war), false si no está.
// Falla (❌) si no se puede conectar a JBoss o la autenticación falla: en ese caso no se sabe si está desplegado.
Boolean call(String component_name) {
    def host           = String.valueOf(config.host_deploy ?: '').trim()
    def ssh_user       = String.valueOf(config.user ?: env.JBOSS_SSH_USER ?: '').trim()
    def jboss_home     = String.valueOf(config.jboss_home ?: '/opt/jboss-eap').trim().replaceFirst(/\/+$/, '')
    def credentials_id = String.valueOf(config.cli_credentials_id ?: 'jboss-cli').trim()

    if (!host) {
        error('[ERROR]: ❌ Falta jboss_cd.host_deploy en la config del servidor.')
    }
    if (!ssh_user) {
        error('[ERROR]: ❌ Falta el usuario SSH de JBoss: definir la propiedad de carpeta JBOSS_SSH_USER (o jboss_cd.user en la config).')
    }
    if (!component_name?.trim() || component_name.contains("'") || component_name.contains(' ')) {
        error("[ERROR]: ❌ Nombre de componente no válido para jboss-cli: '${component_name}'.")
    }

    println("[INFO]: Verificando si '${component_name}' está desplegado en JBoss (${host})")

    def output = ''
    withCredentials([usernamePassword(credentialsId: credentials_id,
                                      usernameVariable: 'JBOSS_CLI_USER',
                                      passwordVariable: 'JBOSS_CLI_PASSWORD')]) {
        // Usuario y clave como variables del SHELL (\$...): no se interpolan en Groovy ni quedan en el log.
        // 'echo p' acepta el certificado del servidor si jboss-cli lo pregunta. "|| true": la salida se analiza abajo.
        output = sh(script: """#!/bin/sh
ssh -o BatchMode=yes -o ConnectTimeout=15 ${ssh_user}@${host} "echo p | ${jboss_home}/bin/jboss-cli.sh --connect -u='\$JBOSS_CLI_USER' -p='\$JBOSS_CLI_PASSWORD' --command='deployment-info --name=${component_name}*'" 2>&1 || true""",
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

    // Fila de la tabla de deployment-info cuyo nombre (primera columna) empieza con el componente
    def lines = output.split('\n')
    for (int i = 0; i < lines.length; i++) {
        def first_column = lines[i].trim().split(/\s+/)[0]
        if (first_column.startsWith(component_name)) {
            println("[INFO]: ✅ Componente '${component_name}' desplegado en JBoss (${first_column})")
            return true
        }
    }

    println("[WARN]: ⚠️ Componente '${component_name}' no encontrado en deployment-info de ${host}")
    return false
}
