package cn.arorms.fms.server.enums;

public enum ControlMode {
    LOCAL(0),
    REMOTE(1);

    private final int code;

    ControlMode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static ControlMode fromCode(int code) {
        for (ControlMode mode : values()) {
            if (mode.code == code) return mode;
        }
        throw new IllegalArgumentException("Unknown ControlMode code: " + code);
    }
}
