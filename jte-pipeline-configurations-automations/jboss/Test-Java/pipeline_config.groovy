// CI del componente Test-Java (JBoss). Repo público: sin IPs, usuarios ni secretos reales.
application_environments {
    dev {
        long_name          = 'Development environment'
        short_name         = 'dev'
        deployment_project = ['pais/jboss/app1/dev-deployment', 'pais/jboss/app2/dev-deployment']   // jobs de CD a disparar
    }
}

jte {
    allow_scm_jenkinsfile = false
    pipeline_template     = 'jboss/dev_integration.groovy'
}

keywords {
    component_name      = 'Test-Java'
    component_packaging = 'war'
    execution {
        deploy_with_referenced_resources = false
        time  = 10
        units = 'MINUTES'
    }
    jenkins_agent_label = 'dev'
    job_environments    = '''
JAVA_OPTS=-Xmx512m
MAVEN_OPTS=-Dmaven.wagon.http.ssl.insecure=true -Dmaven.resolver.transport=wagon
'''
    log_rotator = '10'
}

libraries {
    gitlab {
        server_url      = 'https://gitlab.empresa.com'   // AJUSTAR: URL base de GitLab
        project_path    = 'mexico/APPCODE'               // AJUSTAR: grupo/appcode del repo en GitLab
        repository_name = 'Test-Java'
        credentials_id  = 'gitlab-checkout'              // AJUSTAR: credencial Username with password (usuario + access token read_repository)
    }
    // Se agregan al sumar sus etapas: maven, sonarqube, jboss
}
