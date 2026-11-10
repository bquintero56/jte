// Validador de Merge Requests: un solo job recibe los eventos de MR de TODOS los repos de GitLab y valida la promoción de ramas.
jte {
    allow_scm_jenkinsfile = false
    pipeline_template     = 'webhook/merge_request_validator.groovy'
}

// El template usa execution.time, execution.units, jenkins_agent_label y webhook_token_credential
keywords {
    execution {
        time  = 5
        units = 'MINUTES'
    }
    jenkins_agent_label      = 'linux-pro'            // AJUSTAR: label del nodo de Jenkins
    webhook_token_credential = 'gitlab-webhook-token' // ID de la credencial "Username with password"; el password = Secret token del webhook
}

libraries {
    // Misma librería que el orquestador de pushes: aquí se usa su step validar_merge_request.
    // prefijoRuta es obligatorio en la librería (lo usa el orquestador); el validador no lo usa.
    webhook {
        prefijoRuta = 'mexico'
    }
}
