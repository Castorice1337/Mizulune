package shit.zen.protocol.heypixel;

/** Loader-neutral view of how the JVM instrumentation became available. */
enum ProtocolStartupMode {
    NONE("none"),
    PREMAIN("premain"),
    AGENTMAIN("agentmain");

    private final String propertyValue;

    ProtocolStartupMode(String propertyValue) {
        this.propertyValue = propertyValue;
    }

    static ProtocolStartupMode fromSystemProperty() {
        String value = System.getProperty("oz.agent.startupMode");
        if (value == null) return NONE;
        for (ProtocolStartupMode mode : values()) {
            if (mode.propertyValue.equalsIgnoreCase(value.trim())) return mode;
        }
        return NONE;
    }
}
