// Librería jboss: carpeta temporal en los servidores JBoss, copia del artefacto y disparo del job de CD.
// Conexión: ssh <user>@<host> con la llave SSH que ya tiene el nodo de Jenkins (sin credentialsId).
// Nombre y empaquetado del componente: keywords component_name / component_packaging de la config.
// Repo público: en la config del repo van valores de ejemplo; los reales solo en la copia de Jenkins.
fields {
    required {
        user = String      // usuario SSH en los servidores JBoss
        host = List        // servidores JBoss del ambiente (también acepta un String con un solo host)
        path = String      // carpeta base donde se crea la temporal: <path>/<component_name>
    }
    optional {
        artifact_workspace_location = String    // carpeta del artefacto en el workspace (default 'target')
    }
}
