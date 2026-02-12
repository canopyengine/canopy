package canopy.utils

import com.badlogic.gdx.math.Vector2
import ktx.math.times

const val PPM = 64f // Pixels Per Meter

fun Vector2.toPixels() = this * PPM
