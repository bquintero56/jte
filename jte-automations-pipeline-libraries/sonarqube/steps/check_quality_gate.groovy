// Espera el resultado del Quality Gate del análisis que acaba de subir scan_code_quality().
// SonarQube avisa a Jenkins por el webhook global (https://<jenkins>/sonarqube-webhook/).
//   Pasó          → ✅
//   No pasó       → enforce_quality_gates = true: ❌ falla el pipeline / false: ⚠️ UNSTABLE y sigue
//   Sin respuesta → al cumplirse quality_gate_minutes (default 5) falla por timeout (revisar el webhook en SonarQube)
void call() {
    def minutes = config.quality_gate_minutes ?: 5

    try {
        def quality_gate = null
        timeout(time: minutes, unit: 'MINUTES') {
            quality_gate = waitForQualityGate(abortPipeline: false)
        }

        if (quality_gate.status == 'OK') {
            echo '✅ Quality Gate aprobado'
        }
        else if (config.enforce_quality_gates == true) {
            error("❌ Quality Gate NO aprobado (estado: ${quality_gate.status}). Revisar el proyecto en SonarQube.")
        }
        else {
            unstable("⚠️ Quality Gate NO aprobado (estado: ${quality_gate.status}). El pipeline continúa (enforce_quality_gates = false).")
        }
    }
    catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
        // timeout esperando el webhook también llega como FlowInterruptedException
        echo "❌ No llegó el resultado del Quality Gate en ${minutes} min: revisar el webhook global de SonarQube (https://<jenkins>/sonarqube-webhook/)."
        throw e
    }
    catch (Exception e) {
        error("${e.message ?: e.toString()}")
    }
}
