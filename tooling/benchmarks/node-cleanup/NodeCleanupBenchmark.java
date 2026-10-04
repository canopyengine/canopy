import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Locale;
import io.canopy.engine.core.managers.ManagersRegistry;
import io.canopy.engine.core.managers.SceneManager;
import io.canopy.engine.core.nodes.Node;
import io.canopy.engine.core.nodes.Node2D;
import io.canopy.engine.core.nodes.TreeSystem;
import io.canopy.engine.core.nodes.types.empty.EmptyNode2D;
import kotlin.Unit;
import kotlin.jvm.internal.Reflection;
import kotlin.reflect.KClass;

/** Identical harness for main and candidate; off-screen balanced 8-way scenes. */
public class NodeCleanupBenchmark {
    static volatile long sink;
    static class ReadingSystem extends TreeSystem {
        long reads;
        @SuppressWarnings({"unchecked", "rawtypes"})
        ReadingSystem() {
            super(TreeSystem.UpdatePhase.FramePost, 0,
                (KClass) Reflection.getOrCreateKotlinClass(Node2D.class));
        }
        int matchCount() { return getMatchingNodes().size(); }
        @Override protected void processNode(Node<?> node, float delta) {
            Node2D<?> transform = (Node2D<?>) node;
            reads += transform.getName().length() + (long) transform.getPosition().getX();
        }
    }
    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        if (args.length > 0 && args[0].equals("--validate-cleanup")) { validateCleanup(); return; }
        ThreadMXBean memory = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        memory.setThreadAllocatedMemoryEnabled(true);
        for (int count : new int[]{1000, 10000, 50000}) {
            for (int repeat = 0; repeat < 5; repeat++) {
                SceneManager scenes = new SceneManager(1f / 60f, scene -> Unit.INSTANCE);
                ManagersRegistry.INSTANCE.register(scenes);
                ReadingSystem system = new ReadingSystem();
                scenes.addSystem(system);
                scenes.onEnter();
                long created = System.nanoTime();
                ArrayList<EmptyNode2D> nodes = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    EmptyNode2D node = new EmptyNode2D("node-" + i, ignored -> Unit.INSTANCE);
                    nodes.add(node);
                    if (i != 0) nodes.get((i - 1) / 8).addChild(node);
                }
                scenes.setCurrScene(nodes.get(0));
                double construction = (System.nanoTime() - created) / 1e6;
                int iterations = count == 50000 ? 100 : 200;
                for (int i = 0; i < 150; i++) scenes.onUpdate(1f / 60f);
                long thread = Thread.currentThread().threadId();
                long allocated = memory.getThreadAllocatedBytes(thread);
                long start = System.nanoTime();
                for (int i = 0; i < iterations; i++) scenes.onUpdate(1f / 60f);
                double tick = (System.nanoTime() - start) / 1e6 / iterations;
                long bytes = (memory.getThreadAllocatedBytes(thread) - allocated) / iterations;
                sink = system.reads;
                String cleanupKind = "scene-detach";
                long cleanup = System.nanoTime();
                try {
                    Node.class.getMethod("queueFree").invoke(nodes.get(0));
                    scenes.onUpdate(0f);
                    if (scenes.getCurrScene() == null) cleanupKind = "queued-destruction";
                    else { scenes.setCurrScene(null); cleanupKind = "legacy-scene-detach"; }
                } catch (NoSuchMethodException baseline) { scenes.setCurrScene(null); }
                double cleanupMs = (System.nanoTime() - cleanup) / 1e6;
                scenes.onExit();
                ManagersRegistry.INSTANCE.exit();
                System.out.printf("%d,%d,%.6f,%d,%.3f,%.3f,%s%n",
                    count, repeat + 1, tick, bytes, construction, cleanupMs, cleanupKind);
            }
        }
    }
    /** Deterministic operation counts, independent of timing and GC. Candidate-only validation. */
    static void validateCleanup() throws Exception {
        for (int count : new int[]{1000, 10000, 50000}) {
            SceneManager scenes = new SceneManager(1f / 60f, scene -> Unit.INSTANCE);
            ManagersRegistry.INSTANCE.register(scenes);
            ReadingSystem system = new ReadingSystem();
            scenes.addSystem(system);
            scenes.onEnter();
            ArrayList<EmptyNode2D> nodes = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                EmptyNode2D node = new EmptyNode2D("node-" + i, ignored -> Unit.INSTANCE);
                nodes.add(node);
                if (i != 0) nodes.get((i - 1) / 8).addChild(node);
            }
            scenes.setCurrScene(nodes.get(0));
            long before = counter(scenes, "getIndexRemovalCount");
            Node.class.getMethod("queueFree").invoke(nodes.get(0));
            scenes.onUpdate(0f);
            long removals = counter(scenes, "getIndexRemovalCount") - before;
            long retained = counter(scenes, "getRetainedStateCount");
            long indexed = counter(scenes, "getIndexedNodeCount");
            if (removals != count || retained != 0 || indexed != 0 || system.matchCount() != 0) {
                throw new AssertionError("Incomplete or non-linear engine cleanup at " + count + " nodes");
            }
            for (Node<?> node : nodes) {
                if (node.isValid()) throw new AssertionError("Retained facade is still valid");
            }
            System.out.printf("%d,%d,%d,%d%n", count, removals, retained, indexed);
            scenes.onExit();
            ManagersRegistry.INSTANCE.exit();
        }
    }
    static long counter(SceneManager scenes, String prefix) throws Exception {
        for (java.lang.reflect.Method method : SceneManager.class.getMethods()) {
            if (method.getName().startsWith(prefix)) return ((Number) method.invoke(scenes)).longValue();
        }
        throw new AssertionError("Missing engine counter: " + prefix);
    }
}
