// Librería jboss_cd (lado CD, config del SERVIDOR): despliega en JBoss el componente que dejó el CI en la carpeta temporal.
// La usa el job de despliegue (uno por servidor/aplicación, ej. pais/jboss/app1/dev-deployment), que lanza el CI con
// los parámetros COMPONENT_NAME y COMPONENT_PACKAGING.
// Usuario SSH y carpeta base: propiedades de carpeta JBOSS_SSH_USER y JBOSS_TEMP_PATH (las mismas que usa el CI).
// Repo público: en la config del repo van valores de ejemplo; los reales solo en la copia de Jenkins.
fields {
    optional {
        host_deploy = List      // servidores JBoss donde se despliega
        path        = String    // ruta de JBoss en el servidor (ej. deployments / jboss-cli)
        user        = String    // solo si este servidor usa otro usuario SSH (por defecto: propiedad de carpeta JBOSS_SSH_USER)
    }
}
