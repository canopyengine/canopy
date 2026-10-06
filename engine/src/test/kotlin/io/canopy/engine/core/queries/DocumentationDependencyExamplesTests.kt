package io.canopy.engine.core.queries.examples

import kotlin.test.Test
import io.canopy.engine.core.flows.Context
import io.canopy.engine.core.managers.Manager
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.manager as getManager
import io.canopy.engine.core.managers.managerOrNull as getManagerOrNull
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.queries.ancestor
import io.canopy.engine.core.queries.childOrNull
import io.canopy.engine.core.queries.context
import io.canopy.engine.core.queries.contextOrNull
import io.canopy.engine.core.queries.group
import io.canopy.engine.core.queries.manager
import io.canopy.engine.core.queries.managerOrNull
import io.canopy.engine.core.queries.tree
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

interface Scores : Manager {
    val total: Int
}

class GameScores : Scores {
    override val total = 10
}

class ScoreDisplay {
    val scores by manager<Scores>()
    val optionalScores by managerOrNull<Scores>()
}

fun lookupExample() {
    val display = ScoreDisplay()
    check(getManagerOrNull<Scores>() == null)
    check(display.optionalScores == null)
    ManagersRegistry.register(GameScores())
    check(display.scores.total == 10)
    check(getManager<Scores>() === display.scores)
    ManagersRegistry.unregister(Scores::class)
    check(display.optionalScores == null)
}

class Rules(val difficulty: Int)

class Actor(name: String) : Node<Actor>(name) {
    val scope by ancestor<Context>()
    val attachment by childOrNull<Actor>()
    val firstActor by tree<Actor>()
    val allies by group<Actor>("allies")
    val rules by context<Rules>()
    val label by contextOrNull<String>("label")
}

fun nodeLookupExample() {
    val scenes = SceneManager()
    ManagersRegistry.register(scenes)
    val scope = Context("Level")
    scope.provide<Rules> { Rules(2) }
    scope.provide("label") { "forest" }
    val actor = Actor("Player")
    actor.addGroup("allies")
    scope.addChild(actor)
    check(actor.rules.difficulty == 2) // Context lookup works before entry.
    scenes.currScene = scope
    check(actor.firstActor === actor)
    check(actor.allies == listOf(actor))
    check(actor.label == "forest")
}

/** Compiles and runs the complete dependency manual examples against the real engine API. */
class DocumentationDependencyExamplesTests {
    @BeforeEach
    fun setup() = ManagersRegistry.exit()

    @AfterEach
    fun cleanup() = ManagersRegistry.exit()

    @Test
    fun `global lookup guide observes optional absence and registration`() {
        lookupExample()
    }

    @Test
    fun `node lookup guide distinguishes detached context from entered tree and groups`() {
        nodeLookupExample()
    }
}
