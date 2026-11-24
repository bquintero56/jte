// Espera el resultado del Quality Gate del análisis que acaba de subir scan_code_quality().
// SonarQube avisa a Jenkins por el webhook global (https://<jenkins>/sonarqube-webhook/).
//   Pasó          → ✅
//   No pasó       → enforce_quality_gates = true: ❌ falla el pipeline / false: ⚠️ UNSTABLE y sigue
//   Sin respuesta → al cumplirse quality_gate_minutes (default 5) falla por timeout (revisar el webhook en SonarQube)
// Antes de evaluar el resultado pone los TAGS del proyecto en SonarQube (set_project_tags): aquí el proyecto ya existe,
// porque SonarQube terminó de procesar el análisis (en el primer análisis de un micro el proyecto se crea en ese momento).
void call() {
    def minutes = config.quality_gate_minutes ?: 5

    try {
        def quality_gate = null
        timeout(time: minutes, unit: 'MINUTES') {
            quality_gate = waitForQualityGate(abortPipeline: false)
        }

        set_project_tags()

        if (quality_gate.status == 'OK') {
            println('[INFO]: ✅ Quality Gate aprobado')
        }
        else if (config.enforce_quality_gates == true) {
            error("[ERROR]: ❌ Quality Gate NO aprobado (estado: ${quality_gate.status}). Revisar el proyecto en SonarQube.")
        }
        else {
            unstable("[WARN]: ⚠️ Quality Gate NO aprobado (estado: ${quality_gate.status}). El pipeline continúa (enforce_quality_gates = false).")
        }
    }
    catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
        // timeout esperando el webhook también llega como FlowInterruptedException
        println("[ERROR]: ❌ No llegó el resultado del Quality Gate en ${minutes} min: revisar el webhook global de SonarQube (https://<jenkins>/sonarqube-webhook/).")
        throw e
    }
    catch (Exception e) {
        error("${e.message ?: e.toString()}")
    }
}

// Pone los tags del proyecto en SonarQube con la Web API (api/project_tags/set), para filtrar los proyectos por país en la pantalla
// Projects de SonarQube (filtro "Tags").
//   Tags: el PAÍS = primera carpeta de la ruta del job (JOB_NAME, ej. 'mexico/<appcode>/<repo>/integration' → 'mexico')
//         + los de config.project_tags (opcional). SonarQube los guarda en minúsculas; caracteres no válidos → '-'.
//   Reemplaza los tags que tenga el proyecto (los administra el pipeline, no se ponen a mano).
//   Token: el del servidor Sonar_server_SCAN_SERVER en Jenkins (withSonarQubeEnv). Su usuario necesita permiso "Administer" en el
//   proyecto; si no lo tiene (403) → [WARN] + UNSTABLE y el pipeline sigue (los tags no bloquean el pipeline).
void set_project_tags() {
    def project_key = ''
    try { project_key = component_name } catch (MissingPropertyException e) { project_key = '' }

    def job_path = String.valueOf(env.JOB_NAME ?: '').tokenize('/')
    def country  = job_path ? job_path[0] : ''
    def tags    = ([country] + (config.project_tags ?: [])).collect { String.valueOf(it).trim().toLowerCase().replaceAll(/[^a-z0-9+#.\-]/, '-') }
                                                          .findAll { it }
                                                          .unique()

    if (!project_key || !tags) {
        println("[WARN]: ⚠️ No se ponen tags en SonarQube: falta el proyecto ('${project_key}') o los tags ('${tags.join(',')}').")
        return
    }

    println("[INFO]: Tags del proyecto '${project_key}' en SonarQube: ${tags.join(', ')}")

    withSonarQubeEnv('Sonar_server_SCAN_SERVER') {
        withEnv(["SONAR_PROJECT=${project_key}", "SONAR_TAGS=${tags.join(',')}"]) {
            // Token como variable del SHELL: no se interpola en Groovy ni queda en el log
            def status_code = sh(script: '''#!/bin/sh
respuesta="${WORKSPACE:-.}/jte-sonar-tags.json"
http=$(curl -sS -o "$respuesta" -w "%{http_code}" -u "$SONAR_AUTH_TOKEN:" -X POST \
    --data-urlencode "project=$SONAR_PROJECT" --data-urlencode "tags=$SONAR_TAGS" \
    "$SONAR_HOST_URL/api/project_tags/set")
if [ "$http" -ge 400 ] 2>/dev/null || [ -z "$http" ]; then
    echo "[WARN]: SonarQube respondió HTTP $http al poner los tags:"
    cat "$respuesta" 2>/dev/null; echo
    rm -f "$respuesta"
    exit 1
fi
rm -f "$respuesta"
''', returnStatus: true)

            if (status_code == 0) {
                println("[INFO]: ✅ Tags puestos en SonarQube: ${tags.join(', ')}")
            }
            else {
                unstable("[WARN]: ⚠️ No se pudieron poner los tags en SonarQube (ver el código HTTP arriba). 403 = el usuario del token del " +
                         "servidor Sonar_server_SCAN_SERVER no tiene permiso 'Administer' en el proyecto '${project_key}'. El pipeline continúa.")
            }
        }
    }
}
