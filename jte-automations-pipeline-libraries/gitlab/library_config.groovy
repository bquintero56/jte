// Todo es opcional: por defecto la URL y la credencial salen de las propiedades de la carpeta de Jenkins
// (GITLAB_URL, GITLAB_CREDENTIALS_ID) y la ruta del repo sale del nombre del job.
fields {
    optional {
        repository_path = String    // solo si el repo en GitLab NO coincide con la ruta del job (ej. 'mexico/APP01/Test-Java')
        credentials_id  = String    // solo si este micro usa otra credencial distinta a GITLAB_CREDENTIALS_ID
    }
}
