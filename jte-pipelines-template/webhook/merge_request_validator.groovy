// Validador de Merge Requests de GitLab (job aparte del orquestador de pushes).
// Se dispara con cada MR abierto o actualizado de cualquier repo del grupo y valida la promoción de ramas:
//   rama de trabajo → dev → qa → uat → main (sin excepciones). Informa el estado "Validación de promoción" al MR;
//   con "Pipelines must succeed" activo en el repo, un ❌ bloquea el botón Merge. No lanza ningún otro job.
// URL del webhook en GitLab (solo "Merge request events"): la que muestra el trigger de este job → https://<jenkins>/project/<ruta-del-job>
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
                            gitLabConnection(gitLabConnection: 'api-gitlab_connection_java_repositorie'),
                            pipelineTriggers([
                                gitlab(
                                    triggerOnPush                 : false,     // los pushes los maneja push_orchestrator
                                    triggerOnMergeRequest         : true,      // MR abierto o actualizado
                                    triggerOnAcceptedMergeRequest : false,
                                    triggerOnClosedMergeRequest   : false,
                                    triggerOpenMergeRequestOnPush : 'never',
                                    triggerOnNoteRequest          : false,
                                    ciSkip                        : false,
                                    setBuildDescription           : false,
                                    branchFilterType              : 'All',
                                    secretToken                   : env.GITLAB_WEBHOOK_TOKEN
                                )
                            ])
                        ])
                    }
                }

                if (env.gitlabActionType != 'MERGE') {
                    println('[WARN]: ⚠️ Sin datos de Merge Request (ejecución manual o primera ejecución para registrar el trigger): no se valida nada.')
                } else {
                    def partes = (env.gitlabTargetRepoHttpUrl ?: env.gitlabSourceRepoHttpUrl ?: '').replaceFirst(/\.git$/, '').tokenize('/')
                    def repo   = partes.size() >= 2 ? "${partes[partes.size() - 2]}/${partes[partes.size() - 1]}" : '?'
                    currentBuild.description = "MR ${repo}: ${env.gitlabSourceBranch} → ${env.gitlabTargetBranch}"
                    validate_merge_request(env.gitlabSourceBranch, env.gitlabTargetBranch)
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
