// Lanza el pipeline de la aplicación según la rama que llega en el webhook.
//   Rama de ambiente (dev, qa, uat, release, main) → <prefijoRuta>/<appcode>/<repo>/<rama>
//   Cualquier otra rama                             → <prefijoRuta>/<appcode>/<repo>/<jobIntegracion>
// Al job se le pasa la rama en el parámetro config.parametroRama. No espera a que termine.
void call(String branch_name, String repository_name, String appcode) {
    stage('Webhook: disparar pipeline de la aplicación') {
        def job_name = null

        if (branch_name in ['dev', 'qa', 'uat', 'release', 'main']) {
            job_name = "${config.prefijoRuta}/${appcode}/${repository_name}/${branch_name}"
        }
        else {
            job_name = "${config.prefijoRuta}/${appcode}/${repository_name}/${config.jobIntegracion}"
        }

        try {
            if (job_name == null) {
                error('❌ No se pudo armar job_name con los datos del webhook.')
            }

            def job_parameters = [string(name: config.parametroRama, value: branch_name)]

            echo "✅ Lanzando job de la aplicación: ${job_name} (${config.parametroRama}=${branch_name})"

            build(job: job_name, wait: false, parameters: job_parameters, propagate: false)
        }
        catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
            throw e
        }
        catch (Exception e) {
            error("❌ Falló el disparo del job '${job_name}': ${e.message ?: e.toString()}")
        }
    }
}
