// CI de componentes JBoss — job 'integration': cualquier rama que no sea dev, qa, uat, release o main (feature/*, bugfix/*...). La rama llega en BRANCH.
// Flujo: checkout (GitLab) → checkstyle → compile → unit test → build. Solo valida: no despliega.
// Cada etapa (gitlab_stage) informa su estado en GitLab junto al commit.
// Los 5 templates (default, dev, qa, uat, main) son casi iguales: cada uno agrega o quita etapas según el ambiente.
// Se van agregando las etapas a medida que se prueban.

timeout(time: execution.time, unit: execution.units) {

    node(jenkins_agent_label) {
        withFolderProperties {
            // Propiedades del job (dentro de withFolderProperties para leer GITLAB_CONNECTION de la carpeta)
            def job_properties = [
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
            ]
            if (env.GITLAB_CONNECTION) {
                job_properties.add(gitLabConnection(gitLabConnection: env.GITLAB_CONNECTION))   // estado por etapa en GitLab
            }
            properties(job_properties)

            try {
                gitlab_stage('GitLab: checkout') {
                    checkout_code('')
                }

                gitlab_stage('Maven: checkstyle') {
                    check_code_styles()
                }

                gitlab_stage('Maven: compile code') {
                    compile_code()
                }

                gitlab_stage('Maven: unit test') {
                    run_unit_test()
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
