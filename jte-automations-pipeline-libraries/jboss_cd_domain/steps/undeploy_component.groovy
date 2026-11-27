// Retira del JBoss DOMAIN el deployment <COMPONENT_NAME>.<packaging> (ej. Test-Java.war) del server group del micro.
//   Comando     : [sudo -u <run_as_user>] <jboss_home>/bin/jboss-cli.sh --connect [--controller=<JBOSS_CONTROLLER>] -u=... -p=...
//                 --command="undeploy <componente>.<empaquetado> --server-groups=<SERVER_GROUP>"
//   server group: parámetro SERVER_GROUP (lo envía el CI desde <ambiente>.server_group de la config del micro). Obligatorio: vacío → ❌.
//   controller  : parámetro JBOSS_CONTROLLER (lo envía el CI desde <ambiente>.jboss_controller de la config del micro, host:puerto;
//                 sin puerto → 9990). Vacío = sin --controller (localhost:9990 del servidor al que entra por SSH).
//   run_as_user : config.run_as_user (ej. 'jboss-eap') → antepone 'sudo -u <usuario>' a jboss-cli. Vacío = sin sudo.
//   jboss-cli   : <jboss_home>/bin/jboss-cli.sh (default /opt/jboss-eap), credencial config.cli_credentials_id (default 'jboss-cli')
//   SSH         : <JBOSS_SSH_USER>@<host_deploy> con la llave del nodo de Jenkins
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
    def server_group   = String.valueOf(params.SERVER_GROUP ?: '').trim()

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

    def deployment_name = "${name}.${packaging}"
    def cli_command     = "undeploy ${deployment_name} --server-groups=${server_group}"

    // Simulación (config.dry_run = true): muestra el comando y NO toca JBoss
    if (config.dry_run == true) {
        println("[WARN]: ⚠️ SIMULACIÓN (jboss_cd_domain.dry_run = true): NO se retira nada en ${host}.")
        println("[INFO]: Comando que se ejecutaría en ${host}: ${run_as_prefix}${jboss_home}/bin/jboss-cli.sh --connect${controller_arg} --command='${cli_command}'")
        return false
    }

    println("[INFO]: Retirando '${deployment_name}' del server group '${server_group}' de JBoss domain (${host})")

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
        error("[ERROR]: ❌ Falló el undeploy de '${deployment_name}' en el server group '${server_group}' de ${host} (código ${status_code}). Ver la salida de jboss-cli arriba. No se despliega el código nuevo.")
    }

    println("[INFO]: ✅ '${deployment_name}' retirado del server group '${server_group}' (${host})")
    return true
}
