package jte.maven.lifecycle

import jte.maven.exceptions.MavenPhaseExecutionException

class Test implements Serializable {
    private final def steps

    // Cobertura medida por el pipeline (no depende del jacoco-maven-plugin del micro ni de su configuración)
    private static final String JACOCO      = 'org.jacoco:jacoco-maven-plugin:0.8.12'
    private static final String JACOCO_EXEC = 'target/jte-jacoco/jacoco.exec'   // ruta en el workspace (la crea JaCoCo; no existe en el repo)

    Test(steps) {
        this.steps = steps
    }

    // 1) Corre los tests con el agente de JaCoCo escribiendo en JACOCO_EXEC.
    // 2) Borra cualquier target/site/jacoco previo y genera el reporte SOLO desde JACOCO_EXEC → target/site/jacoco/jacoco.xml
    //    (es el que lee SonarQube). Si la medición no se pudo hacer, no hay reporte: Sonar muestra 0%, nunca una cobertura inflada.
    void codeUnitTest() {
        def status_code = 0

        status_code = steps.sh(script: "mvn clean ${JACOCO}:prepare-agent test -Djacoco.destFile=${JACOCO_EXEC}", returnStatus: true)

        if (status_code != 0) {
            throw new MavenPhaseExecutionException('[run_unit_test_step]: Something went wrong on maven test.')
        }

        steps.sh(script: '''#!/bin/sh
find . -type d -path "*/target/site/jacoco" -prune -exec rm -rf {} + 2>/dev/null || true''')

        status_code = steps.sh(script: "mvn ${JACOCO}:report -Djacoco.dataFile=${JACOCO_EXEC}", returnStatus: true)

        if (status_code != 0) {
            throw new MavenPhaseExecutionException('[run_unit_test_step]: Something went wrong generating the JaCoCo coverage report.')
        }
    }
}
