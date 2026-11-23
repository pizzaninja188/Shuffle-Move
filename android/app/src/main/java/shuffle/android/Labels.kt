package shuffle.android

import shuffle.fwk.data.Species

/**
 * Recognition labels are Shuffle Move species names, plus "Mega_<name>" for the mega-evolved form
 * of the team's mega slot (it looks different on the board but is the same species to the
 * simulator, with mega progress full).
 */
object Labels {
    const val MEGA_PREFIX = "Mega_"

    /** Blocks and empty cells the recognizer always looks for. */
    val SPECIALS: List<String> = listOf(Species.AIR.name, Species.WOOD.name, Species.METAL.name, Species.COIN.name)

    fun isMega(label: String) = label.startsWith(MEGA_PREFIX)

    fun megaOf(name: String) = MEGA_PREFIX + name

    /** The species name a label stands for on the simulator's board. */
    fun speciesName(label: String) = if (isMega(label)) label.removePrefix(MEGA_PREFIX) else label

    /** Species that can be frozen (blocks and empty cells cannot). */
    fun canFreeze(label: String) = speciesName(label) !in SPECIALS

    /** All labels to look for, given the team's names and mega slot. */
    fun forTeam(names: List<String>, megaSlot: String?): List<String> {
        val out = LinkedHashSet<String>(names)
        if (megaSlot != null) out += megaOf(megaSlot)
        out += SPECIALS
        return out.toList()
    }
}
