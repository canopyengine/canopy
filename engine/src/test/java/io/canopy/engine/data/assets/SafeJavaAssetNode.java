package io.canopy.engine.data.assets;

import io.canopy.engine.core.nodes.Node;
import kotlin.Unit;

/** Bypasses compiler checks to verify the runtime metadata delegate allowlist. */
public class SafeJavaAssetNode extends Node<SafeJavaAssetNode> {
    public final AssetDelegate<?> resource;

    public SafeJavaAssetNode(AssetDelegate<?> resource) {
        super("safe-asset", false, node -> Unit.INSTANCE);
        this.resource = resource;
    }
}
