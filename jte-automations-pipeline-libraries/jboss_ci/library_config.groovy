// Librería jboss_ci (lado CI, config del MICRO): carpeta temporal en los servidores JBoss, copia del artefacto y disparo del job de CD.
// El despliegue lo hace el job de CD con la librería jboss_cd (config del SERVIDOR: path, host_deploy, user...).
// Conexión: ssh <usuario>@<host> con la llave SSH que ya tiene el nodo de Jenkins (sin credentialsId).
// Usuario: propiedad de carpeta JBOSS_SSH_USER (global en Jenkins, no va en el repo); user en la config solo para excepciones.
// component_name / component_packaging: si no van en este bloque se toman de los keywords de la config.
// Repo público: en la config del repo van valores de ejemplo; los reales solo en la copia de Jenkins.
fields {
    required {
        path = String                           // carpeta base donde se crea la temporal: <path>/<component_name>
        host = List                             // servidores JBoss del ambiente, siempre como lista: ['host1'] o ['host1', 'host2']
    }
    optional {
        user                        = String    // solo si este micro usa otro usuario SSH (por defecto: propiedad de carpeta JBOSS_SSH_USER)
        component_packaging         = String    // war, ear, jar...
        component_name              = String    // nombre del componente (carpeta temporal y artefacto)
        artifact_workspace_location = String    // carpeta del artefacto en el workspace (default 'target')
    }
}
