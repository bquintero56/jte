// Igual que stage(name) { ... }, pero además informa el estado de la etapa en GitLab (junto al commit y en los merge requests).
//   running al empezar → success / failed / canceled al terminar.
//   Si la etapa deja el build en UNSTABLE (ej. checkstyle con errores) se informa 'failed' en GitLab: GitLab no tiene estado "warning".
// Requiere que checkout_code() se haya ejecutado (antes de eso no hay commit al que informar: la etapa corre igual, sin estado).
// El estado nunca hace fallar el build: si GitLab no responde solo queda un aviso en el log.
void call(String name, Closure body) {
    stage(name) {
        update_gitlab_status(name, 'running')
        def result_before = currentBuild.currentResult

        try {
            body()
        }
        catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
            update_gitlab_status(name, 'canceled')
            throw e
        }
        catch (Exception e) {
            update_gitlab_status(name, 'failed')
            throw e
        }

        def turned_unstable = (currentBuild.currentResult == 'UNSTABLE' && result_before != 'UNSTABLE')
        update_gitlab_status(name, turned_unstable ? 'failed' : 'success')
    }
}
