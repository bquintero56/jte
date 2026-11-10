// Librería jboss_cd (lado CD, config del SERVIDOR): despliega en JBoss el componente que dejó el CI en la carpeta temporal.
// La usa el job de despliegue (uno por servidor JBoss, ej. pais/jboss-server/app1/dev/jboss-ui-deployment), que lanza el CI con
// los parámetros COMPONENT_NAME y COMPONENT_PACKAGING.
// Carpeta del artefacto: <JBOSS_TEMP_PATH>/<COMPONENT_NAME>, la MISMA que arma el CI (propiedad de carpeta, no va en la config).
// Usuario SSH: propiedad de carpeta JBOSS_SSH_USER (la misma del CI); user en la config solo como excepción.
// Repo público: en la config del repo van valores de ejemplo; los reales solo en la copia de Jenkins.
fields {
    required {
        host_deploy = String    // host del servidor JBoss donde se despliega (uno por config)
    }
    optional {
        user        = String    // solo si este servidor usa otro usuario SSH (por defecto: propiedad de carpeta JBOSS_SSH_USER)
    }
}
