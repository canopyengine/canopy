package io.canopy.engine.app

/**
 * Base configuration for a Canopy application backend.
 *
 * This class defines common configuration shared across different
 * platform backends (e.g. LWJGL, headless, future mobile/web backends).
 *
 * Backend implementations may extend this class to add additional
 * platform-specific settings (window size, vsync, fullscreen, etc.).
 *
 * Example:
 * ```
 * class DesktopConfig(
 *     title: String = "My Game",
 *     fps: Int = 60,
 *     val width: Int = 1280,
 *     val height: Int = 720
 * ) : CanopyAppConfig(title, fps)
 * ```
 *
 * @param title Window or application title.
 * @param fps   Target frames per second. Backends may use this to
 *              configure the render loop or frame limiter.
 */
open class AppConfig(val title: String = "Canopy Game", val fps: Int = 60)
