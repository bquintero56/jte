// Ejecuta el (los) job(s) de CD que despliegan el componente en JBoss, uno detrás de otro.
//   project_names      : rutas de los jobs de CD (ej. dev.deployment_project = ['pais/jboss/app1/dev-deployment', ...])
//   custom_parameters  : parámetros para el job de CD (ej. [string(name: 'COMPONENT_NAME', value: ...), ...])
//   wait_job_execution : true = espera a que termine cada job y revisa su resultado; false = solo lo lanza
// Con wait = true:
//   SUCCESS  → ✅ sigue con el siguiente job
//   UNSTABLE → ⚠️ marca este build UNSTABLE y sigue
//   otro     → ❌ detiene el pipeline (no despliega en los siguientes)
// El resultado del último job queda en env.DOWNSTREAM_JOB_RESULT.
void call(List<String> project_names, List custom_parameters, Boolean wait_job_execution) {
    env.DOWNSTREAM_JOB_RESULT = 'STARTED'

    if (!project_names) {
        error('[ERROR]: ❌ No hay jobs de despliegue: revisar deployment_project del ambiente en la config.')
    }

    project_names.each { current_job ->
        println("[INFO]: Iniciando job de despliegue: ${current_job}")
        def execution = null

        try {
            // propagate: false → build no lanza excepción si el job falla; el resultado se revisa abajo
            execution = build(
                job       : current_job,
                parameters: custom_parameters,
                propagate : false,
                wait      : wait_job_execution
            )
        }
        catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
            env.DOWNSTREAM_JOB_RESULT = 'ABORTED'
            throw e
        }
        catch (Exception e) {
            // No se pudo lanzar (job inexistente, sin permisos, parámetros inválidos...)
            env.DOWNSTREAM_JOB_RESULT = 'FAILED'
            error("[ERROR]: ❌ No se pudo ejecutar el job ${current_job}: ${e.message ?: e.toString()}")
        }

        if (!wait_job_execution) {
            env.DOWNSTREAM_JOB_RESULT = 'TRIGGERED'
            println("[INFO]: ✅ Job ${current_job} lanzado (sin esperar a que termine)")
            return      // siguiente job del each
        }

        def result = execution.result ?: 'UNKNOWN'
        env.DOWNSTREAM_JOB_RESULT = result
        println("[INFO]: Job ${current_job} #${execution.number} terminó con resultado: ${result} (${execution.absoluteUrl})")

        if (result == 'SUCCESS') {
            println("[INFO]: ✅ Despliegue OK: ${current_job}")
        }
        else if (result == 'UNSTABLE') {
            unstable("[WARN]: ⚠️ El job ${current_job} terminó UNSTABLE. Se continúa con el siguiente.")
        }
        else {
            error("[ERROR]: ❌ El job ${current_job} terminó en ${result}. Se detiene el pipeline (no se despliega en los siguientes). Ver: ${execution.absoluteUrl}")
        }
    }

    println("[INFO]: ✅ Todos los jobs de despliegue completados. Resultado final: ${env.DOWNSTREAM_JOB_RESULT}")
}
