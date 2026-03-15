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

## 🌲 Node-based declarative game engine built in Kotlin 🌲

[//]: # ([![Crates.io]&#40;https://img.shields.io/crates/v/bevy.svg&#41;]&#40;https://crates.io/crates/bevy&#41;)
[//]: # ([![Downloads]&#40;https://img.shields.io/crates/d/bevy.svg&#41;]&#40;https://crates.io/crates/bevy&#41;)
[//]: # ([![Docs]&#40;https://docs.rs/bevy/badge.svg&#41;]&#40;https://docs.rs/bevy/latest/bevy/&#41;)
[//]: # ([![CI]&#40;https://github.com/bevyengine/bevy/workflows/CI/badge.svg&#41;]&#40;https://github.com/canopyengine/canopy/actions&#41;)

---

Canopy is a: 
* node-based
* declarative 
* 2D game engine built in [Kotlin](https://kotlinlang.org/)

It's designed to be **Kotlin-native**, built with declarative APIs, node composition, and reactive patterns in mind to
create games in a clean, expressive, and maintainable way.

**Canopy** focuses on delivering a **simple yet powerful** experience, giving developers the flexibility to build complex
systems without sacrificing clarity or control.

[Oficial docs](http://github.com/canopyengine/canopy-docs)

---

## Design Goals

* Capable: Offer a complete set of 2D tools.
* Simple: Easy for beginners, flexible for experienced users.
* Modular: Use only what you need, replace what you don't.
* Fast: Your game should feel quick and snappy.
* Productive: Dev experience should be quick, and not bound by long compilation times.

---

## ⚠️ Work in progress ⚠️

**Canopy** is still a **work in progress**, and the **current version** is still unusable as is. Following the next weeks,
the goal will be to release a **Headless Version** capable of running the core features in the **terminal**.
See more details [here](https://github.com/canopyengine/canopy-docs/blob/main/docs/roadmap.md).

---

#### **Minimum Supported Kotlin Version**: **2.3.10**

> **Canopy** development aims to follow **Kotlin**'s improvements, so the minimum
supported version will usually be the latest **stable** version.


## Example Scene

Build your game with composition in mind, through our Kotlin DSL

````kotlin
EmptyNode("level"){
    
    Player("player"){
        behavior(PlayerController())
    }
    
    Enemy("enemy")
    
    UI("ui")
}.asSceneRoot()
````

# Documentation

You can see important information about the engine here:

| Section                                                              | Description            |
|----------------------------------------------------------------------|------------------------|
| [Introduction](docs/articles/introduction.md)                        | Overview of the engine |
| [Official Documentation](https://github.com/canopyengine/canopy-docs) | Complete engine manual |
| [Release Notes](docs/releases/releases.md)                           | Changelogs             |
| [Roadmap](docs/roadmap.md)                                           | Planned development    |


# License

Canopy is licensed under the **MIT License**.

See the [LICENSE](LICENSE) file for details.

---

<p align="center">
  Canopy Engine • 2026
</p>
