// Propiedad del job con la conexión de GitLab (Manage Jenkins → System → GitLab → Connection name).
// La usan gitlab_stage / update_gitlab_status para informar el estado de cada etapa en GitLab.
// Se agrega en properties([...]) del template: properties() reemplaza lo que se marque a mano en la pantalla del job.
def call() {
    def connection_name = 'gitlab'      // AJUSTAR: Connection name exacto de la conexión creada en System
    return gitLabConnection(gitLabConnection: connection_name)
}
