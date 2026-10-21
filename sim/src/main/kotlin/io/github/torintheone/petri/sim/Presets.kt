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

    /**
     * A phagocyte pushed by a flagellocyte (SPEC «Этап 4»). The seed divides into a head in front and a tail behind
     * that faces the head, so the flagellum at its back is free. The head buds off a free seed to its front right.
     */
    SWIMMER,
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
            SWIMMER -> {
                modes[0] = modes[0].copy(
                    makeAdhesin = true,
                    splitAngle = 90f,
                    child1Mode = 1,
                    child2Mode = 2,
                    adhesinStiffness = 1f,
                    red = 0.95f,
                    green = 0.85f,
                    blue = 0.45f,
                )
                modes[1] = modes[1].copy(
                    splitMass = 3f,
                    splitRatio = 0.55f,
                    splitAngle = 225f,
                    child1Mode = 1,
                    child2Mode = 0,
                    child2KeepBonds = false,
                    red = 0.4f,
                    green = 0.85f,
                    blue = 0.95f,
                )
                modes[2] = modes[2].copy(
                    cellType = CellType.FLAGELLOCYTE,
                    splitMass = Mode.SPLIT_NEVER,
                    swimForce = ProgrammableValue.constant(0.6f),
                    red = 0.95f,
                    green = 0.5f,
                    blue = 0.35f,
                )
            }
        }
        return Genome(name = name, initialMode = 0, modes = modes)
    }
}
