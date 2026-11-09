// CI del componente Test-Java (JBoss) — job 'integration' (cualquier rama que no sea dev, qa, uat, release o main).
// La rama llega en el parámetro BRANCH. Repo público: sin IPs, usuarios ni secretos reales.
// Sin application_environments: por ahora no despliega en ningún ambiente (POR DEFINIR).

jte {
    allow_scm_jenkinsfile = false
    pipeline_template     = 'jboss-integration/default_integration.groovy'
}

keywords {
    component_name      = 'Test-Java'
    component_packaging = 'war'
    execution {
        deploy_with_referenced_resources = false
        time  = 10
        units = 'MINUTES'
    }
    jenkins_agent_label = 'dev'                                   // AJUSTAR: label del nodo
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
    sonarqube {
        enforce_quality_gates = true          // false: si no pasa el Quality Gate queda UNSTABLE y sigue
    }
    // Se agrega al sumar sus etapas: jboss
}
