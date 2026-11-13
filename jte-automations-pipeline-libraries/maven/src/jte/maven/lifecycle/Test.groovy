package jte.maven.lifecycle

import jte.maven.exceptions.MavenPhaseExecutionException

class Test implements Serializable {
    private final def steps

    // Cobertura medida por el pipeline con UN SOLO agente de JaCoCo (el del pipeline, versión fija).
    private static final String JACOCO          = 'org.jacoco:jacoco-maven-plugin:0.8.12'
    private static final String JACOCO_AGENT    = 'org.jacoco:org.jacoco.agent:0.8.12:jar:runtime'
    private static final String AGENT_JAR       = 'org.jacoco.agent-0.8.12-runtime.jar'
    private static final String AGENT_DIR       = 'target/jte-jacoco-agent'                 // en el proyecto raíz
    private static final String JACOCO_EXEC     = 'target/jte-jacoco/jacoco.exec'           // relativo a cada módulo
    private static final String DEPENDENCY_PLUGIN = 'org.apache.maven.plugins:maven-dependency-plugin:3.6.1'

    Test(steps) {
        this.steps = steps
    }

    // 1) mvn clean y descarga el agente de JaCoCo 0.8.12 (jar runtime) a AGENT_DIR.
    // 2) Corre los tests con -Djacoco.skip=true (apaga el prepare-agent que tenga el pom del micro) y mete el agente del pipeline
    //    directo en el argLine de surefire. Si corrieran dos agentes (el del micro y el del pipeline) la JVM de los tests se cae con
    //    exit code 134 ("The forked VM terminated without properly saying goodbye").
    // 3) Borra cualquier target/site/jacoco previo y genera el reporte SOLO desde JACOCO_EXEC → target/site/jacoco/jacoco.xml
    //    (es el que lee SonarQube). Si la medición no se pudo hacer, no hay reporte: Sonar muestra 0%, nunca una cobertura inflada.
    void codeUnitTest() {
        def status_code = steps.sh(script: """#!/bin/sh
mvn clean && \\
mvn -N ${DEPENDENCY_PLUGIN}:copy -Dartifact=${JACOCO_AGENT} -DoutputDirectory=${AGENT_DIR}""", returnStatus: true)

        if (status_code != 0) {
            throw new MavenPhaseExecutionException('[run_unit_test_step]: Something went wrong preparing the JaCoCo agent (mvn clean / dependency:copy).')
        }

        status_code = steps.sh(script: """#!/bin/sh
mvn test -Djacoco.skip=true "-DargLine=-javaagent:\$PWD/${AGENT_DIR}/${AGENT_JAR}=destfile=${JACOCO_EXEC}" """, returnStatus: true)

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
