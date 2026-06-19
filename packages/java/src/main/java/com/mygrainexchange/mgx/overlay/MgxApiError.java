/* Hand-written overlay — not generated. */
package com.mygrainexchange.mgx.overlay;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mygrainexchange.mgx.ApiException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Typed error mapped from the API's {@code { "error": { status, code, message, errors[] } }}
 * envelope. Mirrors the {@code MgxApiError} exposed by every other MGX SDK.
 */
public class MgxApiError extends RuntimeException {

    /** A single field-level validation error. */
    public static final class FieldError {
        private final String field;
        private final String message;

        public FieldError(String field, String message) {
            this.field = field;
            this.message = message;
        }

        public String getField() {
            return field;
        }

        public String getMessage() {
            return message;
        }
    }

    private final int status;
    private final String code;
    private final List<FieldError> fieldErrors;

    public MgxApiError(int status, String code, String message, List<FieldError> fieldErrors) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = fieldErrors == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(fieldErrors));
    }

    /** HTTP status code (e.g. 422). */
    public int getStatus() {
        return status;
    }

    /** Machine-readable error code (e.g. {@code validation_failed}). */
    public String getCode() {
        return code;
    }

    /** Field-level validation errors, never null. */
    public List<FieldError> getFieldErrors() {
        return fieldErrors;
    }

    /**
     * Builds a typed error from a generated {@link ApiException}, parsing the
     * {@code { "error": { status, code, message, errors[] } }} envelope when present.
     */
    public static MgxApiError from(ApiException e) {
        int status = e.getCode();
        String code = "error";
        String message = e.getMessage() != null ? e.getMessage() : "Request failed";
        List<FieldError> fieldErrors = new ArrayList<>();

        String body = e.getResponseBody();
        if (body != null && !body.isEmpty()) {
            try {
                JsonElement root = JsonParser.parseString(body);
                if (root.isJsonObject()) {
                    JsonObject obj = root.getAsJsonObject();
                    JsonElement errEl = obj.get("error");
                    if (errEl != null && errEl.isJsonObject()) {
                        JsonObject err = errEl.getAsJsonObject();
                        if (err.has("status") && !err.get("status").isJsonNull()) {
                            status = err.get("status").getAsInt();
                        }
                        if (err.has("code") && !err.get("code").isJsonNull()) {
                            code = err.get("code").getAsString();
                        }
                        if (err.has("message") && !err.get("message").isJsonNull()) {
                            message = err.get("message").getAsString();
                        }
                        if (err.has("errors") && err.get("errors").isJsonArray()) {
                            JsonArray arr = err.get("errors").getAsJsonArray();
                            for (JsonElement item : arr) {
                                if (!item.isJsonObject()) {
                                    continue;
                                }
                                JsonObject fe = item.getAsJsonObject();
                                String field = fe.has("field") && !fe.get("field").isJsonNull()
                                        ? fe.get("field").getAsString() : "";
                                String msg = fe.has("message") && !fe.get("message").isJsonNull()
                                        ? fe.get("message").getAsString() : "";
                                fieldErrors.add(new FieldError(field, msg));
                            }
                        }
                    }
                }
            } catch (RuntimeException ignored) {
                // Non-JSON or unexpected error body — keep the HTTP status / generated message.
            }
        }

        return new MgxApiError(status, code, message, fieldErrors);
    }
}
