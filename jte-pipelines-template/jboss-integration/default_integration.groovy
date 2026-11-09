// CI de componentes JBoss — job 'integration': cualquier rama que no sea dev, qa, uat, release o main (feature/*, bugfix/*...). La rama llega en BRANCH.
// Flujo: checkout (GitLab) → compile → unit test → build. Solo valida: no despliega.
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
    ]
])

timeout(time: execution.time, unit: execution.units) {

    node(jenkins_agent_label) {
        withFolderProperties {
            try {
                stage('GitLab: checkout') {
                    checkout_code('')
                }

                // Validaciones: según run_unit_test de la config (si están en false el build queda UNSTABLE)
                compile_code()                       // trae su propio stage: Maven: compile code

                stage('Maven: unit test') {
                    run_unit_test()
                }

                stage('Maven: build binary') {
                    build_component()
                }

                // Pipeline de validación: solo comprueba que el código compila y pasa las pruebas. No despliega.
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
