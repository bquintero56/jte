// Analiza el código con el scanner de Maven y publica el análisis en SonarQube (Community 9.9: un análisis por proyecto, sin ramas).
//   Servidor   : Sonar_server_SCAN_SERVER (withSonarQubeEnv inyecta URL y token configurados en Jenkins)
//   Project key: component_name (keyword de la config) = nombre del repositorio
//   Propiedades: resources/sonarqube/<component_name>/sonar-project.properties si existe; si no, el general
//                resources/sonarqube/sonar-project.properties. Van como -D: le ganan al pom.xml del micro.
//                Las propiedades que excluyen código se fuerzan siempre (vacías si el archivo no las define).
//                Si el pom.xml declara propiedades sonar.*, se avisa en el log y el build queda UNSTABLE.
//   JDK / Maven: propiedades de carpeta F_PIPELINE_JDK_TOOL_VERSION / F_PIPELINE_MAVEN_TOOL_VERSION
// Necesita las clases compiladas en target/ (va después de los unit tests y antes del build, que hace clean).
void call() {
    def sonar_server = 'Sonar_server_SCAN_SERVER'
    def scanner      = 'org.sonarsource.scanner.maven:sonar-maven-plugin:3.11.0.3922:sonar'   // versión fija compatible con SonarQube 9.9
    def project_key  = ''
    try { project_key = component_name } catch (MissingPropertyException e) { project_key = '' }

    if (!project_key) {
        error('❌ Falta component_name en keywords de la config (es el project key de SonarQube).')
    }

    try {
        withFolderProperties {
            def jdk_version = env.F_PIPELINE_JDK_TOOL_VERSION
            def mvn_version = env.F_PIPELINE_MAVEN_TOOL_VERSION
            def mvn_opts    = [artifactsPublisher(disabled: true), jacocoPublisher(disabled: true), junitPublisher(disabled: true)]

            check_pom_sonar_properties()
            def sonar_args = build_sonar_args(project_key)

            withSonarQubeEnv(sonar_server) {
                withMaven(globalMavenSettingsConfig: config.settings_config ?: 'GLOBAL_MAVEN_SETTINGS', jdk: jdk_version, maven: mvn_version, options: mvn_opts, traceability: false) {
                    def status_code = sh(script: """#!/bin/sh
mvn ${scanner} ${sonar_args}""", returnStatus: true)
                    if (status_code != 0) {
                        error("❌ Falló el análisis de SonarQube del proyecto '${project_key}' (código ${status_code}).")
                    }
                }
            }
            echo "✅ Análisis publicado en SonarQube: proyecto '${project_key}'"
        }
    }
    catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
        throw e
    }
    catch (Exception e) {
        error("${e.message ?: e.toString()}")
    }
}

// Arma los -D del scanner: propiedades del archivo (del micro o general) + propiedades forzadas + project key/name (al final: ganan).
String build_sonar_args(String project_key) {
    def forced_keys = ['sonar.exclusions', 'sonar.inclusions', 'sonar.test.exclusions', 'sonar.test.inclusions',
                       'sonar.coverage.exclusions', 'sonar.cpd.exclusions', 'sonar.issue.ignore.multicriteria',
                       'sonar.issue.ignore.allfile', 'sonar.issue.ignore.block', 'sonar.issue.enforce.multicriteria']
    def ignored_keys = ['sonar.projectKey', 'sonar.projectName', 'sonar.host.url', 'sonar.login', 'sonar.token', 'sonar.password', 'sonar.skip']

    // Archivo del micro si existe; si no, el general
    def content = null
    def origin  = ''
    try {
        content = resource("sonarqube/${project_key}/sonar-project.properties")
        origin  = "propio de ${project_key}"
    } catch (Exception e) {
        content = resource('sonarqube/sonar-project.properties')
        origin  = 'general'
    }
    echo "SonarQube sonar-project.properties: ${origin}"

    def props = [:]
    def lines = content.split('\n')
    for (int i = 0; i < lines.length; i++) {
        def line = lines[i].trim()
        if (!line || line.startsWith('#') || !line.contains('=')) {
            continue
        }
        def key   = line.substring(0, line.indexOf('=')).trim()
        def value = line.substring(line.indexOf('=') + 1).trim()
        if (ignored_keys.contains(key)) {
            echo "⚠️ ${key} en sonar-project.properties se ignora (lo define el pipeline)."
            continue
        }
        props[key] = value
    }
    for (int i = 0; i < forced_keys.size(); i++) {
        if (!props.containsKey(forced_keys[i])) {
            props[forced_keys[i]] = ''          // vacía: anula lo que ponga el pom.xml
        }
    }
    props['sonar.skip']        = 'false'
    props['sonar.projectKey']  = project_key
    props['sonar.projectName'] = project_key

    def args = []
    def keys = new ArrayList(props.keySet())
    for (int i = 0; i < keys.size(); i++) {
        args.add(shell_quote("-D${keys[i]}=${props[keys[i]]}"))
    }
    return args.join(' ')
}

// Avisa (UNSTABLE) si el repo del micro intenta configurar SonarQube por su cuenta. Las propiedades que excluyen código
// se anulan igual desde el pipeline; el aviso deja el intento a la vista. Revisa:
//   - pom.xml (cualquier módulo) con propiedades sonar.*
//   - .mvn/maven.config y .mvn/jvm.config con sonar.* (Maven los lee solo en cada ejecución)
//   - sonar-project.properties en el repo (el scanner de Maven lo ignora: no tiene efecto)
void check_pom_sonar_properties() {
    def found = sh(script: '''#!/bin/sh
find . -name pom.xml -not -path "*/target/*" -exec grep -n "sonar\\." {} + 2>/dev/null
for f in .mvn/maven.config .mvn/jvm.config; do
    [ -f "$f" ] && grep -n "sonar\\." "$f" 2>/dev/null | sed "s#^#$f:#"
done
find . -name sonar-project.properties -not -path "*/target/*" 2>/dev/null | sed "s#\\$# (ignorado por el scanner de Maven)#"
true''', returnStdout: true).trim()

    if (found) {
        echo "⚠️ El repo intenta configurar SonarQube (las propiedades de exclusión se anulan desde el pipeline):\n${found}"
        unstable('⚠️ Configuración de SonarQube en el repo del micro (la controla el pipeline).')
    }
}

// Comillas simples para el shell (escapa las comillas simples del valor)
String shell_quote(String text) {
    return "'" + text.replace("'", "'\\''") + "'"
}
