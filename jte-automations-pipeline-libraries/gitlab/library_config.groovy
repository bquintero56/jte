// La librería gitlab no recibe campos:
//   GITLAB_URL, GITLAB_CREDENTIALS_ID (propiedades de la carpeta 'mexico') → checkout_code (la ruta del repo sale del nombre del job)
//   gitlab_connection() (nombre de la conexión GitLab de System, fijo en el step) → gitlab_stage / update_gitlab_status
// En la config va vacío: gitlab { }
