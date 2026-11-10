// Valida la promoción de ramas de un Merge Request. Si NO es válida, COMENTA el motivo en el MR y lo CIERRA (API de GitLab).
// No usa estados de commit: el commit de origen lo comparten todos los MR y los pipelines de la rama, así que un estado
// "failed" se pisa con el siguiente pipeline y no bloquea el merge. Cerrar el MR sí lo impide.
// Flujo estándar, sin excepciones (no se permiten hotfix):
//   dev  ← cualquier rama (es la rama inicial del flujo)
//   qa   ← solo dev
//   uat  ← solo qa
//   main ← solo uat
//   otro destino → sin regla: ✅
// API de GitLab: <GITLAB_URL>/api/v4 (propiedad de carpeta) con un token de permiso api guardado en Jenkins como "Secret text"
//   (config.api_token_credential, default 'gitlab-api-close-merge-request'). Datos del MR: gitlabMergeRequestTargetProjectId / gitlabMergeRequestIid.
// Librería webhook. Lo llama el pipeline merge_request_validator (job aparte del orquestador de pushes) en cada MR abierto o actualizado.
void call(String source_branch, String target_branch) {
    def promotion_rules = ['qa': 'dev', 'uat': 'qa', 'main': 'uat']   // destino: único origen permitido (dev no tiene regla)

    def source = String.valueOf(source_branch ?: '').trim()
    def target = String.valueOf(target_branch ?: '').trim()

    stage('Webhook: validar promoción del MR') {
        println("[INFO]: Merge Request !${env.gitlabMergeRequestIid}: '${source}' → '${target}'")

        if (!promotion_rules.containsKey(target)) {
            println("[INFO]: ✅ MR permitido: ${target == 'dev' ? "'dev' es la rama inicial: acepta cualquier rama" : "destino '${target}' sin regla de promoción"}")
            return
        }
        if (source == promotion_rules[target]) {
            println("[INFO]: ✅ MR permitido: promoción válida '${source}' → '${target}'")
            return
        }

        // Promoción NO válida: comentar y cerrar el MR
        def reason = "a '${target}' solo se llega desde '${promotion_rules[target]}', no desde '${source}'"
        close_invalid_merge_request(reason)
        error("[ERROR]: ❌ MR !${env.gitlabMergeRequestIid} rechazado y CERRADO: ${reason}. Flujo: dev → qa → uat → main.")
    }
}

// Comenta el motivo en el MR y lo cierra con la API de GitLab.
void close_invalid_merge_request(String reason) {
    def server        = String.valueOf(env.GITLAB_URL ?: '').trim().replaceFirst(/\/+$/, '')
    def project_id    = String.valueOf(env.gitlabMergeRequestTargetProjectId ?: '').trim()
    def mr_iid        = String.valueOf(env.gitlabMergeRequestIid ?: '').trim()
    def credential_id = String.valueOf(config.api_token_credential ?: 'gitlab-api-close-merge-request').trim()

    if (!server || !project_id || !mr_iid) {
        error("[ERROR]: ❌ No se puede cerrar el MR: falta GITLAB_URL (propiedad de carpeta) o los datos del MR del webhook (proyecto '${project_id}', MR '${mr_iid}').")
    }

    def api     = "${server}/api/v4/projects/${project_id}/merge_requests/${mr_iid}"
    def comment = "❌ **Merge Request cerrado automáticamente por Jenkins**: ${reason}.\n\n" +
                  "El flujo de promoción es **rama de trabajo → dev → qa → uat → main**, sin excepciones. " +
                  "Crea el MR desde la rama correcta."

    println("[INFO]: Comentando y cerrando el MR !${mr_iid} (proyecto ${project_id})")

    withCredentials([string(credentialsId: credential_id, variable: 'GITLAB_API_TOKEN')]) {
        withEnv(["MR_API=${api}", "MR_COMMENT=${comment}"]) {
            // Token como variable del SHELL (\$...): no se interpola en Groovy ni queda en el log
            // Si GitLab responde >= 400, muestra el código HTTP y su mensaje (sin el token) para diagnosticar
            def status_code = sh(script: '''#!/bin/sh
call_api() {
    accion="$1"; shift
    http=$(curl -sS -o /tmp/jte_mr_api_$$.json -w "%{http_code}" "$@")
    if [ "$http" -ge 400 ] 2>/dev/null || [ -z "$http" ]; then
        echo "[ERROR]: GitLab respondió HTTP $http al $accion:"
        cat /tmp/jte_mr_api_$$.json 2>/dev/null; echo
        rm -f /tmp/jte_mr_api_$$.json
        return 1
    fi
    echo "[INFO]: GitLab respondió HTTP $http al $accion"
    rm -f /tmp/jte_mr_api_$$.json
}
call_api "comentar el MR" -X POST -H "PRIVATE-TOKEN: $GITLAB_API_TOKEN" --data-urlencode "body=$MR_COMMENT" "$MR_API/notes" || exit 1
call_api "cerrar el MR"   -X PUT  -H "PRIVATE-TOKEN: $GITLAB_API_TOKEN" --data "state_event=close" "$MR_API" || exit 1
''', returnStatus: true)

            if (status_code != 0) {
                error("[ERROR]: ❌ No se pudo comentar/cerrar el MR !${mr_iid} con la API de GitLab (ver el código HTTP y el mensaje arriba). " +
                      "401 = token inválido o vencido; 403 = token sin scope 'api' o su usuario sin rol Developer en el proyecto. Credencial: '${credential_id}'.")
            }
        }
    }
    println("[INFO]: ✅ MR !${mr_iid} comentado y cerrado")
}
