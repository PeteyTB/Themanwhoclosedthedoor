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
            check(Theme.NOVA.next() == Theme.DUSK, "theme cycle wraps");
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
            loaded.reset(); check(loaded.enabled("hud") && !loaded.enabled("sprint") && loaded.targetPlayers() && loaded.zoomFov() == 30, "reset restores defaults");
            check(loaded.theme() == Theme.NOVA, "reset restores theme");
            check(loaded.search(null, "aura,sprint").size() == 2, "multiple module OR search");
            check(loaded.search(null, "AUTO TOOL").size() == 1, "multiword module search");
            check(loaded.search(null, "aura | sprint").size() == 2, "pipe-separated search");
            var layout = WorkspaceLayout.arrange(loaded.features(), 2);
            check(layout.sections().stream().mapToInt(section -> section.features().size()).sum() == loaded.features().size(), "workspace includes every module");
            check(layout.sections().stream().allMatch(section -> section.column() < 2 && section.height() >= 27), "workspace column bounds");
            check(layout.height() >= layout.sections().stream().mapToInt(section -> section.top() + section.height()).max().orElse(0), "scroll extent reaches final row");
            var visual = loaded.search(Category.VISUAL, "");
            var spread = WorkspaceLayout.spreadCategory(visual, 4);
            check(spread.sections().stream().mapToInt(section -> section.features().size()).sum() == visual.size(), "category spreads every module across lanes");
            check(spread.height() < WorkspaceLayout.arrange(visual, 1).height(), "selected categories use available width");
            var enemy = new TargetPolicy.Candidate(false, true, false, false, true, false, false, true, false, true, 2.9, 30);
            check(TargetPolicy.reject(loaded, enemy).isEmpty(), "shared scoreboard team allowed by default");
            loaded.setRespectTeams(true);
            check(TargetPolicy.reject(loaded, enemy).equals("Team filtered"), "explicit team filter");
            loaded.setRespectTeams(false); loaded.setTargetPlayers(false);
            check(TargetPolicy.reject(loaded, enemy).equals("Target type filtered"), "players off has explicit diagnostic");
            loaded.setTargetPlayers(true);
            check(TargetPolicy.reject(loaded, new TargetPolicy.Candidate(false,true,false,false,true,false,false,false,false,false,2,0)).equals("Line of sight blocked"), "wall rejection");
            check(TargetPolicy.reject(loaded, new TargetPolicy.Candidate(false,true,false,false,true,false,false,false,false,true,3.01,0)).equals("Out of range"), "hitbox range enforced");
            check(TargetPolicy.reject(loaded, new TargetPolicy.Candidate(false,true,false,false,true,false,false,false,false,true,Double.NaN,0)).equals("Out of range"), "invalid geometry rejected");
            check(TargetPolicy.reject(loaded, new TargetPolicy.Candidate(false,true,false,false,true,true,false,false,false,true,1,0)).equals("Not attackable"), "creative players excluded");
            check(TargetPolicy.reject(loaded, new TargetPolicy.Candidate(false,true,false,false,true,false,false,false,true,true,1,0)).equals("Invisible filtered"), "invisible filter");
            loaded.setTargetInvisible(true);
            check(TargetPolicy.reject(loaded, new TargetPolicy.Candidate(false,true,false,false,true,false,false,false,true,true,1,0)).isEmpty(), "invisible opt in");
            check(Math.abs(TargetPolicy.approachAngle(179,-179,5)-181) < 0.001, "rotation wraps by shortest path");
            check(TargetPolicy.approachAngle(0,90,25)==25, "rotation speed bounded");
            loaded.setCooldown(5); check(loaded.cooldown()==1, "cooldown upper bound");
            loaded.setCooldown(-1); check(loaded.cooldown()==0.8, "cooldown lower bound");
            loaded.setTargetMobs(true); loaded.setRespectTeams(true); loaded.setRotateAura(false); loaded.setAimSpeed(35); loaded.setTargetFov(120); loaded.setTotemHealth(8);
            store.save(loaded); var copy = new ClientState(); store.load(copy);
            check(copy.targetMobs() && copy.respectTeams() && !copy.rotateAura() && copy.aimSpeed()==35 && copy.targetFov()==120 && copy.totemHealth()==8, "extended combat settings round trip");
            Files.writeString(path,"schema=1\ncombat.players=false\nvisual.theme=DUSK\n");store.load(copy);
            check(!copy.targetPlayers() && copy.targetMobs() && copy.theme()==Theme.DUSK, "legacy profile preserves explicit targeting and theme");
            Motion lowFps = new Motion(0), highFps = new Motion(0);
            for (int i=0;i<30;i++) lowFps.step(1,1.0/30,8);
            for (int i=0;i<144;i++) highFps.step(1,1.0/144,8);
            check(Math.abs(lowFps.value()-highFps.value())<1e-10,"motion is frame-rate independent");
            for (int i=0;i<200;i++) lowFps.step(0,1.0/60,18);
            check(lowFps.value()==0,"closing animation settles exactly");
            lowFps.snap(1); check(lowFps.value()==1,"reduced motion can snap directly");
            try { lowFps.step(0,Double.NaN,18);throw new AssertionError("invalid frame time accepted"); }
            catch(IllegalArgumentException expected) { check(lowFps.value()==1,"invalid frame time preserves motion state"); }
            try { lowFps.step(0,-1,18);throw new AssertionError("negative frame time accepted"); }
            catch(IllegalArgumentException expected) { check(true,"negative frame time rejected"); }
            check(Motion.mix(0xFF000000,0xFFFFFFFF,0)==0xFF000000 && Motion.mix(0xFF000000,0xFFFFFFFF,1)==0xFFFFFFFF,"color transition endpoints");
            check(Math.abs(Motion.quantize(1.034,1,3,0.05)-1.05)<1e-8,"slider value quantization");
            check(Motion.quantize(100,1,3,0.05)==3,"slider value clamping");
            ActionBudget budget=new ActionBudget();
            check(budget.take() && !budget.take() && budget.used(),"inventory modules share one transaction budget");
            budget.nextTick();check(!budget.used() && budget.take(),"inventory budget resets each tick");
            var targets=java.util.List.of(new TargetRetention.Candidate(1,2.5),new TargetRetention.Candidate(2,2.3));
            check(TargetRetention.choose(1,targets,true)==1,"target lock ignores small distance jitter");
            check(TargetRetention.choose(1,targets,false)==2,"nearest-target mode remains available");
            check(TargetRetention.choose(99,targets,true)==2,"invalid target lock is dropped");
            check(TargetRetention.choose(1,java.util.List.of(new TargetRetention.Candidate(1,3),new TargetRetention.Candidate(2,2)),true)==2,"substantially closer target replaces lock");
            check(TargetRetention.choose(1,java.util.List.of(),true)==-1,"empty target set clears lock");
            copy.setReducedMotion(true);copy.setMotionSpeed(2);copy.setArmorUpgrade(false);copy.setPreserveElytra(false);copy.setInventoryInterval(7);copy.setStickyTargets(false);copy.setWindowX(.2);copy.setWindowY(.8);copy.setReplenishBelow(14);
            store.save(copy);var motionCopy=new ClientState();store.load(motionCopy);
            check(motionCopy.reducedMotion() && motionCopy.motionSpeed()==2 && !motionCopy.armorUpgrade() && !motionCopy.preserveElytra() && !motionCopy.stickyTargets() && motionCopy.inventoryInterval()==7 && motionCopy.replenishBelow()==14,"new subsystem settings round trip");
            check(motionCopy.windowX()==.2 && motionCopy.windowY()==.8,"dragged window position persists");
            motionCopy.reset();check(!motionCopy.reducedMotion() && motionCopy.motionSpeed()==1 && motionCopy.windowX()==.5 && motionCopy.armorUpgrade(),"new subsystem defaults reset");
            Files.writeString(path,"schema=3\nui.motionSpeed=NaN\n");
            try { store.load(motionCopy);throw new AssertionError("invalid animation speed accepted"); }
            catch(java.io.IOException expected) { check(motionCopy.motionSpeed()==1,"malformed animation profile is transactional"); }
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
