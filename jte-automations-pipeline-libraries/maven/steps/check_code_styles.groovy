import jte.maven.lifecycle.Checkstyle
import jte.maven.exceptions.MavenPhaseExecutionException

// Valida el estilo del código con Maven (mvn checkstyle:check) dentro de withMaven.
// Solo corre si config.linter_scan = true; si no, marca el build UNSTABLE con un aviso.
// Si checkstyle encuentra errores de estilo NO detiene el pipeline: marca el stage y el build en UNSTABLE (amarillo) y sigue.
// Suppressions (mixto): resources/checkstyle/<component_name>/checkstyle-suppressions.xml si el micro tiene uno propio;
// si no, el común resources/checkstyle/checkstyle-suppressions.xml. Se escribe en el workspace antes de correr checkstyle.
//   JDK / Maven: config.jdk_version / config.mvn_version, o las propiedades de carpeta
//                F_PIPELINE_JDK_TOOL_VERSION / F_PIPELINE_MAVEN_TOOL_VERSION
void call() {
    Checkstyle checkstyle = new Checkstyle(steps)

    try {
        withFolderProperties {
            def jdk_version = config.jdk_version ?: env.F_PIPELINE_JDK_TOOL_VERSION
            def mvn_version = config.mvn_version ?: env.F_PIPELINE_MAVEN_TOOL_VERSION
            def mvn_opts    = [artifactsPublisher(disabled: true), jacocoPublisher(disabled: true), junitPublisher(disabled: true)]

            if (config.linter_scan == true) {
                def suppressions_file = write_suppressions()

                try {
                    withMaven(globalMavenSettingsConfig: config.settings_config, jdk: jdk_version, maven: mvn_version, options: mvn_opts, traceability: false) {
                        checkstyle.scanCode(suppressions_file)
                    }
                    echo '✅ Checkstyle sin errores'
                }
                catch (MavenPhaseExecutionException e) {
                    unstable('⚠️ Checkstyle encontró errores de estilo (ver el log de este stage). El pipeline continúa.')
                }
            }
            else {
                unstable('[WARN]: skip linter execution caused by project configuration.')
            }
        }
    }
    catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
        throw e
    }
    catch (Exception e) {
        error("${e.message}")
    }
}

// Escribe en el workspace el suppressions del micro (si existe) o el común. Devuelve la ruta del archivo.
String write_suppressions() {
    def file    = 'checkstyle-suppressions.xml'
    def micro   = ''
    def content = null
    def origin  = ''

    try { micro = component_name } catch (MissingPropertyException e) { micro = '' }

    if (micro) {
        try {
            content = resource("checkstyle/${micro}/checkstyle-suppressions.xml")
            origin  = "propio de ${micro}"
        } catch (Exception e) {
            content = null
        }
    }
    if (content == null) {
        content = resource('checkstyle/checkstyle-suppressions.xml')
        origin  = 'común'
    }

    writeFile(file: file, text: content)
    echo "Checkstyle suppressions: ${origin} → ${file}"
    return file
}
