package io.canopy.engine.data.assets;

import io.canopy.engine.core.nodes.Node;
import kotlin.Unit;

/** A raw resource field must fail runtime validation before node state is allocated. */
public class UnsafeJavaAssetNode extends Node<UnsafeJavaAssetNode> {
    public CanopyAsset resource;

    public UnsafeJavaAssetNode() {
        super("unsafe-asset", false, node -> Unit.INSTANCE);
    }
}
