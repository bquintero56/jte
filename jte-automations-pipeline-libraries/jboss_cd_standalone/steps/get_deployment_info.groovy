// Consulta en el JBoss standalone del servidor (config.host_deploy) el estado del deployment del componente, con jboss-cli por SSH.
//   jboss-cli   : <jboss_home>/bin/jboss-cli.sh --connect -u=... -p=... --command="deployment-info --name=<componente>*"
//   jboss_home  : config.jboss_home (default '/opt/jboss-eap')
//   credencial  : usuario/clave de administración de JBoss = credencial Username with password config.cli_credentials_id (default 'jboss-cli')
//   SSH         : <JBOSS_SSH_USER>@<host_deploy> con la llave del nodo de Jenkins
// Salida de deployment-info (standalone):
//   NAME           RUNTIME-NAME   PERSISTENT ENABLED STATUS
//   Test-Java.war  Test-Java.war  true       false   STOPPED
// Devuelve un mapa:
//   exists  : true si hay un deployment <componente>.<empaquetado> (aunque esté detenido)
//   name    : nombre del deployment (ej. 'Test-Java.war'), '' si no existe
//   enabled : true si ENABLED = true
//   status  : STATUS (OK, STOPPED, FAILED...), '' si no existe
//   running : true si enabled y status = OK (desplegado y funcionando)
// Falla (❌) si no se puede conectar a JBoss o la autenticación falla: en ese caso no se sabe el estado.
Map call(String component_name) {
    def host           = String.valueOf(config.host_deploy ?: '').trim()
    def ssh_user       = String.valueOf(config.user ?: env.JBOSS_SSH_USER ?: '').trim()
    def jboss_home     = String.valueOf(config.jboss_home ?: '/opt/jboss-eap').trim().replaceFirst(/\/+$/, '')
    def credentials_id = String.valueOf(config.cli_credentials_id ?: 'jboss-cli').trim()

    if (!host) {
        error('[ERROR]: ❌ Falta jboss_cd_standalone.host_deploy en la config del servidor.')
    }
    if (!ssh_user) {
        error('[ERROR]: ❌ Falta el usuario SSH de JBoss: definir la propiedad de carpeta JBOSS_SSH_USER (o jboss_cd_standalone.user en la config).')
    }
    if (!component_name?.trim()) {
        error('[ERROR]: ❌ Falta el parámetro COMPONENT_NAME (este job lo lanza el CI del micro con COMPONENT_NAME y COMPONENT_PACKAGING).')
    }
    if (component_name.contains("'") || component_name.contains(' ')) {
        error("[ERROR]: ❌ Nombre de componente no válido para jboss-cli: '${component_name}'.")
    }

    println("[INFO]: Consultando el estado de '${component_name}' en JBoss standalone (${host})")

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

    def info  = [exists: false, name: '', enabled: false, status: '', running: false]
    def lines = output.split('\n')
    for (int i = 0; i < lines.length; i++) {
        def columns = lines[i].trim().split(/\s+/)
        // Nombre exacto: <componente>.<empaquetado> (ej. Test-Java.war); no confunde Test-Java con Test-Java-Admin.war
        if (columns[0].startsWith(component_name + '.')) {
            info.exists  = true
            info.name    = columns[0]
            info.enabled = columns.length > 3 && columns[3] == 'true'
            info.status  = columns.length > 4 ? columns[4] : 'UNKNOWN'
            info.running = info.enabled && info.status == 'OK'
            break
        }
    }

    if (!info.exists) {
        println("[INFO]: '${component_name}' no está desplegado en ${host}")
    }
    else if (info.running) {
        println("[INFO]: ✅ '${info.name}' desplegado y funcionando (ENABLED=true, STATUS=OK)")
    }
    else {
        println("[WARN]: ⚠️ '${info.name}' existe pero NO está funcionando (ENABLED=${info.enabled}, STATUS=${info.status})")
    }
    return info
}
