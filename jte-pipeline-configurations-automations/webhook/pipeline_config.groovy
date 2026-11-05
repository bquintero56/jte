// Job ORQUESTADOR: un solo job recibe el webhook de TODOS los proyectos de GitLab,
// arma la ruta del job en Jenkins con el grupo, repo y rama del evento, y lo ejecuta.
jte {
    allow_scm_jenkinsfile = false
    pipeline_template     = 'webhook/webhook.groovy'
}

// El template usa execution.time, execution.units y jenkins_agent_label
keywords {
    execution {
        time  = 10
        units = 'MINUTES'
    }
    jenkins_agent_label = 'linux-pro'                 // AJUSTAR: label del nodo de Jenkins
}

libraries {
    webhook {
        // ---- Disparador (GitLab Plugin) ----
        dispararEnPush         = true
        dispararEnMergeRequest = false
        ramas                  = ''                   // ramas que disparan, separadas por coma; '' = todas
        credencialToken        = ''                   // ID de credencial "Secret text" con el Secret token del webhook de GitLab ('' = sin token)

        // ---- Ruta del job a ejecutar ----
        // Variables: {grupo} = grupo/subgrupo de GitLab, {repo} = nombre del repo, {rama} = rama del evento.
        //   Multibranch (un job por rama):      '{grupo}/{repo}/{rama}'
        //   Job normal por repo (sin rama):     '{grupo}/{repo}'
        plantillaRutaJob = '{grupo}/{repo}/{rama}'    // AJUSTAR a cómo están los jobs en Jenkins
        codificarRama    = true                       // multibranch: 'feature/x' → 'feature%2Fx' (así nombra Jenkins el job de la rama)

        // Excepciones: 'grupo/repo' de GitLab → ruta del job en Jenkins, cuando no siga la plantilla. {rama} también vale aquí.
        mapeoRutas = [:]                              // ej. ['finanzas/api-pagos': 'Finanzas/pagos/{rama}']

        // ---- Ejecución ----
        parametroRama     = ''                        // nombre del parámetro String del job donde pasar la rama (ej. 'BRANCH'); '' = no pasa parámetros
        esperarJob       = false                     // true = el orquestador espera a que termine el job
        propagarError     = true                      // con esperarJob = true: si el job falla, el orquestador también
        ignorarSiNoExiste = false                     // true = si el job no existe en Jenkins solo avisa (no falla)
    }
}
