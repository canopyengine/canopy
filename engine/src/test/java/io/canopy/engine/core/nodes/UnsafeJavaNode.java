package io.canopy.engine.core.nodes;

import kotlin.Unit;

/** Deliberately bypasses Kotlin compiler integration to exercise the runtime boundary. */
public class UnsafeJavaNode extends Node<UnsafeJavaNode> {
    public Object resource = new Object();

    public UnsafeJavaNode() {
        super("unsafe", false, node -> Unit.INSTANCE);
    }
}
