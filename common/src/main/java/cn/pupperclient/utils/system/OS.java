package cn.pupperclient.utils.system;

public enum OS {
    WINDOWS("win"),
    MACOS("mac"),
    LINUX("nix", "nux", "aix"),
    UNKNOWN();

    private final String[] identifiers;

    OS(String... identifiers) {
        this.identifiers = identifiers;
    }

    private static final OS CURRENT_OS = detectOS();

    private static OS detectOS() {
        String osName = System.getProperty("os.name").toLowerCase();
        return detectOS(osName);
    }

    private static OS detectOS(String osName) {
        return java.util.Arrays.stream(values())
            .filter(os -> os != UNKNOWN)
            .filter(os -> java.util.Arrays.stream(os.identifiers)
                .anyMatch(osName::contains))
            .findFirst()
            .orElse(UNKNOWN);
    }

    public static boolean isWindows() {
        return CURRENT_OS == WINDOWS;
    }

    public static boolean isMacOS() {
        return CURRENT_OS == MACOS;
    }

    public static boolean isLinux() {
        return CURRENT_OS == LINUX;
    }

    public static OS getCurrent() {
        return CURRENT_OS;
    }
}
