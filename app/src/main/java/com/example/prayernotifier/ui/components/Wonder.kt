package com.example.prayernotifier.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.prayernotifier.R
import com.example.prayernotifier.ui.theme.PrayerPalette
import com.example.prayernotifier.ui.theme.WonderAccent1
import com.example.prayernotifier.ui.theme.WonderAccent2
import com.example.prayernotifier.ui.theme.WonderBlack
import com.example.prayernotifier.ui.theme.WonderCorners
import com.example.prayernotifier.ui.theme.WonderGreyMedium
import com.example.prayernotifier.ui.theme.WonderGreyStrong
import com.example.prayernotifier.ui.theme.WonderOffWhite
import com.example.prayernotifier.ui.theme.WonderSpacing
import com.example.prayernotifier.ui.theme.WonderWhite
import com.example.prayernotifier.ui.theme.paletteFor

//region Arch — the signature shape: a rectangle with a fully rounded top.

val ArchShape = GenericShape { size, _ ->
    val r = size.width / 2f
    moveTo(0f, size.height)
    lineTo(0f, r)
    arcTo(Rect(0f, 0f, size.width, size.width), 180f, 180f, false)
    lineTo(size.width, size.height)
    close()
}

/**
 * Flat layered illustration of the prayer's sky: orb (sun or crescent),
 * cloud pills, dunes and a mosque silhouette, in the prayer's two-tone
 * palette. [fadeTo] dissolves the bottom into the page, like Wonderous'
 * hero art sinking under the title.
 */
@Composable
fun PrayerIllustration(
    prayer: String?,
    modifier: Modifier = Modifier,
    fadeTo: Color? = WonderBlack
) {
    val palette = paletteFor(prayer)
    Canvas(modifier) {
        drawPrayerScene(palette)
        if (fadeTo != null) {
            drawRect(
                brush = Brush.verticalGradient(
                    0.55f to Color.Transparent,
                    1f to fadeTo,
                    startY = 0f,
                    endY = size.height
                )
            )
        }
    }
}

private fun DrawScope.drawPrayerScene(p: PrayerPalette) {
    val w = size.width
    val h = size.height
    drawRect(p.sky)

    // Cloud pills drifting behind the orb.
    val cloud = Color.White.copy(alpha = 0.16f)
    fun pill(x: Float, y: Float, len: Float) = drawRoundRect(
        color = cloud,
        topLeft = Offset(x, y),
        size = Size(len, w * 0.05f),
        cornerRadius = CornerRadius(w * 0.025f)
    )
    pill(w * 0.10f, h * 0.20f, w * 0.38f)
    pill(w * 0.22f, h * 0.27f, w * 0.20f)
    pill(w * 0.52f, h * 0.38f, w * 0.34f)

    // Orb: full sun by day, crescent at night.
    val orbCenter = Offset(w * 0.68f, h * 0.20f)
    val r = w * 0.15f
    drawCircle(p.orb, r, orbCenter)
    if (p.night) {
        drawCircle(p.landDeep, r * 0.86f, orbCenter + Offset(r * 0.42f, -r * 0.18f))
    } else {
        drawCircle(p.orb.copy(alpha = 0.22f), r * 1.45f, orbCenter)
    }

    // Back dune.
    drawPath(
        Path().apply {
            moveTo(0f, h * 0.64f)
            quadraticTo(w * 0.45f, h * 0.50f, w, h * 0.60f)
            lineTo(w, h); lineTo(0f, h); close()
        },
        p.land
    )

    // Mosque: dome on a base, one minaret with a cap.
    val silhouette = p.landDeep
    val domeC = Offset(w * 0.36f, h * 0.66f)
    val domeR = w * 0.14f
    drawCircle(silhouette, domeR, domeC)
    drawRect(silhouette, Offset(domeC.x - domeR * 1.35f, domeC.y), Size(domeR * 2.7f, h))
    drawRect(silhouette, Offset(domeC.x - w * 0.012f, domeC.y - domeR * 1.45f), Size(w * 0.024f, domeR * 0.5f))
    val minX = w * 0.66f
    val minW = w * 0.06f
    drawRect(silhouette, Offset(minX, h * 0.46f), Size(minW, h))
    drawPath(
        Path().apply {
            moveTo(minX - minW * 0.25f, h * 0.46f)
            lineTo(minX + minW / 2f, h * 0.40f)
            lineTo(minX + minW * 1.25f, h * 0.46f)
            close()
        },
        silhouette
    )

    // Front dune closes the scene.
    drawPath(
        Path().apply {
            moveTo(0f, h * 0.84f)
            quadraticTo(w * 0.60f, h * 0.74f, w, h * 0.82f)
            lineTo(w, h); lineTo(0f, h); close()
        },
        lerp(p.landDeep, Color.Black, 0.25f)
    )
}

//endregion

//region Ceremony — eyebrow label, compass ornament.

/** Uppercase Tenor label between two thin rules ("THE ANCIENT WONDER"). */
@Composable
fun EyebrowLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = WonderOffWhite
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(Modifier.weight(1f), thickness = 1.dp, color = color.copy(alpha = 0.35f))
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.titleLarge,
            color = color,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = WonderSpacing.x16)
        )
        HorizontalDivider(Modifier.weight(1f), thickness = 1.dp, color = color.copy(alpha = 0.35f))
    }
}

