import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import io.canopy.engine.core.managers.ManagersRegistry;
import io.canopy.engine.core.managers.SceneManager;
import io.canopy.engine.core.nodes.types.empty.EmptyNode;
import io.canopy.engine.input.events.TextInputEvent;
import kotlin.Unit;

public final class DeepTreeProbe {
    private static void operation(String operation, EmptyNode root, SceneManager manager) {
        switch (operation) {
            case "entry":
                root.nodeEnterTree();
                break;
            case "ready":
                root.nodeReady();
                break;
            case "frame":
                root.nodeUpdate(0);
                break;
            case "physics":
                root.nodePhysicsUpdate(0);
                break;
            case "input":
                root.nodeInput(new TextInputEvent("probe"));
                break;
            case "exit":
                root.nodeExitTree();
                break;
            case "destroy":
                root.queueFree();
                manager.onUpdate(0);
                break;
            case "detach":
                root.removeChild(root.getChildren().values().iterator().next());
                break;
            case "rename":
                root.setName("renamed");
                break;
            default:
                throw new IllegalArgumentException(operation);
        }
    }

    public static void main(String[] args) {
        String stage = "SETUP";
        String operation = args[0];
        int nodes = Integer.parseInt(args[1]);
        String shape = args[2];
        try {
            SceneManager manager = new SceneManager();
            ManagersRegistry.INSTANCE.register(manager);
            manager.onEnter();
            EmptyNode root = new EmptyNode("r", node -> Unit.INSTANCE);
            EmptyNode cursor = root;
            if (!operation.equals("entry")) {
                if (operation.equals("destroy")) {
                    root.nodeEnterTree();
                } else {
                    manager.setCurrScene(root);
                }
            }
            for (int index = 1; index < nodes; index++) {
                EmptyNode child = new EmptyNode(shape.equals("chain") ? "x" : "n" + index, node -> Unit.INSTANCE);
                (shape.equals("chain") ? cursor : root).addChild(child);
                cursor = child;
            }
            boolean repeated = operation.equals("ready") || operation.equals("frame") ||
                operation.equals("physics") || operation.equals("input");
            stage = "WARMUP";
            if (repeated) {
                for (int index = 0; index < 30; index++) {
                    operation(operation, root, manager);
                }
            }
            stage = "MEASURE";
            ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
            bean.setThreadAllocatedMemoryEnabled(true);
            long thread = Thread.currentThread().threadId();
            long before = bean.getThreadAllocatedBytes(thread);
            int count = repeated ? 30 : 1;
            for (int index = 0; index < count; index++) {
                operation(operation, root, manager);
            }
            long bytes = bean.getThreadAllocatedBytes(thread) - before;
            System.out.println("RESULT," + operation + "," + nodes + "," + shape + ",OK," + stage + "," +
                bytes / (double) count);
        } catch (StackOverflowError error) {
            System.out.println("RESULT," + operation + "," + nodes + "," + shape + ",STACK_OVERFLOW," + stage + ",0");
            System.exit(2);
        } catch (Throwable error) {
            error.printStackTrace(System.err);
            System.out.println("RESULT," + operation + "," + nodes + "," + shape + "," +
                error.getClass().getSimpleName() + "," + stage + ",0");
            System.exit(3);
        }
        // Each JVM terminates with its fixture: never retry cleanup after a sampled overflow.
    }
}
