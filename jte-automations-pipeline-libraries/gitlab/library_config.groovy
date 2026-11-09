// La librería gitlab no recibe campos:
//   GITLAB_URL, GITLAB_CREDENTIALS_ID (propiedades de la carpeta 'mexico') → checkout_code (la ruta del repo sale del nombre del job)
//   gitlab_stage / update_gitlab_status usan la conexión GitLab que el template declara en properties([... gitLabConnection(...)])
// En la config va vacío: gitlab { }
