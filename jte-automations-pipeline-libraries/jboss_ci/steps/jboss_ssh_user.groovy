// Devuelve el usuario SSH para conectarse a los servidores JBoss.
//   1) config.user (bloque jboss_ci) si un micro necesita un usuario distinto
//   2) si no, la propiedad de carpeta JBOSS_SSH_USER (global, definida una vez en la carpeta 'mexico' de Jenkins)
// Requiere estar dentro de withFolderProperties (el template ya lo hace).
String call() {
    def user = String.valueOf(config.user ?: env.JBOSS_SSH_USER ?: '').trim()
    if (!user) {
        error('❌ Falta el usuario SSH de JBoss: definir la propiedad de carpeta JBOSS_SSH_USER en la carpeta de Jenkins (o jboss_ci.user en la config).')
    }
    return user
}
