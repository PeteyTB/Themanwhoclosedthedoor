package dev.levrn.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;

/** Dependency-free behavioral checks; runnable even when game dependencies are unavailable. */
public final class CoreTest {
    private static int passed;
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("levrn-test-");
        try {
            var state = new ClientState();
            check(!state.enabled("aura") && !state.enabled("trigger"), "combat disabled by default");
            check(new HashSet<>(state.features().stream().map(f -> f.id).toList()).size() == state.features().size(), "unique IDs");
            check(state.search(Category.COMBAT, "TRIGGER").size() == 1, "case-insensitive category search");
            check(state.search(Category.VISUAL, "aura").isEmpty(), "search respects categories");
            state.setAttackRange(99); check(state.attackRange() == 3, "range upper bound");
            state.setAttackRange(-5); check(state.attackRange() == 1, "range lower bound");
            state.setAttackRange(Double.NaN); check(state.attackRange() == 1, "NaN rejected");
            state.setZoomFov(1); check(state.zoomFov() == 10, "zoom lower bound");
            state.setZoomFov(100); check(state.zoomFov() == 70, "zoom upper bound");
            var path = dir.resolve("nested/profile.properties");
            var store = new ConfigStore(path);
            store.load(state); check(state.attackRange() == 1, "missing profile does not mutate state");
            state.feature("sprint").toggle(); state.setTargetPlayers(true); state.setAttackRange(2.5);
            state.setTheme(Theme.GLACIER);
            store.save(state);
            var loaded = new ClientState(); store.load(loaded);
            check(loaded.enabled("sprint") && loaded.targetPlayers() && loaded.attackRange() == 2.5 && loaded.zoomFov() == 70, "profile round trip");
            check(loaded.theme() == Theme.GLACIER, "theme persists");
            check(Theme.EMBER.next() == Theme.DUSK, "theme cycle wraps");
            store.save(loaded); store.load(loaded);
            check(loaded.enabled("sprint"), "repeatable atomic save");
            Files.writeString(path, "schema=1\nmodule.sprint=false\ncombat.range=NaN\n");
            try { store.load(loaded); throw new AssertionError("malformed profile accepted"); }
            catch (java.io.IOException expected) { check(loaded.enabled("sprint") && loaded.attackRange() == 2.5, "failed load is transactional"); }
            check(Files.readString(path).contains("NaN"), "malformed file preserved");
            Files.writeString(path, "module.sprint=maybe\n");
            try { store.load(loaded); throw new AssertionError("invalid boolean accepted"); }
            catch (java.io.IOException expected) { check(true, "invalid boolean rejected"); }
            Files.writeString(path, "schema=42\n");
            try { store.load(loaded); throw new AssertionError("unknown schema accepted"); }
            catch (java.io.IOException expected) { check(true, "unknown schema rejected"); }
            Files.writeString(path, "visual.theme=UNKNOWN\n");
            try { store.load(loaded); throw new AssertionError("unknown theme accepted"); }
            catch (java.io.IOException expected) { check(loaded.theme() == Theme.GLACIER, "unknown theme preserves state"); }
            Files.writeString(path, "broken=" + "\\" + "uZZZZ\n");
            try { store.load(loaded); throw new AssertionError("invalid escape accepted"); }
            catch (java.io.IOException expected) { check(true, "invalid properties escape reported as IO failure"); }
            loaded.disableAll(); check(loaded.features().stream().noneMatch(Feature::enabled), "panic disables every module");
            loaded.reset(); check(loaded.enabled("hud") && !loaded.enabled("sprint") && !loaded.targetPlayers() && loaded.zoomFov() == 30, "reset restores defaults");
            check(loaded.theme() == Theme.DUSK, "reset restores theme");
            System.out.println("PASS: " + passed + " core behavior checks");
        } finally {
            try (var files = Files.walk(dir)) {
                for (Path p : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(p);
            }
        }
    }
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        passed++;
    }
}
