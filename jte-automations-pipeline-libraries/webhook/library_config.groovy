fields {
    required {
        prefijoRuta    = String    // primera carpeta de la ruta del job (ej. 'mexico')
        jobIntegracion = String    // job para cualquier otra rama (ej. 'integration')
        parametroRama  = String    // parámetro String del job donde va la rama (ej. 'BRANCH')
    }
}
