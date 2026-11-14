package com.ergin.deepreader.core.designsystem.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ExtendedReaderColorsTest {

    @Test
    fun getPhenomenonBadgeStyle_validTypes_returnsMatchingBadgeColors() {
        val theme = LightExtendedReaderColors
        val types = listOf(
            "idiom", "slang", "metaphor", "irony",
            "cultural_reference", "allusion", "archaic", "neologism", "literal_phrase"
        )

        for (type in types) {
            val style = theme.getPhenomenonBadgeStyle(type)
            assertNotNull("Style for $type should not be null", style)
        }

        assertEquals(theme.idiomBadge, theme.getPhenomenonBadgeStyle("idiom"))
        assertEquals(theme.slangBadge, theme.getPhenomenonBadgeStyle("SLANG"))
        assertEquals(theme.metaphorBadge, theme.getPhenomenonBadgeStyle("metaphor"))
    }

    @Test
    fun getPhenomenonBadgeStyle_unknownType_fallsBackToLiteralPhrase() {
        val theme = LightExtendedReaderColors
        val style = theme.getPhenomenonBadgeStyle("unknown_type")

        assertEquals(theme.literalPhraseBadge, style)
    }
}