fields {
    required {
        country = String                  // primera carpeta de la ruta del job (ej. 'mexico'); la usa disparar_pipeline_aplicacion
    }
    optional {
        api_token_credential = String     // validate_merge_request: credencial "Secret text" con token de GitLab (permiso api) para
                                          // comentar y cerrar MR inválidos (default 'gitlab-api-close-merge-request')
    }
}
