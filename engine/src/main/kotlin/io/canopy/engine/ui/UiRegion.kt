package io.canopy.engine.ui

import io.canopy.engine.core.nodes.Node

/** Marks a declarative builder receiver for the Canopy compiler's selective expression lowering. */
@Target(AnnotationTarget.CLASS)
annotation class DeclarativeUi

internal class UiRegion(name: String) : Node<UiRegion>(name, skipOnSearch = true) {
    internal var content by nodeProperty<UiScope?>(null)
    init {
        observeActivity({ content?.resume() }, { if (isValid) content?.suspend() })
    }
    override fun nodeInit() {
        markChildrenManaged()
    }
}
