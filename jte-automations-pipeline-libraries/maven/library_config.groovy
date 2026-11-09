fields {
    required {
        run_unit_test   = Boolean
        linter_scan     = Boolean
        settings_config = String
    }
    optional {
        jdk_version = String
        mvn_version = String
    }
}
