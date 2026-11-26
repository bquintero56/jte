// Lista los deployments de cada server group de config.server_groups en el JBoss DOMAIN (config.host_deploy), con su estado en cada
// servidor del grupo, con jboss-cli por SSH. Equivalente de jboss_cd_standalone.get_deployments_runtime para modo domain.
//   jboss-cli (3 consultas):
//     1) por cada server group: /server-group=<grupo>:read-children-resources(child-type=deployment,include-runtime=true)
//        → deployments asignados al grupo (name, runtime-name, enabled). En domain el grupo NO tiene "status": está en cada servidor.
//     2) /host=*/server=*:read-attribute(name=server-group)               → a qué grupo pertenece cada servidor en ejecución
//     3) /host=*/server=*/deployment=*:read-attribute(name=status)        → estado de cada deployment en cada servidor (OK, FAILED...)
//   jboss_home  : config.jboss_home (default '/opt/jboss-eap')
//   credencial  : usuario/clave de administración de JBoss = credencial Username with password config.cli_credentials_id (default 'jboss-cli')
//   SSH         : <JBOSS_SSH_USER>@<host_deploy> con la llave del nodo de Jenkins
// Devuelve una lista de mapas, uno por deployment y server group (lista vacía si no hay ninguno):
//   server_group, name, runtime_name, enabled (Boolean),
//   servers : lista de [host, server, status] de los servidores EN EJECUCIÓN del grupo
//   running : enabled, al menos un servidor del grupo en ejecución y status = OK en todos ellos
// Falla (❌) si no se puede conectar a JBoss, la autenticación falla o una consulta no responde "success" (ej. server group inexistente).
List call() {
    def host           = String.valueOf(config.host_deploy ?: '').trim()
    def ssh_user       = String.valueOf(config.user ?: env.JBOSS_SSH_USER ?: '').trim()
    def jboss_home     = String.valueOf(config.jboss_home ?: '/opt/jboss-eap').trim().replaceFirst(/\/+$/, '')
    def credentials_id = String.valueOf(config.cli_credentials_id ?: 'jboss-cli').trim()
    def server_groups  = (config.server_groups ?: []).collect { String.valueOf(it).trim() }.findAll { it }

    if (!host) {
        error('[ERROR]: ❌ Falta jboss_cd_domain.host_deploy en la config del servidor.')
    }
    if (!ssh_user) {
        error('[ERROR]: ❌ Falta el usuario SSH de JBoss: definir la propiedad de carpeta JBOSS_SSH_USER (o jboss_cd_domain.user en la config).')
    }
    // Sin server_groups en la config: se consultan TODOS los server groups del domain
    if (!server_groups) {
        println('[INFO]: jboss_cd_domain.server_groups no está en la config: se consultan todos los server groups del domain')
        server_groups = parse_server_group_names(run_cli(host, ssh_user, jboss_home, credentials_id,
                                                         ':read-children-names(child-type=server-group)'))
        if (!server_groups) {
            println("[WARN]: ⚠️ El domain de ${host} no tiene server groups")
            return []
        }
    }
    server_groups.each { group ->
        if (!group.matches('[A-Za-z0-9._-]+')) {
            error("[ERROR]: ❌ Server group no válido para jboss-cli: '${group}'.")
        }
    }

    println("[INFO]: Consultando los deployments de los server groups ${server_groups.join(', ')} en JBoss domain (${host})")

    // 2) y 3): servidores en ejecución con su grupo, y estado de cada deployment en cada servidor (una vez para todos los grupos)
    def server_group_of = parse_server_groups(run_cli(host, ssh_user, jboss_home, credentials_id,
                                                      '/host=*/server=*:read-attribute(name=server-group)'))
    def status_of       = parse_deployment_status(run_cli(host, ssh_user, jboss_home, credentials_id,
                                                          '/host=*/server=*/deployment=*:read-attribute(name=status)'))

    // 1) deployments de cada server group
    def deployments = []
    server_groups.each { group ->
        def output = run_cli(host, ssh_user, jboss_home, credentials_id,
                             "/server-group=${group}:read-children-resources(child-type=deployment,include-runtime=true)")
        def group_servers = server_group_of.findAll { it.group == group }

        parse_deployments(output).each { d ->
            def servers = []
            group_servers.each { s ->
                def status = status_of["${s.host}/${s.server}/${d.name}".toString()] ?: 'NOT_DEPLOYED'
                servers << [host: s.host, server: s.server, status: status]
            }
            deployments << [server_group: group, name: d.name, runtime_name: d.runtime_name, enabled: d.enabled,
                            servers: servers,
                            running: d.enabled && servers && servers.findAll { it.status != 'OK' }.size() == 0]
        }

        if (!group_servers) {
            println("[WARN]: ⚠️ El server group '${group}' no tiene servidores en ejecución en ${host}")
        }
    }

    // Resumen
    if (!deployments) {
        println("[INFO]: No hay deployments en los server groups ${server_groups.join(', ')} de ${host}")
    }
    else {
        println("[INFO]: Deployments en ${host} (${deployments.size()}):")
        deployments.each { d ->
            def icon    = d.running ? '✅' : '⚠️'
            def servers = d.servers ? d.servers.collect { "${it.host}/${it.server}=${it.status}" }.join(', ') : 'sin servidores en ejecución'
            println("[INFO]: ${icon} [${d.server_group}] ${d.name} | runtime-name=${d.runtime_name} | enabled=${d.enabled} | ${servers}")
        }
        def stopped = deployments.findAll { !it.running }
        if (stopped) {
            println("[WARN]: ⚠️ ${stopped.size()} deployment(s) que NO están funcionando: ${stopped.collect { "[${it.server_group}] ${it.name}" }.join(', ')}")
        }
    }
    return deployments
}

