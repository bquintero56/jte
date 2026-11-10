// CD (despliegue) del servidor/aplicación app1 en dev — job 'pais/jboss/app1/dev-deployment'.
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
    jboss_cd {
        host_deploy = ['jboss-1.ejemplo.local', 'jboss-2.ejemplo.local']   // AJUSTAR: servidores JBoss donde se despliega
        path        = '/opt/jboss'                                        // AJUSTAR: ruta de JBoss en el servidor
    }
}
