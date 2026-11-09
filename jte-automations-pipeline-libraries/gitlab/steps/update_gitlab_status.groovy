// Informa en GitLab el estado del pipeline para el commit clonado (se ve junto al commit y en los merge requests).
// state: 'running', 'success', 'failed' o 'canceled'.
// Requiere: haber hecho checkout_code() antes (el plugin toma el commit y el proyecto del repo clonado)
// y la propiedad gitLabConnection en el job (la registra el template con GITLAB_CONNECTION).
// Si GitLab no responde solo deja un aviso en el log: el estado nunca hace fallar el build.
void call(String state) {
    def name = "Jenkins CI (${env.JOB_BASE_NAME})"
    try {
        updateGitlabCommitStatus(name: name, state: state)
        echo "✅ Estado en GitLab: ${name} → ${state}"
    }
    catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
        throw e
    }
    catch (Exception e) {
        echo "⚠️ No se pudo actualizar el estado en GitLab (${state}): ${e.message ?: e.toString()}"
    }
}
