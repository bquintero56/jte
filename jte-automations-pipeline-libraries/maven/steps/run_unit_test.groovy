import jte.maven.lifecycle.Test

// Ejecuta las pruebas unitarias con Maven (mvn clean test) dentro de withMaven.
// Corre si run_test = true; si no se pasa run_test, decide config.run_unit_test. Si no corre, marca el build UNSTABLE.
//   JDK / Maven: config.jdk_version / config.mvn_version, o las propiedades de carpeta
//                F_PIPELINE_JDK_TOOL_VERSION / F_PIPELINE_MAVEN_TOOL_VERSION
void call(Boolean run_test = null) {
    Test test = new Test(steps)
    def ejecutar = (run_test != null) ? run_test : config.run_unit_test

    try {
        withFolderProperties {
            def jdk_version = config.jdk_version ?: env.F_PIPELINE_JDK_TOOL_VERSION
            def mvn_version = config.mvn_version ?: env.F_PIPELINE_MAVEN_TOOL_VERSION
            def mvn_opts    = [artifactsPublisher(disabled: true), jacocoPublisher(disabled: true), junitPublisher(disabled: true)]

            if (ejecutar == true) {
                withMaven(globalMavenSettingsConfig: config.settings_config, jdk: jdk_version, maven: mvn_version, options: mvn_opts, traceability: false) {
                    test.codeUnitTest()
                }
                println('[INFO]: ✅ Pruebas unitarias OK')
            }
            else {
                unstable('[WARN]: Unit tests disabled on pipeline configuration.')
            }
        }
    }
    catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
        throw e
    }
    catch (Exception e) {
        error(e.message)
    }
}
