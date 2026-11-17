package com.ergin.deepreader.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class BadgeColorStyle(
    val container: Color,
    val content: Color
)

@Immutable
data class ExtendedReaderColors(
    val readerBackground: Color,
    val readerContent: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val idiomBadge: BadgeColorStyle,
    val slangBadge: BadgeColorStyle,
    val metaphorBadge: BadgeColorStyle,
    val ironyBadge: BadgeColorStyle,
    val culturalReferenceBadge: BadgeColorStyle,
    val allusionBadge: BadgeColorStyle,
    val archaicBadge: BadgeColorStyle,
    val neologismBadge: BadgeColorStyle,
    val literalPhraseBadge: BadgeColorStyle
) {
    fun getPhenomenonBadgeStyle(type: String): BadgeColorStyle = when (type.lowercase()) {
        "idiom" -> idiomBadge
        "slang" -> slangBadge
        "metaphor" -> metaphorBadge
        "irony" -> ironyBadge
        "cultural_reference" -> culturalReferenceBadge
        "allusion" -> allusionBadge
        "archaic" -> archaicBadge
        "neologism" -> neologismBadge
        else -> literalPhraseBadge
    }
}

val LightExtendedReaderColors = ExtendedReaderColors(
    readerBackground = Color(0xFFFFFFFF),
    readerContent = Color(0xFF191919),
    warningContainer = Color(0xFFFFF0C2),
    onWarningContainer = Color(0xFF7C5E00),
    idiomBadge = BadgeColorStyle(container = Color(0xFFEADDFF), content = Color(0xFF21005D)),
    slangBadge = BadgeColorStyle(container = Color(0xFFF9DEDC), content = Color(0xFF410E0B)),
    metaphorBadge = BadgeColorStyle(container = Color(0xFFC2E7FF), content = Color(0xFF001D35)),
    ironyBadge = BadgeColorStyle(container = Color(0xFFE8DEF8), content = Color(0xFF1D192B)),
    culturalReferenceBadge = BadgeColorStyle(container = Color(0xFFCCEBC5), content = Color(0xFF0A3800)),
    allusionBadge = BadgeColorStyle(container = Color(0xFFE0E2EC), content = Color(0xFF151D24)),
    archaicBadge = BadgeColorStyle(container = Color(0xFFFFD8E4), content = Color(0xFF31111D)),
    neologismBadge = BadgeColorStyle(container = Color(0xFFB2F5EA), content = Color(0xFF003731)),
    literalPhraseBadge = BadgeColorStyle(container = Color(0xFFE3E2E6), content = Color(0xFF1B1B1F))
)

val SepiaExtendedReaderColors = ExtendedReaderColors(
    readerBackground = Color(0xFFFAF5EB),
    readerContent = Color(0xFF231F1C),
    warningContainer = Color(0xFFEADAA4),
    onWarningContainer = Color(0xFF4A3800),
    idiomBadge = BadgeColorStyle(container = Color(0xFFE2D6EE), content = Color(0xFF3A1C5A)),
    slangBadge = BadgeColorStyle(container = Color(0xFFECCBCA), content = Color(0xFF4E1D1B)),
    metaphorBadge = BadgeColorStyle(container = Color(0xFFCBDCE6), content = Color(0xFF0D2D44)),
    ironyBadge = BadgeColorStyle(container = Color(0xFFDFD6E6), content = Color(0xFF2A2033)),
    culturalReferenceBadge = BadgeColorStyle(container = Color(0xFFCDE2C7), content = Color(0xFF1C3C14)),
    allusionBadge = BadgeColorStyle(container = Color(0xFFDDD9D5), content = Color(0xFF272421)),
    archaicBadge = BadgeColorStyle(container = Color(0xFFECCACD), content = Color(0xFF472124)),
    neologismBadge = BadgeColorStyle(container = Color(0xFFBFE0D7), content = Color(0xFF0F362C)),
    literalPhraseBadge = BadgeColorStyle(container = Color(0xFFE2DDD7), content = Color(0xFF2E2925))
)

val GreyExtendedReaderColors = ExtendedReaderColors(
    readerBackground = Color(0xFFEAE8E3),
    readerContent = Color(0xFF201E1D),
    warningContainer = Color(0xFFEADAA4),
    onWarningContainer = Color(0xFF4A3800),
    idiomBadge = BadgeColorStyle(container = Color(0xFFE2D6EE), content = Color(0xFF3A1C5A)),
    slangBadge = BadgeColorStyle(container = Color(0xFFECCBCA), content = Color(0xFF4E1D1B)),
    metaphorBadge = BadgeColorStyle(container = Color(0xFFCBDCE6), content = Color(0xFF0D2D44)),
    ironyBadge = BadgeColorStyle(container = Color(0xFFDFD6E6), content = Color(0xFF2A2033)),
    culturalReferenceBadge = BadgeColorStyle(container = Color(0xFFCDE2C7), content = Color(0xFF1C3C14)),
    allusionBadge = BadgeColorStyle(container = Color(0xFFDDD9D5), content = Color(0xFF272421)),
    archaicBadge = BadgeColorStyle(container = Color(0xFFECCACD), content = Color(0xFF472124)),
    neologismBadge = BadgeColorStyle(container = Color(0xFFBFE0D7), content = Color(0xFF0F362C)),
    literalPhraseBadge = BadgeColorStyle(container = Color(0xFFE2DDD7), content = Color(0xFF2E2925))
)

val DarkExtendedReaderColors = ExtendedReaderColors(
    readerBackground = Color(0xFF1F1E20),
    readerContent = Color(0xFFE5E5E7),
    warningContainer = Color(0xFF4A3800),
    onWarningContainer = Color(0xFFFFE088),
    idiomBadge = BadgeColorStyle(container = Color(0xFF4F378B), content = Color(0xFFEADDFF)),
    slangBadge = BadgeColorStyle(container = Color(0xFF8C1D18), content = Color(0xFFF9DEDC)),
    metaphorBadge = BadgeColorStyle(container = Color(0xFF004977), content = Color(0xFFC2E7FF)),
    ironyBadge = BadgeColorStyle(container = Color(0xFF4A4458), content = Color(0xFFE8DEF8)),
    culturalReferenceBadge = BadgeColorStyle(container = Color(0xFF225110), content = Color(0xFFCCEBC5)),
    allusionBadge = BadgeColorStyle(container = Color(0xFF3B4856), content = Color(0xFFE0E2EC)),
    archaicBadge = BadgeColorStyle(container = Color(0xFF633B48), content = Color(0xFFFFD8E4)),
    neologismBadge = BadgeColorStyle(container = Color(0xFF005047), content = Color(0xFFB2F5EA)),
    literalPhraseBadge = BadgeColorStyle(container = Color(0xFF47464F), content = Color(0xFFE3E2E6))
)

val LocalExtendedReaderColors: ProvidableCompositionLocal<ExtendedReaderColors> =
    staticCompositionLocalOf { LightExtendedReaderColors }
