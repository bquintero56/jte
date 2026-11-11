package jte.maven.lifecycle

import jte.maven.exceptions.MavenPhaseExecutionException

class DependencyScan implements Serializable {
    private final def steps

    private static final String MVN_LOG = 'target/jte-dependency-tree-mvn.log'   // salida de dependency:tree (se valida desde aquí)

    DependencyScan(steps) {
        this.steps = steps
    }

    // includes: lista groupId:artifactId para -Dincludes (solo muestra esas ramas del árbol).
    // Devuelve la salida de mvn dependency:tree (también queda en el log). Si Maven falla lanza MavenPhaseExecutionException.
    String dependencyTree(List includes) {
        def status_code = steps.sh(script: """#!/bin/sh
mkdir -p target
mvn dependency:tree -Dincludes=${includes.join(',')} > ${MVN_LOG} 2>&1
rc=\$?
cat ${MVN_LOG}
exit \$rc""", returnStatus: true)

        if (status_code != 0) {
            throw new MavenPhaseExecutionException('[scan_vulnerable_dependencies_step]: No se pudo generar el árbol de dependencias (mvn dependency:tree). Revisa el error de Maven en el log y el pom.xml.')
        }
        return steps.readFile(MVN_LOG)
    }

    // Ruta del repositorio local de Maven que usa este build (la de settings.xml / withMaven). '' si no se puede obtener.
    String localRepository() {
        def repo = steps.sh(script: '''#!/bin/sh
mvn -q org.apache.maven.plugins:maven-help-plugin:3.4.0:evaluate -Dexpression=settings.localRepository -DforceStdout 2>/dev/null''',
                            returnStdout: true).trim()
        return repo.startsWith('/') ? repo : ''
    }
}
