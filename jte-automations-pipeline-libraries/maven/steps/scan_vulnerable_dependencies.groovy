import jte.maven.exceptions.MavenPhaseExecutionException
import jte.maven.lifecycle.DependencyScan

// Busca dependencias vulnerables (directas o transitivas) con mvn dependency:tree dentro de withMaven. El stage lo pone el template.
// Reglas: resources/dependency-scan/vulnerable-dependencies.txt (groupId:artifactId | versiones | CVE | descripción).
//   -Dincludes se arma con esas reglas, así que el árbol solo muestra esas librerías; luego se busca cada versión en la salida.
// Si encuentra alguna: lista cada una con su CVE y DETIENE el pipeline (FAILURE). No toca la caché de Maven (repositorio local
//   compartido del nodo): si el micro sigue declarando la versión, el siguiente build la vuelve a detectar.
// Si mvn dependency:tree falla: [WARN] + UNSTABLE y el pipeline sigue (el escaneo se omite; revisar el pom.xml).
//   JDK / Maven: config.jdk_version / config.mvn_version, o las propiedades de carpeta
//                F_PIPELINE_JDK_TOOL_VERSION / F_PIPELINE_MAVEN_TOOL_VERSION
void call() {
    DependencyScan scan = new DependencyScan(steps)

    def rules           = parse_rules(resource('dependency-scan/vulnerable-dependencies.txt'))
    def vulnerabilities = []

    try {
        withFolderProperties {
            def jdk_version = config.jdk_version ?: env.F_PIPELINE_JDK_TOOL_VERSION
            def mvn_version = config.mvn_version ?: env.F_PIPELINE_MAVEN_TOOL_VERSION
            def mvn_opts    = [artifactsPublisher(disabled: true), jacocoPublisher(disabled: true), junitPublisher(disabled: true)]

            println("[INFO]: Buscando ${rules.size()} librerías vulnerables: ${rules.collect { it.ga }.join(', ')}")

            withMaven(globalMavenSettingsConfig: config.settings_config, jdk: jdk_version, maven: mvn_version, options: mvn_opts, traceability: false) {
                def tree_output = scan.dependencyTree(rules.collect { it.ga })
                vulnerabilities = find_vulnerabilities(rules, tree_output)

            }
        }
    }
    catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
        throw e
    }
    catch (MavenPhaseExecutionException e) {
        // Si dependency:tree falla no se bloquea el pipeline, pero queda en amarillo para que se note que no se escaneó
        unstable("[WARN]: ⚠️ ${e.message} El escaneo de dependencias fue omitido.")
        return
    }
    catch (Exception e) {
        error("${e.message}")
    }

    // Resultado
    if (vulnerabilities) {
        println('[ERROR]: ================================================================')
        println("[ERROR]: 🚨 VULNERABILIDADES ENCONTRADAS (${vulnerabilities.size()})")
        println('[ERROR]: ================================================================')
        vulnerabilities.each { v ->
            println("[ERROR]: ❌ ${v.desc} → ${v.ga}:jar:${v.version} — ${v.cve}")
        }
        println('[ERROR]: El build se detuvo para proteger el artefacto.')
        println('[ERROR]: Actualiza las dependencias vulnerables y vuelve a ejecutar el pipeline.')
        println('[ERROR]: ================================================================')
        error("[ERROR]: ❌ Se encontraron ${vulnerabilities.size()} dependencia(s) vulnerable(s). Revisa el log de este stage.")
    }

    println('[INFO]: ✅ Escaneo de dependencias completado: no se encontraron dependencias vulnerables')
}

// Lee las reglas del archivo de recursos. Devuelve [[ga, versions, cve, desc], ...].
@NonCPS
List parse_rules(String text) {
    def rules = []
    text.readLines().each { line ->
        def clean = line.trim()
        if (!clean || clean.startsWith('#')) {
            return
        }
        def parts = clean.split(/\|/)*.trim()
        if (parts.size() < 4 || !(parts[0] ==~ /[A-Za-z0-9._-]+:[A-Za-z0-9._-]+/)) {
            throw new IllegalArgumentException("[ERROR]: ❌ Regla inválida en vulnerable-dependencies.txt: '${clean}'")
        }
        rules << [ga: parts[0], versions: parts[1].split(',')*.trim().findAll { it }, cve: parts[2], desc: parts[3]]
    }
    return rules
}

// Busca cada groupId:artifactId:jar:<versión> en la salida de dependency:tree. La versión debe terminar en ':' o espacio
// (así 1.1 no coincide con 1.10). Versión con * = comodín. Devuelve [[ga, version, cve, desc], ...].
@NonCPS
List find_vulnerabilities(List rules, String tree_output) {
    def found = []
    rules.each { rule ->
        rule.versions.each { version ->
            def version_regex = version.split(/\*/, -1).collect { java.util.regex.Pattern.quote(it) }.join('[^:\\s]*')
            def matcher       = (tree_output =~ /${java.util.regex.Pattern.quote(rule.ga)}:jar:(${version_regex})(?=[:\s]|$)/)
            def versions_seen = []
            while (matcher.find()) {
                if (!versions_seen.contains(matcher.group(1))) {
                    versions_seen << matcher.group(1)
                    found << [ga: rule.ga, version: matcher.group(1), cve: rule.cve, desc: rule.desc]
                }
            }
        }
    }
    return found
}
