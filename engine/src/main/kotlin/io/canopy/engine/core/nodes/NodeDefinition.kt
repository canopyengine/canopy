package io.canopy.engine.core.nodes

import java.lang.reflect.Modifier
import java.util.Collections
import io.canopy.engine.core.exceptions.InvalidNodeDefinitionException
import io.canopy.engine.core.queries.Dependency
import io.canopy.engine.data.assets.AssetDelegate

/** Cache immutable class metadata, never node instances. Also protects Java and precompiled consumers. */
internal object NodeDefinition {
    private val invalidFields = object : ClassValue<List<String>>() {
        override fun computeValue(type: Class<*>): List<String> {
            val result = mutableListOf<String>()
            var current: Class<*>? = type
            while (current != null && current != Node::class.java) {
                for (field in current.declaredFields) {
                    if (!Modifier.isStatic(field.modifiers) &&
                        field.type != NodeProperty::class.java &&
                        field.type != Dependency::class.java &&
                        field.type != AssetDelegate::class.java
                    ) {
                        result += "${current.simpleName}.${field.name}"
                    }
                }
                current = current.superclass
            }
            return Collections.unmodifiableList(result)
        }
    }
    fun validate(type: Class<*>) {
        val fields = invalidFields.get(type)
        if (fields.isNotEmpty()) throw InvalidNodeDefinitionException(type.name, fields)
    }
}
