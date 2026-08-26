package ro.jf.ai.assistant.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private fun materialGlyph(
    name: String,
    pathData: String,
): ImageVector =
    ImageVector
        .Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(addPathNodes(pathData), fill = SolidColor(Color.Black))
        .build()

object AppIcons {
    val Tasks: ImageVector by lazy {
        materialGlyph(
            "Tasks",
            "M22,5.18L10.59,16.6l-4.24,-4.24l1.41,-1.41l2.83,2.83l10,-10L22,5.18z" +
                "M19.79,10.22c0.13,0.57 0.21,1.17 0.21,1.78c0,4.42 -3.58,8 -8,8s-8,-3.58 -8,-8s3.58,-8 8,-8" +
                "c1.58,0 3.04,0.46 4.28,1.25l1.44,-1.44C16.1,2.67 14.13,2 12,2C6.48,2 2,6.48 2,12s4.48,10 10,10" +
                "s10,-4.48 10,-10c0,-1.19 -0.22,-2.33 -0.6,-3.39L19.79,10.22z",
        )
    }

    val Chat: ImageVector by lazy {
        materialGlyph(
            "Chat",
            "M20,2L4,2C2.9,2 2,2.9 2,4v18l4,-4h14c1.1,0 2,-0.9 2,-2L22,4c0,-1.1 -0.9,-2 -2,-2z",
        )
    }
}
