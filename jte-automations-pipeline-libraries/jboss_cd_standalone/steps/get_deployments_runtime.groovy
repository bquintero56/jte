// Lista TODOS los deployments del JBoss standalone del servidor (config.host_deploy) con su estado de runtime, con jboss-cli por SSH.
//   jboss-cli   : <jboss_home>/bin/jboss-cli.sh --connect -u=... -p=...
//                 --command=':read-children-resources(child-type=deployment,include-runtime=true)'
//   jboss_home  : config.jboss_home (default '/opt/jboss-eap')
//   credencial  : usuario/clave de administración de JBoss = credencial Username with password config.cli_credentials_id (default 'jboss-cli')
//   SSH         : <JBOSS_SSH_USER>@<host_deploy> con la llave del nodo de Jenkins
// Salida de jboss-cli (formato DMR):
//   {
//       "outcome" => "success",
//       "result" => {
//           "Test-Java.war" => {
//               "enabled" => true,
//               "name" => "Test-Java.war",
//               "persistent" => true,
//               "runtime-name" => "Test-Java.war",
//               "status" => "OK",
//               ...
//           }
//       }
//   }
// Devuelve una lista de mapas, uno por deployment (lista vacía si no hay ninguno):
//   name, runtime_name, enabled (Boolean), persistent (Boolean), status (OK, STOPPED, FAILED...), running (enabled y status = OK)
// Falla (❌) si no se puede conectar a JBoss, la autenticación falla o la operación no responde "success".
List call() {
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

    println("[INFO]: Consultando los deployments de JBoss standalone (${host})")

    def cli_command = ':read-children-resources(child-type=deployment,include-runtime=true)'
    println("[INFO]: Comando enviado: ssh -o BatchMode=yes -o ConnectTimeout=15 ${ssh_user}@${host} \"echo p | ${jboss_home}/bin/jboss-cli.sh --connect -u=*** -p=*** --command='${cli_command}'\"")

    def output = ''
    withCredentials([usernamePassword(credentialsId: credentials_id,
                                      usernameVariable: 'JBOSS_CLI_USER',
                                      passwordVariable: 'JBOSS_CLI_PASSWORD')]) {
        // Usuario y clave como variables del SHELL (\$...): no se interpolan en Groovy ni quedan en el log.
        // 'echo p' acepta el certificado del servidor si jboss-cli lo pregunta. "|| true": la salida se analiza abajo.
        output = sh(script: """#!/bin/sh
ssh -o BatchMode=yes -o ConnectTimeout=15 ${ssh_user}@${host} "echo p | ${jboss_home}/bin/jboss-cli.sh --connect -u='\$JBOSS_CLI_USER' -p='\$JBOSS_CLI_PASSWORD' --command='${cli_command}'" 2>&1 || true""",
                    returnStdout: true).trim()
    }

    println("[INFO]: Salida de jboss-cli:\n${output}")

    // Errores de conexión / autenticación / operación
    def lower = output.toLowerCase()
    if (lower.contains('failed to connect') || lower.contains('unable to connect') || lower.contains('connection refused') ||
        lower.contains('authentication failed') || lower.contains('unable to authenticate') || lower.contains('permission denied') ||
        lower.contains('no such file or directory')) {
        error("[ERROR]: ❌ No se pudo consultar JBoss en ${host} (conexión, autenticación o ruta de jboss-cli). Ver la salida arriba.")
    }
    if (!output.find(/"outcome"\s*=>\s*"success"/)) {
        error("[ERROR]: ❌ jboss-cli no respondió \"outcome\" => \"success\" en ${host}. Ver la salida arriba.")
    }

    def deployments = parse_deployments(output)

    // Resumen
    if (!deployments) {
        println("[INFO]: No hay deployments en ${host}")
    }
    else {
        println("[INFO]: Deployments en ${host} (${deployments.size()}):")
        deployments.each { d ->
            def icon = d.running ? '✅' : '⚠️'
            println("[INFO]: ${icon} ${d.name} | runtime-name=${d.runtime_name} | enabled=${d.enabled} | status=${d.status} | persistent=${d.persistent}")
        }
        def stopped = deployments.findAll { !it.running }
        if (stopped) {
            println("[WARN]: ⚠️ ${stopped.size()} deployment(s) que NO están funcionando: ${stopped.collect { it.name }.join(', ')}")
        }
    }
    return deployments
}

// Lee la salida DMR de :read-children-resources(child-type=deployment,...). Cuenta llaves para saber el nivel:
// nivel 1 = dentro de "result", nivel 2 = dentro de cada deployment (ahí están enabled, status, runtime-name...).
// Ignora los atributos más profundos (subsystem, content...).
@NonCPS
List parse_deployments(String output) {
    def deployments = []
    def current     = null
    def in_result   = false
    def depth       = 0

    output.readLines().each { raw ->
        def line = raw.trim()

        if (!in_result) {
            if (line ==~ /"result"\s*=>\s*\{.*/) {
                in_result = true
                depth     = 1
                if (line.contains('}')) {          // "result" => {} (sin deployments)
                    in_result = false
                }
            }
            return
        }

        if (depth == 1) {
            def start = (line =~ /^"([^"]+)"\s*=>\s*\{/)
            if (start.find()) {
                current = [name: start.group(1), runtime_name: '', enabled: false, persistent: false, status: 'UNKNOWN', running: false]
                deployments << current
            }
        }
        else if (depth == 2 && current != null) {
            def attr = (line =~ /^"([^"]+)"\s*=>\s*"?([^",]*)"?,?$/)
            if (attr.find()) {
                def key   = attr.group(1)
                def value = attr.group(2).trim()
                if (key == 'runtime-name') { current.runtime_name = value }
                if (key == 'enabled')      { current.enabled      = (value == 'true') }
                if (key == 'persistent')   { current.persistent   = (value == 'true') }
                if (key == 'status')       { current.status       = value }
            }
        }

        // Actualiza el nivel con las llaves de la línea
        depth += line.count('{') - line.count('}')
        if (depth <= 0) {
            in_result = false               // fin de "result"
        }
    }

    deployments.each { it.running = it.enabled && it.status == 'OK' }
    return deployments
}
