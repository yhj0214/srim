package org.yhj.srim.repository.entity;

import java.util.List;

public enum ShareClassType {
    TOTAL(List.of("합계")),
    COMMON(List.of("보통주", "의결권 있는 주식")),
    PREFERRED(List.of("우선주", "종류주식", "의결권 없는 주식")),
    OTHER(List.of());

    private final List<String> aliases;

    ShareClassType(List<String> aliases) {
        this.aliases = aliases;
    }

    public static ShareClassType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return OTHER;
        }

        String value = raw.trim();

        for (ShareClassType type : values()) {
            if (type.matches(value)) {
                return type;
            }
        }

        return OTHER;
    }

    private boolean matches(String value) {
        return aliases.stream().anyMatch(value::contains);
    }
}
