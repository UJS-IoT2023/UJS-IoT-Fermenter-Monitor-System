package cn.arorms.fms.server.enums;

public enum ConnectionEventType {
    ONLINE(1),
    OFFLINE(0);

    private final int code;

    ConnectionEventType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static ConnectionEventType fromCode(int code) {
        for (ConnectionEventType type : values()) {
            if (type.code == code) return type;
        }
        throw new IllegalArgumentException("Unknown ConnectionEventType code: " + code);
    }

    public static ConnectionEventType fromString(String status) {
        return "online".equalsIgnoreCase(status) ? ONLINE : OFFLINE;
    }
}