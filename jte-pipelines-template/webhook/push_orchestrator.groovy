// Orquestador de pushes de GitLab (GitLab Plugin: "Build when a change is pushed to GitLab").
// URL del webhook en GitLab: la que muestra el trigger en el job → https://<jenkins>/project/<ruta-del-job>
// En GitLab los repos están en <grupo>/<appcode>/<repo> (ej. mexico/appcode/mi-repo). Del evento se toman:
//   branch_name     ← gitlabSourceBranch                                        'dev'
//   repository_name ← último segmento de gitlabSourceRepoHttpUrl (sin .git)     'mi-repo'
//   appcode         ← penúltimo segmento de gitlabSourceRepoHttpUrl             'appcode'
def branch_name     = ''
def repository_name = ''
def appcode         = ''

timeout(time: execution.time, unit: execution.units) {

    node(jenkins_agent_label) {
        withFolderProperties {
            try {
                stage('Webhook: registrar trigger GitLab') {
                    // Credencial "Username with password": el password es el Secret token del webhook; el usuario no se usa
                    withCredentials([usernamePassword(credentialsId   : webhook_token_credential,
                                                      usernameVariable: 'GITLAB_WEBHOOK_USER',
                                                      passwordVariable: 'GITLAB_WEBHOOK_TOKEN')]) {
                        properties([
                            pipelineTriggers([
                                gitlab(
                                    triggerOnPush                 : true,
                                    triggerOnMergeRequest         : false,
                                    triggerOpenMergeRequestOnPush : 'never',
                                    triggerOnNoteRequest          : false,
                                    ciSkip                        : true,
                                    setBuildDescription           : false,
                                    branchFilterType              : 'All',
                                    secretToken                   : env.GITLAB_WEBHOOK_TOKEN
                                )
                            ])
                        ])
                    }
                }

                // Datos del push (vacíos si el job se ejecutó a mano)
                def partes = (env.gitlabSourceRepoHttpUrl ?: '').replaceFirst(/\.git$/, '').tokenize('/')
                branch_name     = env.gitlabSourceBranch ?: env.gitlabBranch ?: ''
                repository_name = partes.size() >= 4 ? partes[partes.size() - 1] : ''
                appcode         = partes.size() >= 4 ? partes[partes.size() - 2] : ''

                if (!branch_name || !repository_name || !appcode) {
                    echo '⚠️ Sin datos del webhook (ejecución manual o primera ejecución para registrar el trigger): no se lanza ningún job.'
                } else if ((env.gitlabAfter ?: '') ==~ /0+/) {
                    echo "⚠️ Se borró la rama '${branch_name}' en ${appcode}/${repository_name}: no se lanza ningún job."
                } else {
                    currentBuild.description = "${appcode}/${repository_name} @ ${branch_name}"
                    disparar_pipeline_aplicacion(branch_name, repository_name, appcode)
                }
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
