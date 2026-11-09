package jte.maven.lifecycle

import jte.maven.exceptions.MavenPhaseExecutionException

class Checkstyle implements Serializable {
    private final def steps

    Checkstyle(steps) {
        this.steps = steps
    }

    void scanCode() {
        def status_code = 0

        status_code = steps.sh(script: 'mvn checkstyle:check', returnStatus: true)

        if (status_code != 0) {
            throw new MavenPhaseExecutionException('[check_code_styles_step]: Something went wrong on maven checkstyle.')
        }
    }
}
