package jte.maven.lifecycle

import jte.maven.exceptions.MavenPhaseExecutionException

class Pack implements Serializable {
    private final def steps

    Pack(steps) {
        this.steps = steps
    }

    void applicationBinary() {
        def status_code = 0

        status_code = steps.sh(script: 'mvn clean package -Dmaven.test.skip=true', returnStatus: true)

        if (status_code != 0) {
            throw new MavenPhaseExecutionException('[build_component_step]: Something went wrong on maven package.')
        }
    }
}
