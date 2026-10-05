package local.codexlimits;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Base64;

final class CodexAuth {
    record Identity(String accountId, String email) { }

    static Identity read(Path auth) throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(auth)).getAsJsonObject();
        String accountId = string(root, "account_id");
        String email = string(root, "email");
        JsonObject tokens = object(root, "tokens");
        if (tokens != null) {
            if (accountId.isBlank()) accountId = string(tokens, "account_id");
            for (String key : new String[]{"id_token", "access_token"}) {
                JsonObject claims = jwt(string(tokens, key));
                if (claims == null) continue;
                if (email.isBlank()) email = string(claims, "email");
                if (accountId.isBlank()) accountId = first(claims, "chatgpt_account_id", "account_id", "sub");
            }
        }
        return new Identity(accountId, email);
    }

    static void copyAtomically(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING);
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }

    private static JsonObject jwt(String token) {
        if (token.isBlank()) return null;
        String[] parts = token.split("\\.");
        if (parts.length < 2) return null;
        try {
            String json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) { return null; }
    }
    private static JsonObject object(JsonObject o, String key) { return o.has(key) && o.get(key).isJsonObject() ? o.getAsJsonObject(key) : null; }
    private static String first(JsonObject o, String... keys) { for (String key : keys) { String value = string(o, key); if (!value.isBlank()) return value; } return ""; }
    private static String string(JsonObject o, String key) { return o != null && o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : ""; }
}
