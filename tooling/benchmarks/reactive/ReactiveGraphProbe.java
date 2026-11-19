import com.sun.management.ThreadMXBean;
import io.canopy.engine.core.flows.events.Computed;
import io.canopy.engine.core.flows.events.InputCapture;
import io.canopy.engine.core.flows.events.InputRuntime;
import io.canopy.engine.core.flows.events.Signal;
import io.canopy.engine.core.managers.ManagersRegistry;
import io.canopy.engine.core.managers.SceneManager;
import io.canopy.engine.core.nodes.Node;
import io.canopy.engine.core.nodes.types.empty.EmptyNode;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import kotlin.Unit;

public final class ReactiveGraphProbe {
    private static volatile long sink;
    private static final Method constructorStorage = constructorStorage();

    private static Method constructorStorage() {
        try {
            Method method = Node.class.getDeclaredMethod("compilerPropertySet", String.class, Object.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void seedStorage(Node<?> owner, String key, Object value) {
        try {
            constructorStorage.invoke(owner, key, value);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void run(String kind, int size, String workload, ThreadMXBean memory) {
        ManagersRegistry.INSTANCE.exit();
        SceneManager scenes = new SceneManager();
        ManagersRegistry.INSTANCE.register(scenes);
        EmptyNode<?> owner = new EmptyNode<>("reactive-probe", ignored -> Unit.INSTANCE);
        scenes.setCurrScene(owner);

        Signal<Integer> source = new Signal<>(0, null);
        List<Computed<Integer>> computed = new ArrayList<>(size);
        String[] inputKeys = new String[size];
        long[] evaluations = {0};
        for (int index = 0; index < size; index++) {
            final int offset = index;
            if (kind.equals("computed")) {
                Computed<Integer> value = new Computed<>(() -> {
                    evaluations[0]++;
                    return source.invoke() + offset;
                }, owner);
                value.invoke();
                computed.add(value);
            } else {
                String key = "probe:" + index;
                InputCapture<Integer> capture = InputRuntime.INSTANCE.capture(
                    kind.equals("static-input") ? () -> {
                        evaluations[0]++;
                        return offset;
                    } : () -> {
                        evaluations[0]++;
                        return source.invoke() + offset;
                    }
                );
                seedStorage(owner, key, capture.getInitialValue());
                InputRuntime.INSTANCE.install(owner, key, capture);
                inputKeys[index] = key;
            }
        }

        int iterations = size == 1000 ? 200 : 40;
        for (int index = 0; index < 100; index++) step(kind, workload, size, source, computed, inputKeys, owner);
        for (int repetition = 1; repetition <= 5; repetition++) {
            long thread = Thread.currentThread().threadId();
            long allocated = memory.getThreadAllocatedBytes(thread);
            long evaluated = evaluations[0];
            long started = System.nanoTime();
            for (int index = 0; index < iterations; index++) {
                step(kind, workload, size, source, computed, inputKeys, owner);
            }
            long elapsed = System.nanoTime() - started;
            long bytes = memory.getThreadAllocatedBytes(thread) - allocated;
            long evaluationCount = evaluations[0] - evaluated;
            if (evaluationCount != (workload.equals("idle") ? 0 : (long) size * iterations)) {
                throw new AssertionError("Unexpected expression reevaluations: " + evaluationCount);
            }
            System.out.printf(Locale.ROOT, "%s,%d,%s,%d,%.1f,%.1f,%.1f%n", kind, size, workload, repetition,
                elapsed / (double) iterations, bytes / (double) iterations, evaluationCount / (double) iterations);
        }

        computed.forEach(Computed::dispose);
        source.dispose();
        owner.queueFree();
        scenes.onUpdate(0f);
        ManagersRegistry.INSTANCE.exit();
    }

    private static void step(
        String kind,
        String workload,
        int size,
        Signal<Integer> source,
        List<Computed<Integer>> computed,
        String[] inputKeys,
        EmptyNode<?> owner
    ) {
        if (workload.equals("fanout")) source.update(value -> value + 1);
        long total = 0;
        for (int index = 0; index < size; index++) {
            total += kind.equals("computed")
                ? computed.get(index).invoke()
                : InputRuntime.INSTANCE.read(owner, inputKeys[index], 0);
        }
        sink = total;
    }

    public static void main(String[] args) {
        ThreadMXBean memory = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        memory.setThreadAllocatedMemoryEnabled(true);
        System.out.println("kind,values,workload,repetition,ns_per_iteration,bytes_per_iteration,evaluations_per_iteration");
        for (int size : new int[]{1000, 10000}) {
            for (String kind : new String[]{"computed", "reactive-input", "static-input"}) {
                run(kind, size, "idle", memory);
                if (!kind.equals("static-input")) run(kind, size, "fanout", memory);
            }
        }
    }
}
