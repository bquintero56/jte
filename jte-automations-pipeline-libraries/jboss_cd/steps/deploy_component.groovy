// PRUEBA: por ahora solo muestra lo que recibió el job de CD (parámetros del CI y datos del servidor) para validar el flujo CI → CD.
// Aquí irá el despliegue real en JBoss.
// component_deployed: resultado de get_deployment_info (etapa "JBoss: deployment info" del template).
void call(Boolean component_deployed) {
    def component_name      = String.valueOf(params.COMPONENT_NAME ?: '').trim()
    def component_packaging = String.valueOf(params.COMPONENT_PACKAGING ?: '').trim()

    if (!component_name || !component_packaging) {
        error('[ERROR]: ❌ Faltan los parámetros COMPONENT_NAME / COMPONENT_PACKAGING (este job lo lanza el CI del micro).')
    }

    def ssh_user  = String.valueOf(config.user ?: env.JBOSS_SSH_USER ?: '').trim()
    def temp_base = String.valueOf(env.JBOSS_TEMP_PATH ?: '').trim().replaceFirst(/\/+$/, '')

    println("[INFO]: ===== Despliegue JBoss (PRUEBA: solo muestra los datos) =====")
    println("[INFO]: Job                 : ${env.JOB_NAME} #${env.BUILD_NUMBER}")
    println("[INFO]: Lanzado por         : ${currentBuild.getBuildCauses().collect { it.shortDescription }.join(', ')}")
    println("[INFO]: COMPONENT_NAME      : ${component_name}")
    println("[INFO]: COMPONENT_PACKAGING : ${component_packaging}")
    println("[INFO]: Carpeta del artefacto (misma del CI): ${temp_base ?: '<JBOSS_TEMP_PATH sin definir>'}/${component_name}/*.${component_packaging}")
    println("[INFO]: Host (deploy)       : ${config.host_deploy ?: '<host_deploy sin definir>'}")
    println("[INFO]: Usuario SSH         : ${ssh_user ?: '<JBOSS_SSH_USER sin definir>'}")

    println("[INFO]: ¿Desplegado actualmente? : ${component_deployed} (de la etapa JBoss: deployment info)")

    println("[INFO]: ✅ Job de CD recibió los datos correctamente")
}
