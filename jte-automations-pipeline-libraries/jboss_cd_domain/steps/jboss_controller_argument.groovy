// Argumento --controller de jboss-cli para el JBoss DOMAIN: a qué domain controller (host:puerto de administración) se conecta.
//   Valor: parámetro JBOSS_CONTROLLER del job (lo envía el CI desde la config del MICRO: <ambiente>.jboss_controller),
//          ej. '10.0.0.5:9990' o 'jboss-dc.empresa.local' (sin puerto → 9990).
//   Vacío: devuelve '' y jboss-cli usa su valor por defecto (localhost:9990 del servidor al que entra por SSH).
// Devuelve ' --controller=<host>:<puerto>' (con el espacio inicial) para pegarlo después de --connect.
String call() {
    def controller = String.valueOf(params.JBOSS_CONTROLLER ?: '').trim()

    if (!controller) {
        return ''
    }
    if (!(controller ==~ /[A-Za-z0-9._-]+(:[0-9]{1,5})?/)) {
        error("[ERROR]: ❌ JBOSS_CONTROLLER no válido: '${controller}' (formato host:puerto, ej. 10.0.0.5:9990).")
    }
    if (!controller.contains(':')) {
        controller = "${controller}:9990"
    }
    return " --controller=${controller}"
}
