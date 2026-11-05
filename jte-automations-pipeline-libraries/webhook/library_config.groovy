fields {
    required {
        prefijoRuta    = String    // primera carpeta de la ruta del job (ej. 'mexico')
        ramasAmbiente  = List      // ramas que tienen job propio (ej. ['dev', 'qa', 'uat', 'release', 'main'])
        jobIntegracion = String    // job para cualquier otra rama (ej. 'integration')
        parametroRama  = String    // parámetro String del job donde va la rama (ej. 'BRANCH')
    }
}
