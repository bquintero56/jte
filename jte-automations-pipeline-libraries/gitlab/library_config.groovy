fields {
    required {
        server_url      = String    // URL base de GitLab (ej. 'https://gitlab.empresa.com')
        project_path    = String    // grupo/subgrupo donde está el repo en GitLab (ej. 'mexico/APPCODE')
        repository_name = String    // nombre del repo en GitLab (ej. 'Test-Java')
        credentials_id  = String    // ID de credencial "Username with password": usuario de GitLab + access token con read_repository
    }
}
