package canopy.input

import canopy.data.saving.registerSaveModule
import com.badlogic.gdx.Input
import ktx.log.logger

class InputMapper {
    private val logger = logger<InputMapper>()
    internal val mappings: MutableMap<String, MutableList<InputBind>> = mutableMapOf()

    init {
        clearMappings()
        registerSaveModule(
            "input",
            "input",
            InputData.serializer(),
            onSave = { this.asData() },
            onLoad = ::loadData,
        )
    }

    private fun loadData(data: InputData) {
        mappings.clear()
        mappings.putAll(data.mappings.associate { it.name to it.binds.toMutableList() })
    }

    fun mapToAction(bind: InputBind): List<String> = mappings.filterValues { bind in it }.keys.toList()

    fun clearMappings() {
        mappings.clear()
    }

    fun mapAction(
        action: String,
        newBinds: List<InputBind>,
        replace: Boolean = true,
    ) {
        logger.info { "Mapping action [$action] to: ${newBinds.map { Input.Keys.toString(it.code) }}" }

        val binds = mappings.computeIfAbsent(action) { mutableListOf() }
        if (replace) binds.clear()
        binds += newBinds
    }
}
