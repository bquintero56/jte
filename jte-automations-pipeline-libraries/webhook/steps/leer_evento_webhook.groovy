// Lee las variables que deja el GitLab Plugin cuando un webhook dispara el job y las devuelve en un mapa.
// El grupo y el repo salen de la URL del proyecto (https://gitlab.empresa.com/<grupo>/<subgrupo>/<repo>.git),
// que es el path real de GitLab (el nombre "bonito" del proyecto puede tener espacios o mayúsculas).
// Si el job se ejecutó a mano (sin webhook), desdeWebhook = false.
Map call() {
    def url  = env.gitlabSourceRepoHttpUrl ?: env.gitlabSourceRepoURL ?: ''
    def path = url.replaceFirst(/^[a-zA-Z]+:\/\/[^\/]+\//, '').replaceFirst(/\.git$/, '')   // grupo/subgrupo/repo
    def corte = path.lastIndexOf('/')

    def evento = [
        desdeWebhook : env.gitlabActionType ? true : false,
        tipo         : env.gitlabActionType ?: '',                          // PUSH, TAG_PUSH, MERGE, NOTE
        proyecto     : path,                                                // grupo/subgrupo/repo
        grupo        : corte > 0 ? path.substring(0, corte) : '',
        repo         : corte > 0 ? path.substring(corte + 1) : path,
        rama         : env.gitlabSourceBranch ?: env.gitlabBranch ?: '',
        ramaDestino  : env.gitlabTargetBranch ?: '',
        commit       : env.gitlabAfter ?: '',
        usuario      : env.gitlabUserName ?: '',
        correo       : env.gitlabUserEmail ?: '',
        repositorio  : url
    ]
    // Push que borra la rama: GitLab manda el commit "after" en ceros
    evento.ramaBorrada = (evento.commit ==~ /0+/)

    if (!evento.desdeWebhook) {
        echo '⚠️ El job no fue disparado por un webhook de GitLab (ejecución manual o primera ejecución para registrar el disparador).'
        return evento
    }

    echo """✅ Evento recibido desde GitLab
   Tipo        : ${evento.tipo}
   Repositorio : ${evento.repositorio}
   Grupo       : ${evento.grupo}
   Repo        : ${evento.repo}
   Rama        : ${evento.rama}${evento.ramaDestino && evento.ramaDestino != evento.rama ? ' → ' + evento.ramaDestino : ''}
   Commit      : ${evento.commit}${evento.ramaBorrada ? ' (rama borrada)' : ''}
   Usuario     : ${evento.usuario} ${evento.correo ? '<' + evento.correo + '>' : ''}"""

    if (!evento.repo || !evento.rama) {
        error "❌ El webhook no trae el repo o la rama (url: '${url}', rama: '${evento.rama}'). Revisar el webhook en GitLab."
    }

    return evento
}
