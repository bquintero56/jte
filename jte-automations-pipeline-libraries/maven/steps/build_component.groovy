import jte.maven.lifecycle.Pack

// Empaqueta el componente con Maven (mvn clean package -Dmaven.test.skip=true) dentro de withMaven.
//   settings: config.settings_config (Managed file de Config File Provider)
//   JDK / Maven: config.jdk_version / config.mvn_version, o las propiedades de carpeta
//                F_PIPELINE_JDK_TOOL_VERSION / F_PIPELINE_MAVEN_TOOL_VERSION
void call() {
    Pack pack = new Pack(steps)

    try {
        withFolderProperties {
            def jdk_version = config.jdk_version ?: env.F_PIPELINE_JDK_TOOL_VERSION
            def mvn_version = config.mvn_version ?: env.F_PIPELINE_MAVEN_TOOL_VERSION
            def mvn_opts    = [artifactsPublisher(disabled: true), jacocoPublisher(disabled: true), junitPublisher(disabled: true)]

            withMaven(globalMavenSettingsConfig: config.settings_config, jdk: jdk_version, maven: mvn_version, options: mvn_opts, traceability: true) {
                pack.applicationBinary()
            }
            println("[INFO]: ✅ Componente empaquetado (JDK ${jdk_version}, Maven ${mvn_version})")
        }
    }
    catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
        throw e
    }
    catch (Exception e) {
        error("${e.message}")
    }
}
