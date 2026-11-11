package io.canopy.engine.core.flows.events

/** Standard mutable interfaces backed by owned copies. Read and mutation hooks never retain the source. */
internal class ObservableList<E>(source: Collection<E>, private val read: () -> Unit, private val changed: () -> Unit) :
    AbstractMutableList<E>() {
    private val items = ArrayList(source)
    override val size: Int get() {
        read()
        return items.size
    }
    override fun get(index: Int): E {
        read()
        return items[index]
    }
    override fun add(index: Int, element: E) {
        items.add(index, element)
        modCount++
        changed()
    }
    override fun removeAt(index: Int): E {
        val old = items.removeAt(index)
        modCount++
        changed()
        return old
    }
    override fun set(index: Int, element: E): E {
        val old = items.set(index, element)
        if (old != element) changed()
        return old
    }
    override fun addAll(elements: Collection<E>): Boolean = addAll(items.size, elements)
    override fun removeRange(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        items.subList(fromIndex, toIndex).clear()
        modCount++
        changed()
    }
    override fun addAll(index: Int, elements: Collection<E>): Boolean {
        if (!items.addAll(index, elements.toList())) return false
        modCount++
        changed()
        return true
    }
    override fun clear() {
        if (items.isEmpty()) return
        items.clear()
        modCount++
        changed()
    }
}

internal class ObservableSet<E>(source: Collection<E>, private val read: () -> Unit, private val changed: () -> Unit) :
    AbstractMutableSet<E>() {
    private val items = LinkedHashSet(source)
    override val size: Int get() {
        read()
        return items.size
    }
    override fun contains(element: E): Boolean {
        read()
        return items.contains(element)
    }
    override fun add(element: E): Boolean = items.add(element).also { if (it) changed() }
    override fun remove(element: E): Boolean = items.remove(element).also { if (it) changed() }
    override fun clear() {
        if (items.isEmpty()) return
        items.clear()
        changed()
    }
    override fun iterator(): MutableIterator<E> {
        read()
        val iterator = items.iterator()
        return object : MutableIterator<E> {
            override fun hasNext(): Boolean {
                read()
                return iterator.hasNext()
            }
            override fun next(): E {
                read()
                return iterator.next()
            }
            override fun remove() {
                iterator.remove()
                changed()
            }
        }
    }
}

internal class ObservableMap<K, V>(source: Map<K, V>, private val read: () -> Unit, private val changed: () -> Unit) :
    AbstractMutableMap<K, V>() {
    private val items = LinkedHashMap(source)
    override fun put(key: K, value: V): V? {
        val present = items.containsKey(key)
        val old = items.put(key, value)
        if (!present || old != value) changed()
        return old
    }
    override fun clear() {
        if (items.isEmpty()) return
        items.clear()
        changed()
    }
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> =
        object : AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
            override val size: Int get() {
                read()
                return items.size
            }
            override fun add(element: MutableMap.MutableEntry<K, V>): Boolean =
                throw UnsupportedOperationException("Add map entries using put")
            override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> {
                read()
                val iterator = items.entries.iterator()
                return object : MutableIterator<MutableMap.MutableEntry<K, V>> {
                    override fun hasNext(): Boolean {
                        read()
                        return iterator.hasNext()
                    }
                    override fun remove() {
                        iterator.remove()
                        changed()
                    }
                    override fun next(): MutableMap.MutableEntry<K, V> {
                        read()
                        val entry = iterator.next()
                        return object : MutableMap.MutableEntry<K, V> {
                            override val key: K get() {
                                read()
                                return entry.key
                            }
                            override val value: V get() {
                                read()
                                return entry.value
                            }
                            override fun setValue(newValue: V): V {
                                val old = entry.setValue(newValue)
                                if (old != newValue) changed()
                                return old
                            }
                            override fun equals(other: Any?): Boolean =
                                other is Map.Entry<*, *> && key == other.key && value == other.value
                            override fun hashCode(): Int = (key?.hashCode() ?: 0) xor (value?.hashCode() ?: 0)
                            override fun toString(): String = "$key=$value"
                        }
                    }
                }
            }
        }
}
