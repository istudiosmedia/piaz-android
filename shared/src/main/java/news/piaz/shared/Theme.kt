package news.piaz.shared

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Brand {
    val deep = Color(0xFF000D1F)
    val night = Color(0xFF001A33)
    val navy = Color(0xFF001B44)
    val turquoise = Color(0xFF35D4D2)
    val coral = Color(0xFFFF5E5B)
    val slate = Color(0xFF4B5665)
    val paper = Color(0xFFF3F6FA)
    val playfair = FontFamily(Font(R.font.playfair_bold, FontWeight.Bold))
}

/** Adaptive roles. Dark uses the brand palette. Light keeps the same hues with readable contrast. */
@Immutable
data class Palette(
    val dark: Boolean,
    val page: Color, val pageEnd: Color, val card: Color, val ink: Color, val muted: Color,
    val accentInk: Color, val alertInk: Color, val rule: Color,
) {
    companion object {
        val Dark = Palette(true, Brand.deep, Brand.night, Brand.navy, Brand.paper, Brand.paper.copy(alpha = 0.64f),
            Brand.turquoise, Brand.coral, Color(0xFF2A3A52))
        val Light = Palette(false, Brand.paper, Color(0xFFE9EFF6), Color.White, Brand.navy, Brand.slate,
            Color(0xFF08706F), Color(0xFFB93330), Color(0xFFCBD4DF))
    }
}

val LocalPalette = compositionLocalOf { Palette.Dark }

fun headline(size: Int, color: Color) = TextStyle(fontFamily = Brand.playfair, fontWeight = FontWeight.Bold, fontSize = size.sp, lineHeight = (size * 1.18).sp, color = color)
fun kicker(color: Color, size: Int = 11) = TextStyle(fontSize = size.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.8.sp, color = color)

/** Deep stage with the faint turquoise and coral washes from the brand bible. */
@Composable
fun Modifier.stage(p: Palette = LocalPalette.current): Modifier = this
    .background(Brush.verticalGradient(listOf(p.page, p.pageEnd)))
    .background(Brush.radialGradient(listOf(Brand.turquoise.copy(alpha = 0.12f), Color.Transparent), center = Offset(2000f, 0f), radius = 1100f))
    .background(Brush.radialGradient(listOf(Brand.coral.copy(alpha = 0.06f), Color.Transparent), center = Offset(0f, 2600f), radius = 1100f))

@Composable
fun Kicker(text: String, color: Color = LocalPalette.current.accentInk, modifier: Modifier = Modifier, size: Int = 11) =
    BasicText(text.uppercase(), modifier, style = kicker(color, size))

/** The mark only ever sits on navy, so it always gets a navy tile on light surfaces. */
@Composable
fun BrandMark(height: Dp = 36.dp, tile: Boolean = !LocalPalette.current.dark) {
    if (tile) {
        Box(Modifier.size(height).clip(RoundedCornerShape(height * 0.24f)).background(Brand.navy), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.mark), null, Modifier.padding(height * 0.16f).fillMaxSize(), contentScale = ContentScale.Fit)
        }
    } else Image(painterResource(R.drawable.mark), null, Modifier.size(height * 0.78f, height), contentScale = ContentScale.Fit)
}
