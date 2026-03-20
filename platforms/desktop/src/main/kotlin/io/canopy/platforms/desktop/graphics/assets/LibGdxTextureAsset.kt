package io.canopy.platforms.desktop.graphics.assets

import com.badlogic.gdx.graphics.Texture
import io.canopy.adapters.libgdx.data.assets.LibGdxAssetsManager
import io.canopy.engine.data.assets.FileSource

class LibGdxTextureAsset(private val texture: Texture) : AutoCloseable {
    override fun close() {
        texture.dispose()
    }

    fun unwrap(): Texture = texture
}

fun LibGdxAssetsManager.loadTexture(
    path: String,
    source: FileSource,
    customOptions: io.canopy.platforms.desktop.graphics.assets.LibGdxTextureAsset.() -> Unit,
) = _root_ide_package_.io.canopy.platforms.desktop.graphics.assets.LibGdxTextureAsset(
    Texture(
        resolveFile(
            path,
            source
        )
    )
).apply(customOptions)
