<p align="center">
  <img src="docs/assets/canopy-logo-no-bg.png" width="420" alt="Canopy Engine logo">
</p>

<p align="center">
  <b>Canopy</b> is a modular <b>2D game engine written in Kotlin</b>.
</p>

<p align="center">
  Built around <b>node-based architecture</b>, <b>composable behaviors</b>, and <b>reactive state systems</b>.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/version-0.0.1-red.svg">
  <img src="https://img.shields.io/badge/kotlin-2.3.10+-blue.svg">
  <img src="https://img.shields.io/badge/license-MIT-green.svg">
</p>

---

# 🌲 What is Canopy?

**Canopy** is a modern 2D engine designed for developers who want:

- a **clear architecture**
- **modular gameplay systems**
- **Kotlin-first development**
- a **composable runtime model**

Instead of large monolithic objects, Canopy encourages building games from **small composable pieces**.

---

# Core Architecture

Canopy is structured around a few simple ideas:

```text
Application
   ↓
Scene Manager
   ↓
Scene Root
   ↓
Node Tree
   ↓
Behaviors
   ↓
Tree Systems
````

Additional runtime systems provide:

```
Signals
Events
Contexts
Managers
Data Pipelines
```

This architecture allows game logic to remain **modular and scalable**.

---

# Example Scene

Scenes are built using a Kotlin DSL.

```kotlin
EmptyNode("root") {

    Player {
        behavior(PlayerController())
        behavior(Move())
    }

    Enemy()

    UI()

}.asSceneRoot()
```

Nodes define **structure**, while behaviors attach **gameplay logic**.

---

# Documentation

This repository hosts the **official documentation** for the engine.

| Section                                       | Description            |
| --------------------------------------------- | ---------------------- |
| [Introduction](docs/articles/introduction.md) | Overview of the engine |
| [Full Documentation](markdown/index.md)       | Complete engine manual |
| [Release Notes](docs/releases/releases.md)    | Changelogs             |
| [Roadmap](docs/roadmap.md)                    | Planned development    |

---

# Current Status

⚠️ **Canopy is currently in early development.**

The architecture is evolving and both the engine and documentation may introduce breaking changes.

You can follow development progress in the [roadmap](docs/roadmap.md).

---

# Minimum Kotlin Version

```
Kotlin 2.3.10
```

Canopy generally tracks the **latest stable Kotlin release**.

---

# Project Goals

The engine aims to provide:

* a **clean runtime architecture**
* **modular gameplay composition**
* a **modern reactive state system**
* a **flexible content pipeline**

Canopy prioritizes **clarity and maintainability** over complexity.

---

# License

Canopy is licensed under the **MIT License**.

See the [LICENSE](LICENSE) file for details.

---

<p align="center">
  Canopy Engine • 2026
</p>
