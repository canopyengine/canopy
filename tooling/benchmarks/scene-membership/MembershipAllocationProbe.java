import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import io.canopy.engine.core.managers.ManagersRegistry;
import io.canopy.engine.core.managers.SceneManager;
import io.canopy.engine.core.nodes.types.empty.EmptyNode;
import kotlin.Unit;

/** Operation-only rename samples; construction/entry are outside every measured interval. */
public final class MembershipAllocationProbe {
    public static void main(String[] args) {
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId();
        System.out.println("nodes,repetition,bytes_per_rename,nanos_per_rename");
        for (int count : new int[] {128, 512, 1024}) {
            ManagersRegistry.INSTANCE.exit();
            SceneManager manager = new SceneManager();
            ManagersRegistry.INSTANCE.register(manager);
            manager.onEnter();
            EmptyNode root = new EmptyNode("a", node -> Unit.INSTANCE);
            manager.setCurrScene(root);
            EmptyNode cursor = root;
            for (int index = 1; index < count; index++) {
                EmptyNode child = new EmptyNode("x", node -> Unit.INSTANCE);
                cursor.addChild(child);
                cursor = child;
            }
            for (int index = 0; index < 100; index++) {
                root.setName((index & 1) == 0 ? "b" : "a");
            }
            for (int repetition = 0; repetition < 3; repetition++) {
                long before = bean.getThreadAllocatedBytes(thread);
                long start = System.nanoTime();
                for (int index = 0; index < 100; index++) {
                    root.setName((index & 1) == 0 ? "b" : "a");
                }
                long nanos = System.nanoTime() - start;
                long bytes = bean.getThreadAllocatedBytes(thread) - before;
                System.out.println(count + "," + repetition + "," + bytes / 100.0 + "," + nanos / 100.0);
            }
        }
    }
}
