// La librería gitlab no recibe campos: todo sale de las propiedades de la carpeta 'mexico' en Jenkins
//   GITLAB_URL, GITLAB_CREDENTIALS_ID → checkout_code (la ruta del repo sale del nombre del job)
//   GITLAB_CONNECTION                 → gitlab_stage / update_gitlab_status (estado por etapa en GitLab)
// En la config va vacío: gitlab { }
