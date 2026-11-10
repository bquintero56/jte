// Devuelve la ruta de la carpeta temporal del componente en los servidores JBoss: <carpeta base>/<component_name>.
//   Carpeta base  : config.path (excepción por micro) o la propiedad de carpeta JBOSS_TEMP_PATH (global; la lee también el job de CD)
//   component_name: config.component_name (bloque jboss_ci) o, si no está, el keyword component_name
// Valida la ruta antes de usarla en un rm -rf: nunca vacía, nunca la raíz ni la carpeta base sola.
// Requiere estar dentro de withFolderProperties (el template ya lo hace).
String call() {
    def base      = String.valueOf(config.path ?: env.JBOSS_TEMP_PATH ?: '').trim().replaceFirst(/\/+$/, '')
    def component = String.valueOf(config.component_name ?: '').trim()
    if (!component) {
        try { component = String.valueOf(component_name).trim() } catch (MissingPropertyException e) { component = '' }
    }

    if (!base || !base.startsWith('/')) {
        error("❌ Carpeta base de JBoss no válida: '${base}'. Definir la propiedad de carpeta JBOSS_TEMP_PATH (ruta absoluta, ej. /opt) en la carpeta de Jenkins.")
    }
    if (!component || component.contains('/') || component.contains('..') || component.contains(' ')) {
        error("❌ component_name no es válido para la carpeta temporal: '${component}'.")
    }

    return "${base}/${component}"
}
