package jte.maven.lifecycle

import jte.maven.exceptions.MavenPhaseExecutionException

class Checkstyle implements Serializable {
    private final def steps

    Checkstyle(steps) {
        this.steps = steps
    }

    // suppressions_file: ruta del checkstyle-suppressions.xml en el workspace ('' = sin suppressions)
    void scanCode(String suppressions_file = '') {
        def status_code = 0
        def args        = suppressions_file ? " -Dcheckstyle.suppressions.location=${suppressions_file}" : ''

        status_code = steps.sh(script: "mvn checkstyle:check${args}", returnStatus: true)

        if (status_code != 0) {
            throw new MavenPhaseExecutionException('[check_code_styles_step]: Something went wrong on maven checkstyle.')
        }
    }
}
