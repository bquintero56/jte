// Ejecuta el job de Jenkins en la ruta indicada.
// config.esperarJob: espera a que termine; config.propagarError: si falla, el orquestador también.
// config.ignorarSiNoExiste: si el job no existe solo avisa (útil si no todos los repos tienen job).
// config.parametroRama: si viene, pasa la rama al job como parámetro String con ese nombre.
void call(String ruta, String rama) {
    def esperar    = config.esperarJob
    def propagar   = esperar && config.propagarError
    def parametro  = String.valueOf(config.parametroRama ?: '').trim()
    def parametros = parametro ? [string(name: parametro, value: rama)] : []

    if (parametro) {
        echo "Parámetro ${parametro} = ${rama}"
    }

    try {
        def resultado = build(job: ruta, wait: esperar, propagate: propagar, parameters: parametros)

        if (esperar) {
            def estado = resultado.result
            echo "${estado == 'SUCCESS' ? '✅' : '❌'} Job ${ruta} #${resultado.number} terminó en ${estado}"
            if (estado != 'SUCCESS' && !propagar) {
                currentBuild.result = 'UNSTABLE'
            }
        } else {
            echo "✅ Job ${ruta} lanzado (sin esperar a que termine)"
        }
    }
    catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
        echo "❌ Job ${ruta} falló o fue abortado"
        throw e
    }
    catch (Exception e) {
        def mensaje = e.message ?: e.toString()
        if (mensaje.contains('No item named')) {
            if (config.ignorarSiNoExiste) {
                echo "⚠️ No existe el job '${ruta}' en Jenkins: no se ejecuta nada."
                return
            }
            error "❌ No existe el job '${ruta}' en Jenkins. Revisar plantillaRutaJob / mapeoRutas o crear el job."
        }
        error "❌ No se pudo ejecutar el job '${ruta}': ${mensaje}"
    }
}
