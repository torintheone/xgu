package io.github.torintheone.petri.sim

/**
 * A number inside a [Mode]: its range and how to read and change it.
 * Point mutations pick from [ModeNumbers.ALL]; [types] limits a number to the cell types that use it.
 */
class ModeNumber(
    val key: String,
    val range: ClosedFloatingPointRange<Float>,
    val isAngle: Boolean = false,
    val isInteger: Boolean = false,
    val types: Set<CellType>? = null,
    val get: (Mode) -> Float,
    val set: (Mode, Float) -> Mode,
) {
    fun appliesTo(type: CellType): Boolean = types == null || type in types
}

object ModeNumbers {
    private val SENSORS = setOf(CellType.SENSEOCYTE, CellType.STEREOCYTE)

    val ALL: List<ModeNumber> = buildList {
        add(ModeNumber("splitMass", Mode.SPLIT_MASS, get = { it.splitMass }, set = { m, v -> m.copy(splitMass = v) }))
        add(ModeNumber("splitRatio", Mode.SPLIT_RATIO, get = { it.splitRatio }, set = { m, v -> m.copy(splitRatio = v) }))
        add(ModeNumber("nutrientPriority", Mode.UNIT, get = { it.nutrientPriority }, set = { m, v -> m.copy(nutrientPriority = v) }))
        add(ModeNumber("splitAngle", Mode.ANGLE, isAngle = true, get = { it.splitAngle }, set = { m, v -> m.copy(splitAngle = v) }))
        add(ModeNumber("child1Angle", Mode.ANGLE, isAngle = true, get = { it.child1Angle }, set = { m, v -> m.copy(child1Angle = v) }))
        add(ModeNumber("child2Angle", Mode.ANGLE, isAngle = true, get = { it.child2Angle }, set = { m, v -> m.copy(child2Angle = v) }))
        add(ModeNumber("red", Mode.UNIT, get = { it.red }, set = { m, v -> m.copy(red = v) }))
        add(ModeNumber("green", Mode.UNIT, get = { it.green }, set = { m, v -> m.copy(green = v) }))
        add(ModeNumber("blue", Mode.UNIT, get = { it.blue }, set = { m, v -> m.copy(blue = v) }))
        add(ModeNumber("adhesinStiffness", Mode.UNIT, get = { it.adhesinStiffness }, set = { m, v -> m.copy(adhesinStiffness = v) }))
        add(ModeNumber("adhesinLength", Mode.UNIT, get = { it.adhesinLength }, set = { m, v -> m.copy(adhesinLength = v) }))
        add(ModeNumber("cytoskeleton", Mode.UNIT, get = { it.cytoskeleton }, set = { m, v -> m.copy(cytoskeleton = v) }))
        add(
            ModeNumber(
                "maxBonds", 0f..Mode.MAX_BONDS.toFloat(), isInteger = true,
                get = { it.maxBonds.toFloat() }, set = { m, v -> m.copy(maxBonds = v.toInt()) },
            ),
        )

        addAll(programmable("swimForce", setOf(CellType.FLAGELLOCYTE), { it.swimForce }, { m, v -> m.copy(swimForce = v) }))
        addAll(programmable("density", setOf(CellType.BUOYOCYTE), { it.density }, { m, v -> m.copy(density = v) }))
        addAll(programmable("contraction", setOf(CellType.MYOCYTE), { it.contraction }, { m, v -> m.copy(contraction = v) }))
        addAll(programmable("bend", setOf(CellType.MYOCYTE), { it.bend }, { m, v -> m.copy(bend = v) }))
        addAll(programmable("lift", setOf(CellType.MYOCYTE), { it.lift }, { m, v -> m.copy(lift = v) }))
        val neuro = setOf(CellType.NEUROCYTE)
        addAll(programmable("neuronOutput1", neuro, { it.neuronOutput1 }, { m, v -> m.copy(neuronOutput1 = v) }))
        addAll(programmable("neuronOutput2", neuro, { it.neuronOutput2 }, { m, v -> m.copy(neuronOutput2 = v) }))
        addAll(programmable("neuronOutput3", neuro, { it.neuronOutput3 }, { m, v -> m.copy(neuronOutput3 = v) }))
        addAll(programmable("neuronOutput4", neuro, { it.neuronOutput4 }, { m, v -> m.copy(neuronOutput4 = v) }))
        add(
            ModeNumber(
                "oscillatorFrequency", Mode.UNIT, types = neuro,
                get = { it.oscillatorFrequency }, set = { m, v -> m.copy(oscillatorFrequency = v) },
            ),
        )
        val stem = setOf(CellType.STEMOCYTE)
        addAll(programmable("path1Signal", stem, { it.path1Signal }, { m, v -> m.copy(path1Signal = v) }))
        addAll(programmable("path2Signal", stem, { it.path2Signal }, { m, v -> m.copy(path2Signal = v) }))
        add(ModeNumber("senseGain", Mode.UNIT, types = SENSORS, get = { it.senseGain }, set = { m, v -> m.copy(senseGain = v) }))
        add(ModeNumber("senseRed", Mode.UNIT, types = SENSORS, get = { it.senseRed }, set = { m, v -> m.copy(senseRed = v) }))
        add(ModeNumber("senseGreen", Mode.UNIT, types = SENSORS, get = { it.senseGreen }, set = { m, v -> m.copy(senseGreen = v) }))
        add(ModeNumber("senseBlue", Mode.UNIT, types = SENSORS, get = { it.senseBlue }, set = { m, v -> m.copy(senseBlue = v) }))
        add(
            ModeNumber(
                "senseColorThreshold", Mode.UNIT, types = SENSORS,
                get = { it.senseColorThreshold }, set = { m, v -> m.copy(senseColorThreshold = v) },
            ),
        )
        add(
            ModeNumber(
                "secretionIntensity", Mode.UNIT, types = setOf(CellType.SECROCYTE),
                get = { it.secretionIntensity }, set = { m, v -> m.copy(secretionIntensity = v) },
            ),
        )
        val cilio = setOf(CellType.CILIOCYTE)
        addAll(programmable("leftCilia", cilio, { it.leftCilia }, { m, v -> m.copy(leftCilia = v) }))
        addAll(programmable("rightCilia", cilio, { it.rightCilia }, { m, v -> m.copy(rightCilia = v) }))
    }

    /** The numbers that matter for a cell of [type]. */
    fun forType(type: CellType): List<ModeNumber> = ALL.filter { it.appliesTo(type) }

    private fun programmable(
        key: String,
        types: Set<CellType>,
        get: (Mode) -> ProgrammableValue,
        set: (Mode, ProgrammableValue) -> Mode,
    ): List<ModeNumber> = listOf(
        ModeNumber("$key.a", ProgrammableValue.LINEAR_AB, types = types, get = { get(it).a }, set = { m, v -> set(m, get(m).copy(a = v)) }),
        ModeNumber("$key.b", ProgrammableValue.LINEAR_AB, types = types, get = { get(it).b }, set = { m, v -> set(m, get(m).copy(b = v)) }),
        ModeNumber("$key.c", ProgrammableValue.STEP_C, types = types, get = { get(it).c }, set = { m, v -> set(m, get(m).copy(c = v)) }),
    )
}
