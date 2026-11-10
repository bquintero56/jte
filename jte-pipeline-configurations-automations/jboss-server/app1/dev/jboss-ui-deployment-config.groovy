// CD (despliegue) del servidor jboss-ui de la aplicación app1 en dev — job 'pais/jboss-server/app1/dev/jboss-ui-deployment'.
// Nomenclatura: jboss-server/<app>/<ambiente>/<servidor>-deployment-config.groovy (una config por servidor JBoss).
// Config del SERVIDOR (no del micro): el micro llega por parámetros COMPONENT_NAME / COMPONENT_PACKAGING desde el CI.
// Repo público: valores de ejemplo; los reales solo en la copia de Jenkins.
jte {
    allow_scm_jenkinsfile = false
    pipeline_template     = 'jboss-deployment/jboss_deployment.groovy'
}

keywords {
    execution {
        time  = 30
        units = 'MINUTES'
    }
    jenkins_agent_label = 'dev'                                   // AJUSTAR: label del nodo
    log_rotator         = '10'
}

libraries {
    jboss_cd_standalone {                                             // JBoss standalone (si es domain: jboss_cd_domain + server_groups)
        host_deploy = 'jboss-ui-dev.ejemplo.local'                  // AJUSTAR: host del servidor JBoss donde se despliega (uno)
        // dry_run = true                                             // opcional: SIMULACIÓN (no despliega, solo muestra el comando)
    }
}
