package tv.own.owntv.ui.components

import tv.own.owntv.core.ui.stage.StageGlyph

/**
 * The Stage line icons, as SVG path data on the same 24×24 grid [OwnTVIcon] draws on. The
 * paths live in core ([StageGlyph], shared with the phone); its names are the [OwnTVIcon] entries. The
 * phone-only marks (PLUS, MOON) have no entry here, so the TV keeps drawing ADD and BEDTIME its own way.
 */
internal val StageGlyphPaths: Map<OwnTVIcon, String> = StageGlyph.entries.mapNotNull { glyph ->
    OwnTVIcon.entries.firstOrNull { it.name == glyph.name }?.let { it to glyph.path }
}.toMap() + mapOf(
    OwnTVIcon.NZ_FERN to "M5 21 C7 16 9 9 18 3 M8 14 L4 11 M9 11 L6 7 M11 8 L10 4 M8 16 L13 16 M10 12 L16 12 M13 8 L19 8 M16 5 L20 5",
    OwnTVIcon.AU_ROO to "M3 19 Q9 18 11 11 L15 8 L15 3 L17 7 L20 7 L21 10 L17 11 L15 16 L19 21 L14 21 L11 17 L8 21 M11 11 L8 13 L5 12",
    OwnTVIcon.RELAX_LEAF to "M5 20 Q6 11 19 4 Q23 19 8 18 M6 20 L17 8",
)
