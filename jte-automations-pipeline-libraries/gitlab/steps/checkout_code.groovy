// Clona el repo de GitLab en el workspace y hace checkout de la rama del evento.
//   Rama: parámetro BRANCH (lo manda el orquestador del webhook); si no viene, default_branch (ej. dev.short_name).
//   Repo: <server_url>/<project_path>/<repository_name>.git con la credencial credentials_id.
// Devuelve un mapa con branch, commit y url.
Map call(String default_branch) {
    def branch = (params.BRANCH ?: env.BRANCH ?: default_branch ?: '').trim()
    def url    = "${config.server_url.replaceFirst(/\/+$/, '')}/${config.project_path.trim()}/${config.repository_name.trim()}.git"

    if (!branch) {
        error('❌ No se indicó la rama a clonar (parámetro BRANCH vacío y sin rama por defecto).')
    }

    echo "Clonando ${url} rama ${branch}"

    try {
        def scm_vars = checkout([
            $class           : 'GitSCM',
            branches         : [[name: "refs/heads/${branch}"]],
            userRemoteConfigs: [[url: url, credentialsId: config.credentials_id]],
            extensions       : [
                [$class: 'CloneOption', noTags: true, shallow: false, timeout: 10],
                [$class: 'CleanBeforeCheckout'],
                [$class: 'LocalBranch', localBranch: branch]     // deja el repo en la rama (no en "detached HEAD")
            ]
        ])

        def commit = scm_vars.GIT_COMMIT ?: ''
        echo "✅ Código descargado: ${config.project_path}/${config.repository_name} @ ${branch} (commit ${commit.length() > 8 ? commit.substring(0, 8) : commit})"
        return [branch: branch, commit: scm_vars.GIT_COMMIT, url: url]
    }
    catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
        throw e
    }
    catch (Exception e) {
        error("❌ No se pudo clonar ${url} rama '${branch}': ${e.message ?: e.toString()}")
    }
}