// Ejecuta un comando de jboss-cli por SSH y devuelve la salida. Falla (❌) si no hay conexión/autenticación o si el
// comando no responde "outcome" => "success".
String run_cli(String host, String ssh_user, String jboss_home, String credentials_id, String cli_command) {
    // --controller del domain controller: parámetro JBOSS_CONTROLLER (lo envía el CI desde <ambiente>.jboss_controller de la config del
    // micro, host:puerto; sin puerto → 9990). Vacío = sin --controller (jboss-cli usa localhost:9990 del servidor al que entra por SSH).
    def controller = String.valueOf(params.JBOSS_CONTROLLER ?: '').trim()
    if (controller && !controller.matches('[A-Za-z0-9._-]+(:[0-9]{1,5})?')) {
        error("[ERROR]: ❌ JBOSS_CONTROLLER no válido: '${controller}' (formato host:puerto, ej. 10.0.0.5:9990).")
    }
    if (controller && !controller.contains(':')) {
        controller = controller + ':9990'
    }
    def controller_arg = controller ? " --controller=${controller}" : ''

    println("[INFO]: Comando enviado: ssh -o BatchMode=yes -o ConnectTimeout=15 ${ssh_user}@${host} \"echo p | ${jboss_home}/bin/jboss-cli.sh --connect${controller_arg} -u=*** -p=*** --command='${cli_command}'\"")

    def output = ''
    withCredentials([usernamePassword(credentialsId: credentials_id,
                                      usernameVariable: 'JBOSS_CLI_USER',
                                      passwordVariable: 'JBOSS_CLI_PASSWORD')]) {
        // Usuario y clave como variables del SHELL (\$...): no se interpolan en Groovy ni quedan en el log.
        // 'echo p' acepta el certificado del servidor si jboss-cli lo pregunta. "|| true": la salida se analiza abajo.
        output = sh(script: """#!/bin/sh
ssh -o BatchMode=yes -o ConnectTimeout=15 ${ssh_user}@${host} "echo p | ${jboss_home}/bin/jboss-cli.sh --connect${controller_arg} -u='\$JBOSS_CLI_USER' -p='\$JBOSS_CLI_PASSWORD' --command='${cli_command}'" 2>&1 || true""",
                    returnStdout: true).trim()
    }

    println("[INFO]: Salida de jboss-cli:\n${output}")

    def lower = output.toLowerCase()
    if (lower.contains('failed to connect') || lower.contains('unable to connect') || lower.contains('connection refused') ||
        lower.contains('authentication failed') || lower.contains('unable to authenticate') || lower.contains('permission denied') ||
        lower.contains('no such file or directory')) {
        error("[ERROR]: ❌ No se pudo consultar JBoss en ${host} (conexión, autenticación o ruta de jboss-cli). Ver la salida arriba.")
    }
    if (!output.find(/"outcome"\s*=>\s*"success"/)) {
        error("[ERROR]: ❌ jboss-cli no respondió \"outcome\" => \"success\" en ${host} para '${cli_command}' (¿server group inexistente?). Ver la salida arriba.")
    }
    return output
}

// Lee la salida DMR de /server-group=<g>:read-children-resources(child-type=deployment,...). Cuenta llaves para saber el nivel:
// nivel 1 = dentro de "result", nivel 2 = dentro de cada deployment (enabled, runtime-name...). Ignora lo más profundo.
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
                current = [name: start.group(1), runtime_name: '', enabled: false]
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
            }
        }

        depth += line.count('{') - line.count('}')
        if (depth <= 0) {
            in_result = false
        }
    }
    return deployments
}

// Salida de :read-children-names(child-type=server-group) → ['main-server-group', 'other-server-group']
//   "result" => ["main-server-group", "other-server-group"]
@NonCPS
List parse_server_group_names(String output) {
    def names  = []
    def result = (output =~ /(?s)"result"\s*=>\s*\[(.*?)\]/)
    if (result.find()) {
        def item = (result.group(1) =~ /"([^"]+)"/)
        while (item.find()) {
            names << item.group(1)
        }
    }
    return names
}

// Salida de /host=*/server=*:read-attribute(name=server-group) → [[host, server, group], ...] (solo servidores en ejecución).
//   "address" => [("host" => "master"), ("server" => "server-one")], "outcome" => "success", "result" => "main-server-group"
@NonCPS
List parse_server_groups(String output) {
    def servers = []
    def matcher = (output =~ /\("host"\s*=>\s*"([^"]+)"\),\s*\("server"\s*=>\s*"([^"]+)"\)\s*\],\s*"outcome"\s*=>\s*"success",\s*"result"\s*=>\s*"([^"]+)"/)
    while (matcher.find()) {
        servers << [host: matcher.group(1), server: matcher.group(2), group: matcher.group(3)]
    }
    return servers
}

// Salida de /host=*/server=*/deployment=*:read-attribute(name=status) → mapa 'host/server/deployment' → status (OK, FAILED, STOPPED...).
@NonCPS
Map parse_deployment_status(String output) {
    def status  = [:]
    def matcher = (output =~ /\("host"\s*=>\s*"([^"]+)"\),\s*\("server"\s*=>\s*"([^"]+)"\),\s*\("deployment"\s*=>\s*"([^"]+)"\)\s*\],\s*"outcome"\s*=>\s*"success",\s*"result"\s*=>\s*"?([^",\s}]+)"?/)
    while (matcher.find()) {
        status["${matcher.group(1)}/${matcher.group(2)}/${matcher.group(3)}".toString()] = matcher.group(4)
    }
    return status
}
