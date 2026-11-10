// Informa en GitLab el estado 'state' (pending, running, success, failed, canceled) para el nombre 'name' en el commit clonado.
// Usa la conexión GitLab declarada en properties() del template. No hace nada si todavía no se clonó el código.
// Nunca hace fallar el build: si GitLab no responde solo deja un aviso en el log.
void call(String name, String state) {
    def commit = env.GITLAB_STATUS_COMMIT ?: ''
    if (!commit) {
        println("[INFO]: GitLab estado: '${name}' → ${state} omitido (todavía no hay commit clonado)")
        return
    }
    try {
        println("[INFO]: GitLab estado: '${name}' → ${state} (commit ${commit.length() > 8 ? commit.substring(0, 8) : commit})")
        updateGitlabCommitStatus(name: name, state: state)
    }
    catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
        throw e
    }
    catch (Exception e) {
        println("[WARN]: ⚠️ No se pudo informar a GitLab '${name}' → ${state}: ${e.message ?: e.toString()}")
    }
}
