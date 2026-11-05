// Registra en el job el disparador de GitLab (GitLab Plugin) según la config.
// Ojo: Jenkins guarda el disparador recién después de la PRIMERA ejecución (manual) del job.
void call() {
    def ramas = String.valueOf(config.ramas ?: '').trim()

    def disparador = [
        triggerOnPush               : config.dispararEnPush,
        triggerOnMergeRequest       : config.dispararEnMergeRequest,
        triggerOpenMergeRequestOnPush: 'never',
        triggerOnNoteRequest        : false,
        skipWorkInProgressMergeRequest: true,
        ciSkip                      : true,             // un commit con [ci-skip] no dispara el job
        setBuildDescription         : true,
        branchFilterType            : ramas ? 'NameBasedFilter' : 'All',
        includeBranchesSpec         : ramas,
        excludeBranchesSpec         : ''
    ]

    def credencial = String.valueOf(config.credencialToken ?: '').trim()
    if (credencial) {
        withCredentials([string(credentialsId: credencial, variable: 'TOKEN_WEBHOOK')]) {
            disparador.secretToken = env.TOKEN_WEBHOOK
            properties([pipelineTriggers([gitlab(disparador)])])
        }
    } else {
        properties([pipelineTriggers([gitlab(disparador)])])
    }

    def eventos = []
    if (config.dispararEnPush)         { eventos.add('push') }
    if (config.dispararEnMergeRequest) { eventos.add('merge request') }

    echo "✅ Webhook de GitLab registrado | eventos: ${eventos ? eventos.join(', ') : 'ninguno'} | ramas: ${ramas ?: 'todas'} | token: ${credencial ? 'sí' : 'no'}"
}
