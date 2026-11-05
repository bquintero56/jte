fields {
    required {
        dispararEnPush         = Boolean
        dispararEnMergeRequest = Boolean
        ramas                  = String    // ramas separadas por coma; '' = todas
        plantillaRutaJob       = String    // con {grupo}, {repo}, {rama}
        codificarRama          = Boolean   // '/' → '%2F' en la rama (jobs multibranch)
        esperarJob             = Boolean
        propagarError          = Boolean
    }
    optional {
        credencialToken        = String    // ID de credencial Secret text con el Secret token del webhook; '' o ausente = sin token
        mapeoRutas             = Map       // 'grupo/repo' de GitLab → ruta del job en Jenkins
        ignorarSiNoExiste      = Boolean   // true = job inexistente solo avisa
        parametroRama          = String    // parámetro String del job donde pasar la rama; '' = sin parámetros
    }
}
