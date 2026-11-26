// Retira del JBoss domain del servidor (config.host_deploy) el deployment <COMPONENT_NAME>.<packaging> (ej. Test-Java.war).
//   Comando   : undeploy <componente>.<empaquetado> --all-relevant-server-groups  (retira de todos los server groups donde esté)
//   jboss-cli : <jboss_home>/bin/jboss-cli.sh (default /opt/jboss-eap), credencial config.cli_credentials_id (default 'jboss-cli')
//   SSH       : <JBOSS_SSH_USER>@<host_deploy> con la llave del nodo de Jenkins
// El template lo llama solo si get_deployment_info dijo que existe (antes del deploy del código nuevo).
// config.dry_run = true → SIMULACIÓN: muestra el comando y NO toca JBoss.
// Devuelve true si retiró el deployment y false si fue simulación.
Boolean call(String component_name, String component_packaging) {
    def host           = String.valueOf(config.host_deploy ?: '').trim()
    def ssh_user       = String.valueOf(config.user ?: env.JBOSS_SSH_USER ?: '').trim()
    def jboss_home     = String.valueOf(config.jboss_home ?: '/opt/jboss-eap').trim().replaceFirst(/\/+$/, '')
    def credentials_id = String.valueOf(config.cli_credentials_id ?: 'jboss-cli').trim()
    def name           = String.valueOf(component_name ?: '').trim()
    def packaging      = String.valueOf(component_packaging ?: '').trim()

    // Validaciones
    if (!name || !packaging) {
        error('[ERROR]: ❌ Faltan los parámetros COMPONENT_NAME / COMPONENT_PACKAGING (este job lo lanza el CI del micro).')
    }
    if (name.contains('/') || name.contains('..') || name.contains(' ') || name.contains("'") || packaging.contains(' ')) {
        error("[ERROR]: ❌ COMPONENT_NAME / COMPONENT_PACKAGING no válidos: '${name}' / '${packaging}'.")
    }
    if (!ssh_user) {
        error('[ERROR]: ❌ Falta el usuario SSH de JBoss: definir la propiedad de carpeta JBOSS_SSH_USER (o jboss_cd_domain.user en la config).')
    }

    def deployment_name = "${name}.${packaging}"
    def cli_command     = "undeploy ${deployment_name} --all-relevant-server-groups"

    // Simulación (config.dry_run = true): muestra el comando y NO toca JBoss
    if (config.dry_run == true) {
        println("[WARN]: ⚠️ SIMULACIÓN (jboss_cd_domain.dry_run = true): NO se retira nada en ${host}.")
        println("[INFO]: Comando que se ejecutaría en ${host}: ${jboss_home}/bin/jboss-cli.sh --connect --command='${cli_command}'")
        return false
    }

    println("[INFO]: Retirando '${deployment_name}' de JBoss domain (${host})")

    println("[INFO]: Comando enviado: ssh -o BatchMode=yes -o ConnectTimeout=15 ${ssh_user}@${host} \"echo p | ${jboss_home}/bin/jboss-cli.sh --connect -u=*** -p=*** --command='${cli_command}'\"")

    def status_code = 0
    withCredentials([usernamePassword(credentialsId: credentials_id,
                                      usernameVariable: 'JBOSS_CLI_USER',
                                      passwordVariable: 'JBOSS_CLI_PASSWORD')]) {
        // Usuario y clave como variables del SHELL (\$...): no se interpolan en Groovy ni quedan en el log
        status_code = sh(script: """#!/bin/sh
ssh -o BatchMode=yes -o ConnectTimeout=15 ${ssh_user}@${host} "echo p | ${jboss_home}/bin/jboss-cli.sh --connect -u='\$JBOSS_CLI_USER' -p='\$JBOSS_CLI_PASSWORD' --command='${cli_command}'" 2>&1""",
                         returnStatus: true)
    }

    if (status_code != 0) {
        error("[ERROR]: ❌ Falló el undeploy de '${deployment_name}' en ${host} (código ${status_code}). Ver la salida de jboss-cli arriba. No se despliega el código nuevo.")
    }

    println("[INFO]: ✅ '${deployment_name}' retirado de JBoss (${host})")
    return true
}
