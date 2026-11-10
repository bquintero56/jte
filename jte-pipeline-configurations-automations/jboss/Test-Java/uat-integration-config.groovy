// CI del componente Test-Java (JBoss) — job 'uat' (rama uat). Repo público: sin IPs, usuarios ni secretos reales.
application_environments {
    uat {
        long_name          = 'UAT environment'
        short_name         = 'uat'
        deployment_project = ['pais/jboss/app1/uat-deployment', 'pais/jboss/app2/uat-deployment']   // AJUSTAR: jobs de CD a disparar
    }
}

jte {
    allow_scm_jenkinsfile = false
    pipeline_template     = 'jboss-integration/uat_integration.groovy'
}

keywords {
    component_name      = 'Test-Java'
    component_packaging = 'war'
    execution {
        deploy_with_referenced_resources = false
        time  = 10
        units = 'MINUTES'
    }
    jenkins_agent_label = 'dev'                                   // AJUSTAR: label del nodo para uat
    job_environments    = '''
JAVA_OPTS=-Xmx512m
MAVEN_OPTS=-Dmaven.wagon.http.ssl.insecure=true -Dmaven.resolver.transport=wagon
'''
    log_rotator = '10'
}

libraries {
    // URL y credencial: propiedades de carpeta GITLAB_URL / GITLAB_CREDENTIALS_ID. Repo: ruta del job sin la rama.
    gitlab { }
    maven {
        run_unit_test   = true
        linter_scan     = true
        settings_config = 'GLOBAL_MAVEN_SETTINGS'     // ID del Managed file (Config File Provider) con el settings.xml
        // jdk_version / mvn_version: opcionales; si no van, salen de F_PIPELINE_JDK_TOOL_VERSION / F_PIPELINE_MAVEN_TOOL_VERSION
    }
    jboss_ci {
        host = ['jboss-1.ejemplo.local', 'jboss-2.ejemplo.local']   // AJUSTAR: servidores JBoss del ambiente (repo público: no poner IPs reales)
        component_packaging         = 'war'
        component_name              = 'Test-Java'
        artifact_workspace_location = 'target'                // carpeta del artefacto en el workspace
    }
}
