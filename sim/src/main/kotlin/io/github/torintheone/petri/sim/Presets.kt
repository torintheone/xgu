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

    /**
     * SPEC «Сигналы»: a row «mouth – flagellocyte – stereocyte – flagellocyte – mouth», all facing forward. The stereocyte
     * smells food into S1; the right flagellocyte takes +0.76·S1, the left one −0.76·S1, so the swimmer turns to food.
     * The mouths are phagocytes that feed the row and bud off seeds. Every division is sideways (split angle 0), so each
     * bond lies clearly on one child's side.
     */
    SMART_SWIMMER,
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
            SMART_SWIMMER -> smartSwimmer(modes)
        }
        return Genome(name = name, initialMode = 0, modes = modes)
    }

    private fun smartSwimmer(modes: MutableList<Mode>) {
        val seed = 0
        val rightGroup = 1
        val leftGroup = 2
        val rightMouth = 3
        val rightTail = 4
        val sensor = 5
        val leftGroup2 = 6
        val leftTail = 7
        val leftMouth = 8
        fun sideways(index: Int, right: Int, left: Int, splitMass: Float) = modes[index].copy(
            makeAdhesin = true,
            splitAngle = 0f,
            splitMass = splitMass,
            child1Mode = right,
            child2Mode = left,
            adhesinStiffness = 1f,
        )
        // Ratios that leave all five cells equally heavy: 2/5 of the seed makes two cells, 3/5 makes three.
        modes[seed] = sideways(seed, rightGroup, leftGroup, splitMass = 3.4f).copy(splitRatio = 0.4f, red = 0.9f, green = 0.9f, blue = 0.5f)
        modes[rightGroup] = sideways(rightGroup, rightMouth, rightTail, splitMass = Mode.SPLIT_BY_AGE)
        modes[leftGroup] = sideways(leftGroup, sensor, leftGroup2, splitMass = Mode.SPLIT_BY_AGE).copy(splitRatio = 1f / 3f)
        modes[leftGroup2] = sideways(leftGroup2, leftTail, leftMouth, splitMass = Mode.SPLIT_BY_AGE)
        // The mouths bud a free seed off their outer side; the bond on their inner side stays with them.
        modes[rightMouth] = modes[rightMouth].copy(
            splitMass = 2.6f,
            splitAngle = 0f,
            child1Mode = seed,
            child2Mode = rightMouth,
            child1KeepBonds = false,
            red = 0.55f,
            green = 0.85f,
            blue = 0.4f,
        )
        modes[leftMouth] = modes[leftMouth].copy(
            splitMass = 2.6f,
            splitAngle = 0f,
            child1Mode = leftMouth,
            child2Mode = seed,
            child2KeepBonds = false,
            red = 0.55f,
            green = 0.85f,
            blue = 0.4f,
        )
        fun tail(index: Int, steering: Float) = modes[index].copy(
            cellType = CellType.FLAGELLOCYTE,
            splitMass = Mode.SPLIT_NEVER,
            swimForce = ProgrammableValue(SignalInput.S1, a = steering, b = 0.5f),
            red = 0.95f,
            green = 0.55f,
            blue = 0.35f,
        )
        modes[rightTail] = tail(rightTail, 0.76f)
        modes[leftTail] = tail(leftTail, -0.76f)
        modes[sensor] = modes[sensor].copy(
            cellType = CellType.STEREOCYTE,
            splitMass = Mode.SPLIT_NEVER,
            senseTarget = SenseTarget.FOOD,
            senseChannel = SignalChannel.S1,
            senseGain = 1f,
            red = 0.45f,
            green = 0.6f,
            blue = 1f,
        )
    }
}
