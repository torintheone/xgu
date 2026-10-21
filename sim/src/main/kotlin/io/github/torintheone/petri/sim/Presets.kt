package io.github.torintheone.petri.sim

/** Example genomes the genome bank starts with. The app names them (the names are interface text). */
enum class Preset {
    /** Free phagocytes: the default genome. */
    SINGLE_PHAGOCYTES,

    /** Phagocytes in a straight chain: the split plane crosses the front at 90°, the children stay glued. */
    CHAIN,

    /** Only the tip of the chain divides, and each new tip turns by 15°: the chain curls into an arc. */
    ARC,

    /** Photocytes glued into a round colony that grows towards the light. */
    PHOTOCYTE_COLONY,
    ;

    fun genome(name: String): Genome {
        val modes = MutableList(Mode.MODE_COUNT) { Mode.default(it) }
        when (this) {
            SINGLE_PHAGOCYTES -> Unit
            CHAIN -> modes[0] = modes[0].copy(makeAdhesin = true, splitAngle = 90f, adhesinStiffness = 1f)
            ARC -> {
                modes[0] = modes[0].copy(
                    makeAdhesin = true,
                    splitAngle = 90f,
                    child1Angle = 15f,
                    child2Mode = 1,
                    adhesinStiffness = 1f,
                )
                modes[1] = modes[1].copy(splitMass = Mode.SPLIT_NEVER)
            }
            PHOTOCYTE_COLONY -> modes[0] = modes[0].copy(
                cellType = CellType.PHOTOCYTE,
                makeAdhesin = true,
                red = 0.35f,
                green = 0.85f,
                blue = 0.3f,
            )
        }
        return Genome(name = name, initialMode = 0, modes = modes)
    }
}
