// Job ORQUESTADOR: un solo job recibe el webhook de TODOS los proyectos de GitLab y lanza
// <prefijoRuta>/<appcode>/<repo>/<rama | integration>.
jte {
    allow_scm_jenkinsfile = false
    pipeline_template     = 'webhook/push_orchestrator.groovy'
}

// El template usa execution.time, execution.units, jenkins_agent_label y webhook_token_credential
keywords {
    execution {
        time  = 10
        units = 'MINUTES'
    }
    jenkins_agent_label      = 'linux-pro'            // AJUSTAR: label del nodo de Jenkins
    webhook_token_credential = 'gitlab-webhook-token' // AJUSTAR: ID de la credencial "Secret text" con el token del webhook
}

libraries {
    webhook {
        prefijoRuta    = 'mexico'
    }
}
