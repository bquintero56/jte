// Clona el repo de GitLab en el workspace y hace checkout de la rama del evento.
// Los jobs en Jenkins tienen la misma estructura que GitLab: el job 'mexico/APP01/Test-Java/dev' clona 'mexico/APP01/Test-Java'.
//   URL GitLab : propiedad de carpeta GITLAB_URL (global, se define una vez en la carpeta 'mexico' de Jenkins)
//   Credencial : propiedad de carpeta GITLAB_CREDENTIALS_ID (global)
//   Repo       : la ruta del job sin el último segmento (la rama / integration)
//   Rama       : parámetro BRANCH (lo manda el orquestador); si no viene, default_branch (ej. dev.short_name)
// Requiere estar dentro de withFolderProperties. Devuelve [branch, commit, url].
Map call(String default_branch) {
    def server      = (env.GITLAB_URL ?: '').trim().replaceFirst(/\/+$/, '')
    def credentials = (env.GITLAB_CREDENTIALS_ID ?: '').trim()
    def branch      = (params.BRANCH ?: env.BRANCH ?: default_branch ?: '').trim()
    def job         = env.JOB_NAME ?: ''
    def repo_path   = job.contains('/') ? job.substring(0, job.lastIndexOf('/')) : ''

    if (!server) {
        error('❌ Falta la propiedad de carpeta GITLAB_URL (ej. https://gitlab.empresa.com) en la carpeta de Jenkins.')
    }
    if (!credentials) {
        error('❌ Falta la propiedad de carpeta GITLAB_CREDENTIALS_ID en la carpeta de Jenkins.')
    }
    if (!repo_path) {
        error("❌ No se pudo obtener la ruta del repo desde el job '${job}' (se espera <grupo>/<appcode>/<repo>/<rama>).")
    }
    if (!branch) {
        error('❌ No se indicó la rama a clonar (parámetro BRANCH vacío y sin rama por defecto).')
    }

    def url = "${server}/${repo_path}.git"
    echo "Clonando ${url} rama ${branch}"

    try {
        def scm_vars = checkout([
            $class           : 'GitSCM',
            branches         : [[name: "refs/heads/${branch}"]],
            userRemoteConfigs: [[url: url, credentialsId: credentials]],
            extensions       : [
                [$class: 'CloneOption', noTags: true, shallow: false, timeout: 10],
                [$class: 'CleanBeforeCheckout'],
                [$class: 'LocalBranch', localBranch: branch]     // deja el repo en la rama (no en "detached HEAD")
            ]
        ])

        def commit = scm_vars.GIT_COMMIT ?: ''
        env.GITLAB_STATUS_COMMIT = commit        // habilita update_gitlab_status / gitlab_stage (estado en GitLab)
        echo "✅ Código descargado: ${repo_path} @ ${branch} (commit ${commit.length() > 8 ? commit.substring(0, 8) : commit})"
        return [branch: branch, commit: commit, url: url]
    }
    catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
        throw e
    }
    catch (Exception e) {
        error("❌ No se pudo clonar ${url} rama '${branch}': ${e.message ?: e.toString()}")
    }
}
