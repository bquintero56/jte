// Arma la ruta completa del job de Jenkins para el evento (grupo, repo, rama).
// Usa config.mapeoRutas['grupo/repo'] si existe; si no, config.plantillaRutaJob.
String call(Map evento) {
    def rama = evento.rama
    if (config.codificarRama) {
        rama = rama.replace('/', '%2F')               // así nombra Jenkins los jobs de rama en un multibranch
    }

    def mapeo     = config.mapeoRutas ?: [:]
    def plantilla = mapeo[evento.proyecto] ?: config.plantillaRutaJob
    def origen    = mapeo[evento.proyecto] ? "mapeoRutas['${evento.proyecto}']" : 'plantillaRutaJob'

    def ruta = plantilla
        .replace('{grupo}', evento.grupo)
        .replace('{repo}',  evento.repo)
        .replace('{rama}',  rama)
        .replaceAll(/\/+/, '/')                        // sin grupo no deja '//'
        .replaceFirst(/^\//, '')

    echo "✅ Ruta del job: ${ruta}  (${origen}: '${plantilla}')"
    return ruta
}
