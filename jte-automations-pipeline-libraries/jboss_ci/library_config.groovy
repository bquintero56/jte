// Librería jboss_ci (lado CI, config del MICRO): carpeta temporal en los servidores JBoss, copia del artefacto y disparo del job de CD.
// El despliegue lo hace el job de CD con la librería jboss_cd (config del SERVIDOR: path, host_deploy, user...).
// Conexión: ssh <user>@<host> con la llave SSH que ya tiene el nodo de Jenkins (sin credentialsId).
// component_name / component_packaging: si no van en este bloque se toman de los keywords de la config.
// Repo público: en la config del repo van valores de ejemplo; los reales solo en la copia de Jenkins.
fields {
    required {
        user = String                           // usuario SSH en los servidores JBoss
        path = String                           // carpeta base donde se crea la temporal: <path>/<component_name>
    }
    optional {
        host                        = List      // servidores JBoss del ambiente (también acepta un String con un solo host)
        component_packaging         = String    // war, ear, jar...
        component_name              = String    // nombre del componente (carpeta temporal y artefacto)
        artifact_workspace_location = String    // carpeta del artefacto en el workspace (default 'target')
    }
}
