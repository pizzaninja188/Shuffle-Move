package shuffle.android

import shuffle.fwk.data.Species

/**
 * Recognition labels are Shuffle Move species names, plus the mega form's name (from species data,
 * e.g. "Mega_Charizard_xs" for Charizard_sx) for the team's mega slot: it looks different on the
 * board but is the same species to the simulator, with mega progress full.
 */
object Labels {
    /** Blocks and empty cells the recognizer always looks for. */
    val SPECIALS: List<String> = listOf(Species.AIR.name, Species.WOOD.name, Species.METAL.name, Species.COIN.name)

    /** Species that can be frozen (blocks and empty cells cannot). */
    fun canFreeze(label: String) = label !in SPECIALS

    /** All labels to look for: the team, its mega form, stage disruptions, then blocks. */
    fun forTeam(names: List<String>, megaLabel: String?, disruptions: List<String> = emptyList()): List<String> {
        val out = LinkedHashSet<String>(names)
        if (megaLabel != null) out += megaLabel
        out += disruptions
        out += SPECIALS
        return out.toList()
    }
}
