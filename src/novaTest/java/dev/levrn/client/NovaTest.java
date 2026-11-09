package dev.levrn.client;

import dev.levrn.core.ClientState;
import dev.levrn.core.ConfigStore;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;

/** Tests the uploaded Nova model and its actual persistence, without starting Minecraft. */
public final class NovaTest {
    private static int passed;
    private static void check(boolean value,String why) {if(!value) throw new AssertionError(why);passed++;}
    public static void main(String[] args) throws Exception {
        var state=new ClientState();state.feature("adaptive").setEnabled(true);
        check(state.adaptiveEnabled(),"module checkbox enables Nova");state.setAdaptiveEnabled(false);
        check(!state.enabled("adaptive"),"inspector switch disables the same module");
        var signals=new PerformanceSignals();signals.record(PerformanceSignals.Signal.HIT_RATE,Double.NaN);
        check(signals.observations(PerformanceSignals.Signal.HIT_RATE)==0,"non-finite observations ignored");
        var weights=new WeightMatrix();var correction=PerformanceSignals.Signal.CORRECTION_RATE;
        weights.setWeight(WeightMatrix.ATTACK_RANGE,correction.ordinal(),0);
        var props=new Properties();weights.serialize(props,"w.");var restored=new WeightMatrix();restored.deserialize(props,"w.");
        check(restored.getWeight(WeightMatrix.ATTACK_RANGE,correction.ordinal())==0,"learned zero overrides nonzero default after reload");
        var engine=new AdaptiveEngine(signals,new WeightMatrix(),state);engine.setEnabled(true);
        engine.onAttackSent();engine.onAttackHit();double before=signals.get(PerformanceSignals.Signal.HIT_RATE);
        engine.setEnabled(true);engine.onAttackSent();
        check(Math.abs(signals.get(PerformanceSignals.Signal.HIT_RATE)-(before*.85+.5*.15))<1e-9,"syncing enabled state does not reset session on every tick");
        var noisy=new PerformanceSignals();for(int i=0;i<30;i++) noisy.record(correction,5);
        state.setAttackRange(2.5);var saves=new java.util.concurrent.atomic.AtomicInteger();var adjustment=new AdaptiveEngine(noisy,new WeightMatrix(),state,saves::incrementAndGet);adjustment.setEnabled(true);adjustment.evaluate();
        check(state.attackRange()<2.5 && state.attackRange()>=1,"observed corrections produce a bounded real setting adjustment");
        check(saves.get()>0,"actual adjustments request profile persistence");
        adjustment.restoreState(new double[]{Double.NaN},new double[]{Double.POSITIVE_INFINITY});
        check(adjustment.momentumArray()[0]==0 && adjustment.confidenceArray()[0]==0,"invalid restored learning state cannot poison model");
        adjustment.setEnabled(false);before=state.attackRange();adjustment.evaluate();
        check(state.attackRange()==before,"disabled Nova does not change settings");
        var first=new ServerProfile("first");var second=new ServerProfile("second");
        first.weightMatrix().setWeight(0,0,.8);second.weightMatrix().setWeight(0,0,-.4);
        check(Math.abs(ServerProfile.computeGlobalDefaults(List.of(first,second)).weightMatrix().getWeight(0,0)-.2)<1e-9,"cross-server defaults use a true weighted mean");
        var root=Files.createTempDirectory("nova-verification-");
        try {
            var store=new AdaptiveStore(root);var profile=store.getOrCreateProfile(null);
            var model=new AdaptiveEngine(profile.signals(),profile.weightMatrix(),state);model.setEnabled(true);
            model.onAttackSent();model.onAttackHit();model.onKill();model.momentumArray()[0]=.33;
            store.capture(model);store.save();
            var loaded=ServerProfile.load(root.resolve("nova"),"singleplayer");
            check(loaded!=null && loaded.momentum()[0]==.33,"checkpoint saves learned momentum");
            check(loaded.matchCount()==1 && loaded.signals().observations(PerformanceSignals.Signal.HIT_RATE)>0,"encounters and signals persist");
            store.capture(model);store.save();check(ServerProfile.load(root.resolve("nova"),"singleplayer").matchCount()==1,"autosave does not double count encounters");
            model.onAttackSent();model.setEnabled(false);store.capture(model);store.save();
            check(ServerProfile.load(root.resolve("nova"),"singleplayer").matchCount()==2,"disabling Nova finalizes an ongoing encounter before saving");
            check(ServerProfile.hashServerIp(null).equals("singleplayer"),"singleplayer identity is stable across sessions");
            var config=new ConfigStore(root.resolve("profile.properties"));state.setAdaptiveEnabled(true);config.save(state);var copy=new ClientState();config.load(copy);
            check(copy.adaptiveEnabled() && copy.enabled("adaptive"),"Nova toggle round trip stays consistent");
        } finally {try(var files=Files.walk(root)){for(var file:files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(file);}}
        System.out.println("PASS: "+passed+" Nova learning and persistence checks");
    }
}
