// Informa en GitLab el estado 'state' (pending, running, success, failed, canceled) para el nombre 'name' en el commit clonado.
// No hace nada si no hay conexión (propiedad de carpeta GITLAB_CONNECTION) o si todavía no se clonó el código.
// Nunca hace fallar el build: si GitLab no responde solo deja un aviso en el log.
void call(String name, String state) {
    if (!env.GITLAB_CONNECTION || !env.GITLAB_STATUS_COMMIT) {
        return
    }
    try {
        updateGitlabCommitStatus(name: name, state: state)
    }
    catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
        throw e
    }
    catch (Exception e) {
        echo "⚠️ No se pudo informar a GitLab '${name}' → ${state}: ${e.message ?: e.toString()}"
    }
}
