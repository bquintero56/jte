// CI de componentes JBoss — job 'integration': cualquier rama que no sea dev, qa, uat, release o main (feature/*, bugfix/*...). La rama llega en BRANCH.
// Flujo: checkout (GitLab) → checkstyle → dependency scan (librerías vulnerables) → compile → unit test → SonarQube (scan + quality gate) → build. Solo valida: no despliega.
// Cada etapa (gitlab_stage) informa su estado en GitLab junto al commit (conexión GitLab en properties).
// Los 5 templates (default, dev, qa, uat, main) son casi iguales: cada uno agrega o quita etapas según el ambiente.
// Se van agregando las etapas a medida que se prueban.
properties([
    buildDiscarder(logRotator(numToKeepStr: log_rotator)),
    disableConcurrentBuilds(),
    durabilityHint('PERFORMANCE_OPTIMIZED'),
    [
        $class                    : 'EnvInjectJobProperty',
        info                      : [
            loadFilesFromMaster: false,
            propertiesContent  : job_environments
        ],
        keepBuildVariables        : true,
        keepJenkinsSystemVariables: true,
        on                        : true
    ],
    gitLabConnection(gitLabConnection: 'api-gitlab_connection_java_repositorie')   // conexión GitLab (System) para el estado por etapa
])

timeout(time: execution.time, unit: execution.units) {

    node(jenkins_agent_label) {
        withFolderProperties {
            try {
                gitlab_stage('GitLab: checkout') {
                    checkout_code('')
                }

                gitlab_stage('Maven: checkstyle') {
                    check_code_styles()
                }

                gitlab_stage('Maven: dependency scan') {
                    scan_vulnerable_dependencies()
                }

                gitlab_stage('Maven: compile code') {
                    compile_code()
                }

                gitlab_stage('Maven: unit test') {
                    run_unit_test()
                }

                gitlab_stage('SonarQube: scan') {
                    scan_code_quality()
                }

                gitlab_stage('SonarQube: quality gate') {
                    check_quality_gate()
                }

                gitlab_stage('Maven: build binary') {
                    build_component()
                }

                // Pipeline de validación: solo comprueba el estilo, que el código compila y que pasa las pruebas. No despliega.
            }
            catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
                throw e     // conserva el estado ABORTED
            }
            catch (Exception e) {
                error("${e.message ?: e.toString()}")
            }
            finally {
                clean_job_workspace()
            }
        }
    }
}
