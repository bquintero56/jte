// Igual que stage(name) { ... }, pero además informa el estado de la etapa en GitLab (Build → Pipelines, junto al commit y en los MR).
//   running al empezar → success / failed / canceled al terminar.
//   Si la etapa deja el build en UNSTABLE (amarillo en Jenkins, ej. checkstyle con errores) se informa 'success' en GitLab:
//   GitLab no tiene estado "warning" y un 'failed' dejaría todo el pipeline en failed. La advertencia queda en Jenkins (log del stage).
// Requiere que checkout_code() se haya ejecutado (antes de eso no hay commit al que informar: la etapa corre igual, sin estado).
// El estado nunca hace fallar el build: si GitLab no responde solo queda un aviso en el log.
void call(String name, Closure body) {
    stage(name) {
        update_gitlab_status(name, 'running')

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

        update_gitlab_status(name, 'success')
    }
}
