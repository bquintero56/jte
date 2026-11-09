import jte.maven.lifecycle.Checkstyle

// Valida el estilo del código con Maven (mvn checkstyle:check) dentro de withMaven.
// Solo corre si config.linter_scan = true; si no, marca el build UNSTABLE con un aviso.
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
                withMaven(globalMavenSettingsConfig: config.settings_config, jdk: jdk_version, maven: mvn_version, options: mvn_opts, traceability: false) {
                    checkstyle.scanCode()
                }
                echo '✅ Checkstyle sin errores'
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
