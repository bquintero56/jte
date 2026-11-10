import jte.maven.lifecycle.Compile

// Compila el código con Maven (mvn compile) dentro de withMaven. El stage lo pone el template.
//   JDK / Maven: config.jdk_version / config.mvn_version, o las propiedades de carpeta
//                F_PIPELINE_JDK_TOOL_VERSION / F_PIPELINE_MAVEN_TOOL_VERSION
void call() {
    Compile compile = new Compile(steps)

    try {
        withFolderProperties {
            def jdk_version = config.jdk_version ?: env.F_PIPELINE_JDK_TOOL_VERSION
            def mvn_version = config.mvn_version ?: env.F_PIPELINE_MAVEN_TOOL_VERSION
            def mvn_opts    = [artifactsPublisher(disabled: true), jacocoPublisher(disabled: true), junitPublisher(disabled: true)]

            withMaven(globalMavenSettingsConfig: config.settings_config, jdk: jdk_version, maven: mvn_version, options: mvn_opts, traceability: false) {
                compile.applicationCode()
            }
            println('[INFO]: ✅ Código compilado')
        }
    }
    catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
        throw e
    }
    catch (Exception e) {
        error(e.message)
    }
}
