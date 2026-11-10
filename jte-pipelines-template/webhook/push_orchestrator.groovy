// Orquestador de GitLab (GitLab Plugin: "Build when a change is pushed to GitLab").
//   Push a una rama        → lanza el job de la aplicación de esa rama (disparar_pipeline_aplicacion)
//   MR abierto/actualizado → valida la promoción origen → destino e informa ✅/❌ al MR (validar_merge_request), no lanza jobs
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
                            gitLabConnection(gitLabConnection: 'api-gitlab_connection_java_repositorie'),   // para informar el estado del MR
                            pipelineTriggers([
                                gitlab(
                                    triggerOnPush                 : true,
                                    triggerOnMergeRequest         : true,      // MR abierto o actualizado → validar_merge_request
                                    triggerOnAcceptedMergeRequest : false,     // el merge genera un push: ese lanza el job
                                    triggerOnClosedMergeRequest   : false,
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

                if (env.gitlabActionType == 'MERGE') {
                    // Merge Request: solo validar la promoción (no se lanza ningún job)
                    currentBuild.description = "MR ${appcode}/${repository_name}: ${env.gitlabSourceBranch} → ${env.gitlabTargetBranch}"
                    validar_merge_request(env.gitlabSourceBranch, env.gitlabTargetBranch)
                } else if (!branch_name || !repository_name || !appcode) {
                    println('[WARN]: ⚠️ Sin datos del webhook (ejecución manual o primera ejecución para registrar el trigger): no se lanza ningún job.')
                } else if ((env.gitlabAfter ?: '') ==~ /0+/) {
                    println("[WARN]: ⚠️ Se borró la rama '${branch_name}' en ${appcode}/${repository_name}: no se lanza ningún job.")
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
