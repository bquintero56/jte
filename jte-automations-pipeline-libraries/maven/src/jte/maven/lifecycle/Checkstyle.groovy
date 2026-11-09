package jte.maven.lifecycle

import jte.maven.exceptions.MavenPhaseExecutionException

class Checkstyle implements Serializable {
    private final def steps

    private static final String MVN_LOG = 'target/jte-checkstyle-mvn.log'   // salida de Maven, para distinguir violaciones de otros errores

    Checkstyle(steps) {
        this.steps = steps
    }

    // suppressions_file: ruta del checkstyle-suppressions.xml en el workspace ('' = sin suppressions)
    // Devuelve true si no hay violaciones y false si checkstyle encontró violaciones de estilo.
    // Cualquier otro fallo de Maven (repositorio local, settings, red, plugin...) lanza MavenPhaseExecutionException.
    boolean scanCode(String suppressions_file = '') {
        def args = suppressions_file ? " -Dcheckstyle.suppressions.location=${suppressions_file}" : ''

        def status_code = steps.sh(script: """#!/bin/sh
mkdir -p target
mvn checkstyle:check${args} > ${MVN_LOG} 2>&1
rc=\$?
cat ${MVN_LOG}
exit \$rc""", returnStatus: true)

        if (status_code == 0) {
            return true
        }

        def violations = steps.sh(script: "grep -q 'Checkstyle violation' ${MVN_LOG}", returnStatus: true) == 0
        if (violations) {
            return false
        }

        throw new MavenPhaseExecutionException('[check_code_styles_step]: Something went wrong on maven checkstyle (no son violaciones de estilo: revisar el error de Maven en el log).')
    }
}
