package jte.maven.exceptions

class MavenPhaseExecutionException extends RuntimeException {
    MavenPhaseExecutionException(String message) {
        super(message)
    }
}
