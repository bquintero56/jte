// Servidor SonarQube fijo en los steps: Sonar_server_SCAN_SERVER (Manage Jenkins → System → SonarQube servers).
// Project key = component_name (keyword de la config) = nombre del repositorio.
fields {
    required {
        enforce_quality_gates = Boolean    // true: si no pasa el Quality Gate el pipeline falla; false: queda UNSTABLE y sigue
    }
    optional {
        settings_config      = String     // Managed file con el settings.xml (default 'GLOBAL_MAVEN_SETTINGS')
        quality_gate_minutes = Integer    // tiempo máximo esperando el Quality Gate (default 5)
    }
}
