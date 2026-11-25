// Server groups del domain donde se despliega / consulta el componente:
//   1) parámetro SERVER_GROUPS del job (lo envía el CI desde la config del MICRO: <ambiente>.server_groups), separados por coma
//   2) si viene vacío: jboss_cd_domain.server_groups de la config del SERVIDOR (respaldo)
// Falla (❌) si no hay ninguno o si un nombre no es válido para jboss-cli. Lo usan deploy_component y get_deployments_runtime.
List call() {
    def from_param = String.valueOf(params.SERVER_GROUPS ?: '').split(',').collect { it.trim() }.findAll { it }
    def from_config = (config.server_groups ?: []).collect { String.valueOf(it).trim() }.findAll { it }

    def groups = from_param ?: from_config
    def origin = from_param ? 'parámetro SERVER_GROUPS (config del micro)' : 'jboss_cd_domain.server_groups (config del servidor)'

    if (!groups) {
        error('[ERROR]: ❌ No hay server groups: definir <ambiente>.server_groups en la config del micro (llega como SERVER_GROUPS) ' +
              'o jboss_cd_domain.server_groups en la config del servidor.')
    }
    groups.each { group ->
        if (!(group ==~ /[A-Za-z0-9._-]+/)) {
            error("[ERROR]: ❌ Server group no válido para jboss-cli: '${group}'.")
        }
    }

    println("[INFO]: Server groups: ${groups.join(', ')} (${origin})")
    return groups
}
