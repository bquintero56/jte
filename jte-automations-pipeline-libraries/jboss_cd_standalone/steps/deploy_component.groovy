// Despliega en el JBoss del servidor (config.host_deploy) el artefacto que dejó el CI en <JBOSS_TEMP_PATH>/<COMPONENT_NAME>.
//   Artefacto : único *.<component_packaging> en la carpeta temporal DEL SERVIDOR (jboss-cli corre en el servidor, ruta absoluta)
//   Modo      : STANDALONE (para domain usar la librería jboss_cd_domain)
//   Comando   : deploy <archivo> --name=<COMPONENT_NAME>.<packaging> --runtime-name=<COMPONENT_NAME>.<packaging> --force
//     --name         : nombre FIJO del deployment (aunque el archivo traiga versión): info, undeploy y verify siempre lo encuentran
//     --runtime-name : nombre con que corre; define el context root (URL) si el .war no lo trae, así la URL no cambia
//     --force        : respaldo: si aún existiera un deployment con ese nombre lo reemplaza (el template ya hace undeploy antes)
//   jboss-cli : <jboss_home>/bin/jboss-cli.sh (default /opt/jboss-eap), credencial config.cli_credentials_id (default 'jboss-cli')
//   SSH       : <JBOSS_SSH_USER>@<host_deploy> con la llave del nodo de Jenkins
// El template ejecuta undeploy_component antes si el componente ya existía.
// Devuelve true si desplegó y false si fue simulación (dry_run): el template solo verifica si devolvió true.
Boolean call(String component_name, String component_packaging) {
    def host           = String.valueOf(config.host_deploy ?: '').trim()
    def ssh_user       = String.valueOf(config.user ?: env.JBOSS_SSH_USER ?: '').trim()
    def jboss_home     = String.valueOf(config.jboss_home ?: '/opt/jboss-eap').trim().replaceFirst(/\/+$/, '')
    def credentials_id = String.valueOf(config.cli_credentials_id ?: 'jboss-cli').trim()
    def temp_base      = String.valueOf(env.JBOSS_TEMP_PATH ?: '').trim().replaceFirst(/\/+$/, '')
    def name           = String.valueOf(component_name ?: '').trim()
    def packaging      = String.valueOf(component_packaging ?: '').trim()

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
        error('[ERROR]: ❌ Falta el usuario SSH de JBoss: definir la propiedad de carpeta JBOSS_SSH_USER (o jboss_cd_standalone.user en la config).')
    }


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
    def deployment_name = "${name}.${packaging}"
    def cli_command     = "deploy ${artifact} --name=${deployment_name} --runtime-name=${deployment_name} --force"

    // Simulación (config.dry_run = true): muestra el comando y NO toca JBoss
    if (config.dry_run == true) {
        println("[WARN]: ⚠️ SIMULACIÓN (jboss_cd_standalone.dry_run = true): NO se despliega nada en ${host}.")
        println("[INFO]: Comando que se ejecutaría en ${host}: ${jboss_home}/bin/jboss-cli.sh --connect --command='${cli_command}'")
        return false
    }

    println("[INFO]: Desplegando ${artifact} como '${deployment_name}' en JBoss standalone (${host}); si ya existe se reemplaza (--force)")

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
        error("[ERROR]: ❌ Falló el deploy de ${artifact} en ${host} (código ${status_code}). Ver la salida de jboss-cli arriba.")
    }

    println("[INFO]: ✅ '${deployment_name}' desplegado en JBoss (${host})")
    return true
}
