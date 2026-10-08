package io.canopy.engine.core.nodes;

import kotlin.Unit;

/** A no-plugin Java constructor participating through the explicit runtime boundary. */
public final class FailingConstructionJavaNode extends Node<FailingConstructionJavaNode> {
    private FailingConstructionJavaNode(Runnable cleanup, RuntimeException failure) {
        super("java-failed", false, node -> Unit.INSTANCE);
        onDestroy(() -> {
            cleanup.run();
            return Unit.INSTANCE;
        });
        throw failure;
    }

    public static FailingConstructionJavaNode construct(Runnable cleanup, RuntimeException failure) {
        return NodeConstructionKt.nodeConstruction(() -> new FailingConstructionJavaNode(cleanup, failure));
    }

    public static Node<?> builder(Runnable cleanup, RuntimeException failure) {
        return new BuilderNode(cleanup, failure);
    }

    private static final class BuilderNode extends Node<BuilderNode> {
        private BuilderNode(Runnable cleanup, RuntimeException failure) {
            super("java-parent", false, node -> {
                new FailingConstructionJavaNode(cleanup, failure);
                return Unit.INSTANCE;
            });
        }
    }

}
