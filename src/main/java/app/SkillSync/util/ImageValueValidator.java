package app.SkillSync.util;

import java.util.Base64;
import java.util.Locale;
import java.util.Set;

public final class ImageValueValidator {

    private static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_REMOTE_URL_LENGTH = 500;
    private static final Set<String> ALLOWED_DATA_IMAGE_PREFIXES = Set.of(
            "data:image/png;base64,",
            "data:image/jpeg;base64,",
            "data:image/webp;base64,",
            "data:image/svg+xml;base64,"
    );

    private ImageValueValidator() {
    }

    public static String normalizeOptionalImageValue(String value, String fieldName) {
        if (value == null || value.trim().isBlank()) {
            return null;
        }

        String normalized = value.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);

        if (lower.startsWith("https://") || lower.startsWith("http://")) {
            if (normalized.length() > MAX_REMOTE_URL_LENGTH) {
                throw new IllegalArgumentException(fieldName + " URL is too long.");
            }

            return normalized;
        }

        boolean allowedDataImage = ALLOWED_DATA_IMAGE_PREFIXES
                .stream()
                .anyMatch(lower::startsWith);

        if (!allowedDataImage) {
            throw new IllegalArgumentException(fieldName + " must be an image URL or uploaded PNG, JPG, WebP, or SVG.");
        }

        int commaIndex = normalized.indexOf(',');

        if (commaIndex < 0 || commaIndex == normalized.length() - 1) {
            throw new IllegalArgumentException(fieldName + " image data is invalid.");
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(normalized.substring(commaIndex + 1));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(fieldName + " image data is invalid.");
        }

        if (decoded.length >= MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException(fieldName + " must be smaller than 2 MB.");
        }

        return normalized;
    }
}
