// Valida la promoción de ramas de un Merge Request y le informa el resultado a GitLab (estado "Validación de promoción").
// Con "Pipelines must succeed" activo en el repo, un ❌ bloquea el botón Merge del MR.
// Flujo estándar, sin excepciones (no se permiten hotfix):
//   dev  ← cualquier rama (es la rama inicial del flujo)
//   qa   ← solo dev
//   uat  ← solo qa
//   main ← solo uat
//   otro destino → sin regla: ✅
// Librería webhook. Lo llama el pipeline merge_request_validator (job aparte del orquestador de pushes) en cada MR abierto o actualizado.
void call(String source_branch, String target_branch) {
    def status_name     = 'Validación de promoción'
    def promotion_rules = ['qa': 'dev', 'uat': 'qa', 'main': 'uat']   // destino: único origen permitido (dev no tiene regla)

    def source = String.valueOf(source_branch ?: '').trim()
    def target = String.valueOf(target_branch ?: '').trim()

    stage('Webhook: validar promoción del MR') {
        println("[INFO]: Merge Request: '${source}' → '${target}'")
        updateGitlabCommitStatus(name: status_name, state: 'running')

        def valid   = true
        def message = ''

        if (promotion_rules.containsKey(target)) {
            valid   = (source == promotion_rules[target])
            message = valid ? "promoción válida: '${source}' → '${target}'"
                            : "a '${target}' solo se llega desde '${promotion_rules[target]}', no desde '${source}'"
        }
        else if (target == 'dev') {
            message = "'${source}' → 'dev' (dev es la rama inicial: acepta cualquier rama)"
        }
        else {
            message = "destino '${target}' sin regla de promoción"
        }

        if (valid) {
            updateGitlabCommitStatus(name: status_name, state: 'success')
            println("[INFO]: ✅ MR permitido: ${message}")
        }
        else {
            updateGitlabCommitStatus(name: status_name, state: 'failed')
            error("[ERROR]: ❌ MR rechazado: ${message}. Flujo: rama de trabajo → dev → qa → uat → main.")
        }
    }
}
