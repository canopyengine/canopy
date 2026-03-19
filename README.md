# Canopy Engine

<p align="center">
  <img src="logo.png" width="420" alt="Canopy Engine logo">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/version-0.0.1-red.svg">
  <img src="https://img.shields.io/badge/kotlin-2.3.10+-blue.svg">
  <img src="https://img.shields.io/badge/license-MIT-green.svg">
</p>

---

## ✨ What is Canopy?

**Canopy is a declarative 2D game engine built for Kotlin developers.**

It rethinks how games are structured by combining:

* 🌲 **Declarative structure** → declare how your game is structured via node-based DSL
* ⚡ **Reactive state** → changes propagate automatically
* 🧩 **Composable behaviors** → logic is modular and reusable

Instead of manually orchestrating update loops and state syncing, you **describe your game**, and Canopy handles the flow.

---

## 🧠 The Core Idea

Traditional engines revolve around:

> “What runs every frame?”

Canopy flips that into:

> **“What is the structure of my world, and how does it react to change?”**

---

## 🚀 Example

Build your game using a clean Kotlin DSL:

```kotlin
EmptyNode("level") {

    Player("player") {
        behavior(PlayerController())
    }

    Enemy("enemy")

    UI("ui")

}.asSceneRoot()
```

No manual update loops.
No tangled systems.

👉 Just structure + behavior.

---

## 🔥 Why Canopy?

### 1. Declarative, not imperative

You describe *what exists*, not *how to update it*. You do it via our ``node-based`` system

**Example**

````kotlin
EmptyNode("level"){
    
    Player()
    
    Enemy{
        at(200f, 100f)
        behavior(EnemyController())
        
        Weapon("gun")
    }
    
    UI()
}
````

---

### 2. Reactive by default

State changes automatically propagate through your game.

No manual wiring. No hidden dependencies.

````kotlin
val onKilled = event()
// Stateful events
val health = signal(100)
// Derived signals
val healthPercentage = computed{ "${health() / 100}%" }

// Derived callbacks
effect{
    log.info{ "Life: ${healthPercentage()}" }
    if(health() <= 0)
        onKilled.emit()
}
````

---

### 3. Structured, but flexible

* Organized like a tree
* Extensible like a framework
* Not locked into rigid engine patterns

---

### 4. Kotlin-first design

Built from the ground up for Kotlin:

* Modern language features
* DSL-driven APIs
* No Java-first compromises

---

## 🧭 Design Goals

Canopy is built with a few strong principles:

* **Clarity over cleverness**
  → Code should be readable and predictable

* **Composition over inheritance**
  → Build systems by combining behaviors

* **Reactivity over manual syncing**
  → State drives behavior automatically

* **Structure over chaos**
  → Your game should scale without becoming messy

* **Freedom without fragmentation**
  → Flexible, but still coherent

---

## ⚠️ Current Status

Canopy is **work in progress** and not yet production-ready.

### Current focus:

* 🧪 Headless runtime (terminal-based execution)
* ⚡ Reactive system foundations
* 🌲 Core node architecture

This allows:

* fast iteration
* simulation-driven development
* debugging without rendering overhead

👉 See the roadmap:
[Roadmap](https://github.com/canopyengine/canopy-docs/blob/main/markdown/misc/roadmap.md)

---

## 📚 Documentation

👉 [Canopy Docs](https://github.com/canopyengine/canopy-docs)

---

## 📦 Minimum Requirements

* **Kotlin 2.3.10+**

Canopy tracks modern Kotlin releases closely.

---

## 📜 License

MIT License — see [LICENSE-MIT](/LICENSE-MIT)

APACHE License - see [LICENSE-APACHE](/LICENSE-APACHE)
---

<p align="center">
  Canopy Engine • 2026
</p>

