package jte.maven.lifecycle

import jte.maven.exceptions.MavenPhaseExecutionException

class Test implements Serializable {
    private final def steps

    Test(steps) {
        this.steps = steps
    }

    void codeUnitTest() {
        def status_code = 0

        status_code = steps.sh(script: 'mvn clean test', returnStatus: true)

        if (status_code != 0) {
            throw new MavenPhaseExecutionException('[run_unit_test_step]: Something went wrong on maven test.')
        }
    }
}
