package jte.maven.lifecycle

import jte.maven.exceptions.MavenPhaseExecutionException

class Compile implements Serializable {
    private final def steps

    Compile(steps) {
        this.steps = steps
    }

    void applicationCode() {
        def status_code = 0

        status_code = steps.sh(script: 'mvn compile', returnStatus: true)

        if (status_code != 0) {
            throw new MavenPhaseExecutionException('[compile_code_step]: Something went wrong on maven compile.')
        }
    }
}
