// Quality Gate del análisis de scan_code_quality_pom (la variante que respeta la configuración de SonarQube del pom.xml).
// Mismo comportamiento que check_quality_gate (espera el webhook, pone los tags por país y aplica enforce_quality_gates), pero el
// project key de los tags es el REAL del análisis: si el pom.xml define sonar.projectKey puede no ser component_name.
// El key sale de target/sonar/report-task.txt (lo escribe el scanner de Maven al publicar el análisis, línea projectKey=...).
// Si no se encuentra el archivo se usa component_name.
void call() {
    def project_key = ''

    if (fileExists('target/sonar/report-task.txt')) {
        def report = readFile('target/sonar/report-task.txt')
        def lines  = report.split('\n')
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().startsWith('projectKey=')) {
                project_key = lines[i].trim().substring('projectKey='.length()).trim()
                break
            }
        }
    }

    if (project_key) {
        println("[INFO]: Project key del análisis (target/sonar/report-task.txt): '${project_key}'")
    }
    else {
        println('[WARN]: ⚠️ No se encontró target/sonar/report-task.txt: los tags se ponen con component_name')
    }

    check_quality_gate(project_key)
}