/** Thin rules with a compass-star glyph centered. */
@Composable
fun OrnamentDivider(
    modifier: Modifier = Modifier,
    color: Color = WonderOffWhite
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(Modifier.weight(1f), thickness = 1.dp, color = color.copy(alpha = 0.35f))
        CompassStar(Modifier.padding(horizontal = WonderSpacing.x16).size(32.dp), color)
        HorizontalDivider(Modifier.weight(1f), thickness = 1.dp, color = color.copy(alpha = 0.35f))
    }
}

@Composable
fun CompassStar(modifier: Modifier = Modifier, color: Color = WonderOffWhite) {
    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2f
        fun star(outer: Float, inner: Float, rotation: Double) = Path().apply {
            for (i in 0 until 8) {
                val a = rotation + i * Math.PI / 4
                val rad = if (i % 2 == 0) outer else inner
                val pt = Offset(c.x + (rad * kotlin.math.sin(a)).toFloat(), c.y - (rad * kotlin.math.cos(a)).toFloat())
                if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
            }
            close()
        }
        drawCircle(color, r * 0.42f, c, style = Stroke(width = 1.dp.toPx()))
        drawPath(star(r * 0.62f, r * 0.12f, Math.PI / 4), color.copy(alpha = 0.7f))
        drawPath(star(r, r * 0.14f, 0.0), color)
    }
}

//endregion

//region Floating controls — dark circles and pills over content, no bar.

/** 48dp dark circle with a light line icon (back, close, menu). */
@Composable
fun CircleButton(
    @DrawableRes icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = WonderGreyStrong.copy(alpha = 0.92f)
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        color = containerColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(icon),
                contentDescription = contentDescription,
                modifier = Modifier.size(24.dp),
                tint = WonderOffWhite
            )
        }
    }
}

/** Page header: back circle at left, centered Tenor title. */
@Composable
fun WonderPageHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = WonderSpacing.x16, vertical = WonderSpacing.x12),
        contentAlignment = Alignment.Center
    ) {
        CircleButton(
            icon = R.drawable.ph_arrow_left_light,
            contentDescription = stringResource(R.string.back),
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = WonderOffWhite
        )
    }
}

//endregion

//region Cards and metadata rows — tone steps, no borders or shadows.

/** #272625 card on the warm-black page; separated by tone alone. */
@Composable
fun WonderCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(WonderCorners.card),
        color = WonderGreyStrong
    ) {
        Column(content = content)
    }
}

/**
 * Metadata row: uppercase tracked #BEABA1 label over an off-white value
 * ("DATE / ca. 2420 B.C."), with a chevron when tappable.
 */
@Composable
fun MetaRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null
) {
    val clickable = if (onClick != null) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(clickable)
            .padding(horizontal = WonderSpacing.x24, vertical = WonderSpacing.x16),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.titleSmall,
                color = WonderAccent2
            )
            Spacer(Modifier.height(WonderSpacing.x4))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = WonderOffWhite
            )
        }
        if (trailingContent != null) {
            Spacer(Modifier.width(WonderSpacing.x16))
            trailingContent()
        } else if (onClick != null) {
            Icon(
                painter = painterResource(R.drawable.ph_caret_right_light),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = WonderGreyMedium
            )
        }
    }
}

/** Hairline between rows inside a card: a step down to the page tone. */
@Composable
fun WonderDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = WonderSpacing.x24),
        thickness = 1.dp,
        color = WonderBlack
    )
}

//endregion

//region Buttons — full-width dark 8dp buttons with uppercase Raleway.

/** Primary button: dark fill, uppercase tracked label. */
@Composable
fun WonderPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = WonderGreyStrong
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(WonderCorners.card),
        color = containerColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) WonderOffWhite else WonderGreyMedium
            )
        }
    }
}

/** Quiet text action in muted accent, same height as the primary. */
@Composable
fun WonderTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(WonderCorners.card))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = WonderAccent2
        )
    }
}

/**
 * Choice chip: 8dp rectangle, orange when selected (the accent marks the
 * active state), warm black otherwise so it reads on #272625 cards.
 */
@Composable
fun WonderChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 40.dp)
            .semantics { this.selected = selected },
        shape = RoundedCornerShape(WonderCorners.card),
        color = if (selected) WonderAccent1 else WonderBlack
    ) {
        Box(
            modifier = Modifier.padding(horizontal = WonderSpacing.x16, vertical = WonderSpacing.x8),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) WonderWhite else WonderOffWhite,
                maxLines = 1
            )
        }
    }
}

//endregion

//region Empty states — small arch, Tenor title, ornament, quiet body.

@Composable
fun WonderEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    prayer: String? = "Isha",
    archWidth: Dp = 140.dp
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = WonderSpacing.x24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        PrayerIllustration(
            prayer = prayer,
            modifier = Modifier
                .size(width = archWidth, height = archWidth * 1.3f)
                .clip(ArchShape)
        )
        Spacer(Modifier.height(WonderSpacing.x24))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = WonderOffWhite,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(WonderSpacing.x8))
        OrnamentDivider(Modifier.padding(horizontal = WonderSpacing.x32))
        Spacer(Modifier.height(WonderSpacing.x8))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = WonderAccent2,
            textAlign = TextAlign.Center
        )
    }
}

//endregion

/** Background helper so callers don't import the palette just for the page. */
fun Modifier.wonderPage() = this.background(WonderBlack)
