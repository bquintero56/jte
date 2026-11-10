// Devuelve la ruta de la carpeta temporal del componente en los servidores JBoss: <config.path>/<component_name>.
// Valida la ruta antes de usarla en un rm -rf: nunca vacía, nunca la raíz ni la carpeta base sola.
String call() {
    def base      = String.valueOf(config.path ?: '').trim().replaceFirst(/\/+$/, '')
    def component = ''
    try { component = String.valueOf(component_name).trim() } catch (MissingPropertyException e) { component = '' }

    if (!base || !base.startsWith('/')) {
        error("❌ jboss.path no es válido: '${config.path}'. Debe ser una ruta absoluta (ej. /opt).")
    }
    if (!component || component.contains('/') || component.contains('..') || component.contains(' ')) {
        error("❌ component_name no es válido para la carpeta temporal: '${component}'.")
    }

    return "${base}/${component}"
}
