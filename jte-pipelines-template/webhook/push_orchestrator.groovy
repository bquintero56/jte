// Orquestador de webhooks de GitLab (Generic Webhook Trigger).
// En GitLab los repos están en <grupo>/<appcode>/<repo> (ej. mexico/appcode/mi-repo).
// Del JSON del push de GitLab se toman:
//   branch_name     ← $.ref                         'refs/heads/dev'          → 'dev'
//   repository_name ← $.project.path_with_namespace 'mexico/appcode/mi-repo'  → 'mi-repo'  (último segmento)
//   appcode         ← $.project.path_with_namespace 'mexico/appcode/mi-repo'  → 'appcode'  (penúltimo segmento)
// Solo dispara con push a ramas (no tags) y no cuando el push borra la rama (after = 000...).
properties([
    pipelineTriggers([
        GenericTrigger(
            genericVariables: [
                [key: 'branch_name',     value: '$.ref',                         regexpFilter: '^refs/heads/'],
                [key: 'repository_name', value: '$.project.path_with_namespace', regexpFilter: '^.*/'],
                [key: 'appcode',         value: '$.project.path_with_namespace', regexpFilter: '^.*/(?=[^/]+/[^/]+$)|/[^/]+$'],
                [key: 'ref',             value: '$.ref'],
                [key: 'after',           value: '$.after']
            ],
            tokenCredentialId        : webhook_token_credential,
            causeString              : 'Webhook GitLab: $appcode/$repository_name @ $branch_name',
            printContributedVariables: false,
            printPostContent         : false,
            regexpFilterText         : '$ref $after',
            regexpFilterExpression   : '^refs/heads/\\S+ (?!0+$)[0-9a-f]+$'
        )
    ])
])

timeout(time: execution.time, unit: execution.units) {

    node(jenkins_agent_label) {
        withFolderProperties {
            try {
                if (!env.branch_name || !env.repository_name || !env.appcode) {
                    echo '⚠️ Sin datos del webhook (ejecución manual o primera ejecución para registrar el trigger): no se lanza ningún job.'
                } else {
                    currentBuild.description = "${env.appcode}/${env.repository_name} @ ${env.branch_name}"
                    disparar_pipeline_aplicacion(env.branch_name, env.repository_name, env.appcode)
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
