// Informa en GitLab el estado 'state' (pending, running, success, failed, canceled) para el nombre 'name' en el commit clonado.
// Usa la conexión GitLab declarada en properties() del template. No hace nada si todavía no se clonó el código.
// Nunca hace fallar el build: si GitLab no responde solo deja un aviso en el log.
void call(String name, String state) {
    if (!env.GITLAB_STATUS_COMMIT) {
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
