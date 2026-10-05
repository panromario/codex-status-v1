package local.codexlimits;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

final class AccountProfiles {
    static final String KEY = "uis.codex.limit.";
    record Profile(String id, String name, String email, String home, String addedAt, String registeredOn) {
        String displayName() { return email == null || email.isBlank() ? name : name + " · " + email; }
    }

    static Path systemHome() {
        String configured = System.getenv("CODEX_HOME");
        return (configured == null || configured.isBlank()
            ? Paths.get(System.getProperty("user.home"), ".codex")
            : Paths.get(configured)).toAbsolutePath().normalize();
    }
    static Path profilesRoot() { return systemHome().resolve("profiles"); }
    private static Path metadata() { return profilesRoot().resolve("accounts.json"); }
    private static Path activeFile() { return profilesRoot().resolve("active-profile"); }

    static synchronized List<Profile> all() {
        if (!Files.isRegularFile(metadata())) return List.of();
        try {
            JsonArray array = JsonParser.parseString(Files.readString(metadata())).getAsJsonArray();
            List<Profile> result = new ArrayList<>();
            for (JsonElement item : array) {
                JsonObject o = item.getAsJsonObject();
                result.add(new Profile(value(o, "id"), value(o, "name"), value(o, "email"), value(o, "home"),
                    value(o, "addedAt"), value(o, "registeredOn")));
            }
            return List.copyOf(result);
        } catch (Exception e) { return List.of(); }
    }

    static synchronized Profile active() {
        try {
            if (!Files.isRegularFile(activeFile())) return null;
            String id = Files.readString(activeFile()).trim();
            return all().stream().filter(p -> p.id().equals(id)).findFirst().orElse(null);
        } catch (IOException e) { return null; }
    }

    static synchronized Profile add(String name, String email, Path home, String registeredOn) throws IOException {
        List<Profile> profiles = new ArrayList<>(all());
        String id = UUID.randomUUID().toString();
        Profile profile = new Profile(id, name, email == null ? "" : email, home.toAbsolutePath().normalize().toString(),
            Instant.now().toString(), registeredOn);
        profiles.add(profile); save(profiles); return profile;
    }

    static synchronized void rename(Profile profile, String name) {
        replace(profile, new Profile(profile.id(), name, profile.email(), profile.home(), profile.addedAt(), profile.registeredOn()));
    }
    static synchronized void setRegistrationDate(Profile profile, String date) {
        replace(profile, new Profile(profile.id(), profile.name(), profile.email(), profile.home(), profile.addedAt(), date));
    }
    private static void replace(Profile old, Profile replacement) {
        List<Profile> profiles = new ArrayList<>(all());
        for (int i = 0; i < profiles.size(); i++) if (profiles.get(i).id().equals(old.id())) profiles.set(i, replacement);
        try { save(profiles); } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    static synchronized void select(Profile profile) throws IOException {
        Files.createDirectories(profilesRoot());
        Files.writeString(activeFile(), profile.id(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
    static synchronized void clearSelection() { try { Files.deleteIfExists(activeFile()); } catch (IOException ignored) { } }

    static synchronized void delete(Profile profile) throws IOException {
        boolean selected = profile.equals(active());
        Path profileHome = Path.of(profile.home()).toAbsolutePath().normalize();
        Path root = profilesRoot().toAbsolutePath().normalize();
        if (!profileHome.startsWith(root) || profileHome.equals(root))
            throw new IOException("Каталог профиля находится вне CODEX_HOME/profiles.");
        List<Profile> profiles = new ArrayList<>(all());
        profiles.removeIf(p -> p.id().equals(profile.id()));
        save(profiles);
        if (selected) clearSelection();
        deleteTree(profileHome);
    }

    private static void save(List<Profile> profiles) throws IOException {
        JsonArray array = new JsonArray();
        for (Profile p : profiles) {
            JsonObject o = new JsonObject();
            o.addProperty("id", p.id()); o.addProperty("name", p.name()); o.addProperty("email", p.email());
            o.addProperty("home", p.home()); o.addProperty("addedAt", p.addedAt()); o.addProperty("registeredOn", p.registeredOn());
            array.add(o);
        }
        Files.createDirectories(profilesRoot());
        Path temp = Files.createTempFile(profilesRoot(), "accounts", ".tmp");
        try {
            Files.writeString(temp, new GsonBuilder().setPrettyPrinting().create().toJson(array), StandardCharsets.UTF_8);
            try { Files.move(temp, metadata(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, metadata(), StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }
    private static String value(JsonObject o, String key) { return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : ""; }
}
