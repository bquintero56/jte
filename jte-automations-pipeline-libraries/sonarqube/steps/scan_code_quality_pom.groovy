// Igual que scan_code_quality, pero RESPETA la configuración de SonarQube del pom.xml del micro (el pom manda).
//   Servidor   : Sonar_server_SCAN_SERVER (withSonarQubeEnv inyecta URL y token configurados en Jenkins; el pom no los cambia)
//   Prioridad  : 1) propiedades sonar.* del pom.xml (cualquier módulo)
//                2) sonar-project.properties del micro (resources/sonarqube/<properties_path o component_name>/) o el general:
//                   solo se pasan como -D las que el pom NO declara (rellenan lo que falte, no pisan al pom)
//   Siempre    : sonar.projectKey / sonar.projectName = component_name (el pom no los cambia: los tags de check_quality_gate van a
//                ese proyecto) y sonar.skip=false (si el pom apagara el análisis, el Quality Gate se quedaría esperando).
//   El Quality Gate es el mismo check_quality_gate de la otra variante.
//   Diferencias con scan_code_quality: no fuerza las exclusiones vacías ni marca UNSTABLE si el pom tiene sonar.*; solo lo informa.
//   JDK / Maven: propiedades de carpeta F_PIPELINE_JDK_TOOL_VERSION / F_PIPELINE_MAVEN_TOOL_VERSION
// Necesita las clases compiladas en target/ (va después de los unit tests y antes del build, que hace clean).
void call() {
    def sonar_server = 'Sonar_server_SCAN_SERVER'
    def scanner      = 'org.sonarsource.scanner.maven:sonar-maven-plugin:3.11.0.3922:sonar'   // versión fija compatible con SonarQube 9.9
    def project_key  = ''
    try { project_key = component_name } catch (MissingPropertyException e) { project_key = '' }

    if (!project_key) {
        error('[ERROR]: ❌ Falta component_name en keywords de la config (es el project key de SonarQube).')
    }

    try {
        withFolderProperties {
            def jdk_version = env.F_PIPELINE_JDK_TOOL_VERSION
            def mvn_version = env.F_PIPELINE_MAVEN_TOOL_VERSION
            def mvn_opts    = [artifactsPublisher(disabled: true), jacocoPublisher(disabled: true), junitPublisher(disabled: true)]

            def pom_keys   = pom_sonar_keys()
            def sonar_args = build_sonar_args(project_key, pom_keys)

            withSonarQubeEnv(sonar_server) {
                withMaven(globalMavenSettingsConfig: config.settings_config ?: 'GLOBAL_MAVEN_SETTINGS', jdk: jdk_version, maven: mvn_version, options: mvn_opts, traceability: false) {
                    def status_code = sh(script: """#!/bin/sh
mvn ${scanner} ${sonar_args}""", returnStatus: true)
                    if (status_code != 0) {
                        error("[ERROR]: ❌ Falló el análisis de SonarQube (código ${status_code}).")
                    }
                }
            }
            println('[INFO]: ✅ Análisis publicado en SonarQube (configuración del pom.xml + valores por defecto del pipeline)')
        }
    }
    catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
        throw e
    }
    catch (Exception e) {
        error("${e.message ?: e.toString()}")
    }
}

// Claves sonar.* declaradas en los pom.xml del repo (cualquier módulo), ej. ['sonar.exclusions', 'sonar.coverage.exclusions'].
List pom_sonar_keys() {
    def found = sh(script: '''#!/bin/sh
find . -name pom.xml -not -path "*/target/*" -exec grep -ho "<sonar\\.[A-Za-z0-9._-]*>" {} + 2>/dev/null | sort -u
true''', returnStdout: true).trim()

    def keys = []
    if (found) {
        found.split('\n').each { tag ->
            keys << tag.trim().replace('<', '').replace('>', '')
        }
        println("[INFO]: Propiedades de SonarQube tomadas del pom.xml (mandan sobre las del pipeline): ${keys.join(', ')}")
    }
    else {
        println('[INFO]: El pom.xml no declara propiedades sonar.*: se usan las del pipeline')
    }
    return keys
}

// Arma los -D: propiedades del archivo (del micro o general) que el pom NO declara + project key/name si el pom no los define.
String build_sonar_args(String project_key, List pom_keys) {
    def ignored_keys = ['sonar.projectKey', 'sonar.projectName', 'sonar.host.url', 'sonar.login', 'sonar.token', 'sonar.password', 'sonar.skip']

    def micro_path = String.valueOf(config.properties_path ?: project_key).trim().replaceAll('^/+', '').replaceAll('/+$', '')
    if (!(micro_path ==~ /[A-Za-z0-9._\/-]+/) || micro_path.split('/').contains('..')) {
        error("[ERROR]: ❌ sonarqube.properties_path no es una ruta válida: '${micro_path}' (ej. 'pais/Test/java', sin '..').")
    }

    def micro_file = "sonarqube/${micro_path}/sonar-project.properties"
    def content    = null
    def origin     = ''
    try {
        content = resource(micro_file)
        origin  = "propio del micro (resources/${micro_file})"
    } catch (Exception e) {
        content = resource('sonarqube/sonar-project.properties')
        origin  = "general (no existe resources/${micro_file})"
    }
    println("[INFO]: Valores por defecto del pipeline: sonar-project.properties ${origin}")

    def props   = [:]
    def skipped = []
    def lines   = content.split('\n')
    for (int i = 0; i < lines.length; i++) {
        def line = lines[i].trim()
        if (!line || line.startsWith('#') || !line.contains('=')) {
            continue
        }
        def key   = line.substring(0, line.indexOf('=')).trim()
        def value = line.substring(line.indexOf('=') + 1).trim()
        if (ignored_keys.contains(key)) {
            continue
        }
        if (pom_keys.contains(key)) {
            skipped << key                      // la define el pom: no se pisa
            continue
        }
        props[key] = value
    }
    if (skipped) {
        println("[INFO]: No se pasan (las define el pom.xml): ${skipped.join(', ')}")
    }

    // Project key/name SIEMPRE = component_name (como host y token, el pom no los cambia): así check_quality_gate pone los tags
    // por país en el proyecto correcto sin cambios.
    props['sonar.skip']        = 'false'
    props['sonar.projectKey']  = project_key
    props['sonar.projectName'] = project_key
    if (pom_keys.contains('sonar.projectKey') || pom_keys.contains('sonar.projectName')) {
        println("[WARN]: ⚠️ El pom.xml define sonar.projectKey / sonar.projectName: se ignoran, el proyecto es '${project_key}' (component_name)")
    }
    else {
        println("[INFO]: Project key: '${project_key}' (component_name)")
    }

    def args = []
    def keys = new ArrayList(props.keySet())
    for (int i = 0; i < keys.size(); i++) {
        args.add(shell_quote("-D${keys[i]}=${props[keys[i]]}"))
    }
    return args.join(' ')
}

// Comillas simples para el shell (escapa las comillas simples del valor)
String shell_quote(String text) {
    return "'" + text.replace("'", "'\\''") + "'"
}
