package io.github.torintheone.petri.sim

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.floor

/** One of the four signal substances (SPEC «Сигналы»). */
@Serializable
enum class SignalChannel { S1, S2, S3, S4 }

/** What a programmable value reacts to. NONE turns it into the constant b. */
@Serializable
enum class SignalInput { NONE, S1, S2, S3, S4, MASS, AGE, NITRATES, BONDS }

/** Form of a programmable value (SPEC «Программируемые значения»). */
@Serializable
enum class ValueShape { LINEAR, STEP }

/** What senseocytes and stereocytes smell. */
@Serializable
enum class SenseTarget { CELLS, FOOD, COATED_FOOD, LIGHT, SPEED, WALL }

/** What a secrocyte releases into the medium. */
@Serializable
enum class Substance { FOOD_SMELL, CYANIDE, SIGNAL, LIPASE }

/**
 * A parameter that is either a constant or a function of one input (SPEC «Программируемые значения»):
 * linear a·input + b, or a step: a below c, b from c on.
 */
@Serializable
data class ProgrammableValue(
    val input: SignalInput = SignalInput.NONE,
    val shape: ValueShape = ValueShape.LINEAR,
    val a: Float = 0f,
    val b: Float = 0f,
    val c: Float = 0f,
) {
    fun evaluate(inputValue: Float): Float = when {
        input == SignalInput.NONE -> b
        shape == ValueShape.LINEAR -> a * inputValue + b
        inputValue < c -> a
        else -> b
    }

    fun sanitized(): ProgrammableValue {
        val abRange = if (shape == ValueShape.STEP && input != SignalInput.NONE) STEP_AB else LINEAR_AB
        return copy(a = a.clampTo(abRange), b = b.clampTo(abRange), c = c.clampTo(STEP_C))
    }

    companion object {
        val LINEAR_AB = -4f..4f
        val STEP_AB = -0.5f..0.5f
        val STEP_C = -1f..1f

        fun constant(value: Float) = ProgrammableValue(b = value)
    }
}

/**
 * One of the 40 modes of a genome (SPEC «Геном и деление»): the cell type, how the cell divides
 * and what its children become, plus the settings of its type. Angles are in degrees, counter-clockwise.
 */
@Serializable
data class Mode(
    val cellType: CellType = CellType.PHAGOCYTE,
    val makeAdhesin: Boolean = false,
    val child1Mode: Int = 0,
    val child2Mode: Int = 0,
    val child1KeepBonds: Boolean = true,
    val child2KeepBonds: Boolean = true,
    val child1Mirror: Boolean = false,
    val child2Mirror: Boolean = false,
    /** Mass at which the cell divides; [SPLIT_BY_AGE] and [SPLIT_NEVER] are the two ends of the slider. */
    val splitMass: Float = 2.5f,
    /** Share of the mass that goes to child 1. */
    val splitRatio: Float = 0.5f,
    val nutrientPriority: Float = 0.5f,
    val prioritize: Boolean = true,
    val splitAngle: Float = 0f,
    val child1Angle: Float = 0f,
    val child2Angle: Float = 0f,
    val red: Float = 0.5f,
    val green: Float = 0.8f,
    val blue: Float = 0.6f,
    val adhesinStiffness: Float = 0.5f,
    val adhesinLength: Float = 0.5f,
    val cytoskeleton: Float = 0.5f,
    val maxBonds: Int = MAX_BONDS,

    // Flagellocyte, buoyocyte.
    val swimForce: ProgrammableValue = ProgrammableValue.constant(0.5f),
    val density: ProgrammableValue = ProgrammableValue.constant(0.5f),
    // Myocyte.
    val contraction: ProgrammableValue = ProgrammableValue.constant(0f),
    val bend: ProgrammableValue = ProgrammableValue.constant(0f),
    val lift: ProgrammableValue = ProgrammableValue.constant(0f),
    // Neurocyte: one output per channel, plus an optional oscillator.
    val neuronOutput1: ProgrammableValue = ProgrammableValue.constant(0f),
    val neuronOutput2: ProgrammableValue = ProgrammableValue.constant(0f),
    val neuronOutput3: ProgrammableValue = ProgrammableValue.constant(0f),
    val neuronOutput4: ProgrammableValue = ProgrammableValue.constant(0f),
    /** Oscillations per game hour; 0 switches the oscillator off. */
    val oscillatorFrequency: Float = 0f,
    val oscillatorChannelA: SignalChannel = SignalChannel.S1,
    val oscillatorChannelB: SignalChannel = SignalChannel.S2,
    // Stemocyte.
    val path1Mode: Int = 0,
    val path1Signal: ProgrammableValue = ProgrammableValue.constant(0f),
    val path2Mode: Int = 0,
    val path2Signal: ProgrammableValue = ProgrammableValue.constant(0f),
    // Senseocyte, stereocyte.
    val senseTarget: SenseTarget = SenseTarget.FOOD,
    val senseChannel: SignalChannel = SignalChannel.S1,
    val senseGain: Float = 0.5f,
    val senseRed: Float = 0.5f,
    val senseGreen: Float = 0.5f,
    val senseBlue: Float = 0.5f,
    val senseColorThreshold: Float = 0.3f,
    // Secrocyte.
    val secretion: Substance = Substance.FOOD_SMELL,
    val secretionIntensity: Float = 0.5f,
    val secretionChannel: SignalChannel = SignalChannel.S1,
    // Virocyte, gamete.
    val virusMode: Int = 0,
    val fusionMode: Int = 0,
    // Ciliocyte.
    val leftCilia: ProgrammableValue = ProgrammableValue.constant(0.5f),
    val rightCilia: ProgrammableValue = ProgrammableValue.constant(0.5f),
) {
    val splitsByAge: Boolean get() = splitMass <= SPLIT_BY_AGE
    val neverSplits: Boolean get() = splitMass >= SPLIT_NEVER

    /** Every value forced into its allowed range; mode indices outside the genome become this mode. */
    fun sanitized(self: Int): Mode {
        fun index(i: Int) = if (i in 0 until MODE_COUNT) i else self
        return copy(
            child1Mode = index(child1Mode),
            child2Mode = index(child2Mode),
            splitMass = splitMass.clampTo(SPLIT_MASS),
            splitRatio = splitRatio.clampTo(SPLIT_RATIO),
            nutrientPriority = nutrientPriority.clampTo(UNIT),
            splitAngle = wrapDegrees(splitAngle),
            child1Angle = wrapDegrees(child1Angle),
            child2Angle = wrapDegrees(child2Angle),
            red = red.clampTo(UNIT),
            green = green.clampTo(UNIT),
            blue = blue.clampTo(UNIT),
            adhesinStiffness = adhesinStiffness.clampTo(UNIT),
            adhesinLength = adhesinLength.clampTo(UNIT),
            cytoskeleton = cytoskeleton.clampTo(UNIT),
            maxBonds = maxBonds.coerceIn(0, MAX_BONDS),
            swimForce = swimForce.sanitized(),
            density = density.sanitized(),
            contraction = contraction.sanitized(),
            bend = bend.sanitized(),
            lift = lift.sanitized(),
            neuronOutput1 = neuronOutput1.sanitized(),
            neuronOutput2 = neuronOutput2.sanitized(),
            neuronOutput3 = neuronOutput3.sanitized(),
            neuronOutput4 = neuronOutput4.sanitized(),
            oscillatorFrequency = oscillatorFrequency.clampTo(UNIT),
            path1Mode = index(path1Mode),
            path1Signal = path1Signal.sanitized(),
            path2Mode = index(path2Mode),
            path2Signal = path2Signal.sanitized(),
            senseGain = senseGain.clampTo(UNIT),
            senseRed = senseRed.clampTo(UNIT),
            senseGreen = senseGreen.clampTo(UNIT),
            senseBlue = senseBlue.clampTo(UNIT),
            senseColorThreshold = senseColorThreshold.clampTo(UNIT),
            secretionIntensity = secretionIntensity.clampTo(UNIT),
            virusMode = index(virusMode),
            fusionMode = index(fusionMode),
            leftCilia = leftCilia.sanitized(),
            rightCilia = rightCilia.sanitized(),
        )
    }

    companion object {
        const val MODE_COUNT = 40
        const val MAX_BONDS = 20

        /** Left end of the split mass slider: divide by age, as soon as the cell is 0.5 h old. */
        const val SPLIT_BY_AGE = 0f

        /** Right end of the split mass slider: never divide. */
        const val SPLIT_NEVER = 3.6f

        val SPLIT_MASS = SPLIT_BY_AGE..SPLIT_NEVER
        val SPLIT_RATIO = 0.1f..0.9f
        val UNIT = 0f..1f
        val ANGLE = 0f..360f

        /** A fresh mode: divides into two copies of itself, coloured by its number. */
        fun default(index: Int): Mode {
            val (r, g, b) = modeColor(index)
            return Mode(
                child1Mode = index,
                child2Mode = index,
                red = r,
                green = g,
                blue = b,
                path1Mode = index,
                path2Mode = index,
                virusMode = index,
                fusionMode = index,
            )
        }

        /** Distinct, soft colours for mode numbers: hues spread by the golden angle. */
        fun modeColor(index: Int): Triple<Float, Float, Float> {
            val hue = (0.33f + index * 0.381966f) % 1f
            return hsvToRgb(hue, 0.55f, 0.9f)
        }
    }
}

/**
 * A complete genome: 40 modes and the initial one, from which an organism grows and which the synthesizer plants.
 * Genomes never change; a mutation makes a new one.
 */
@Serializable
data class Genome(
    val name: String = "",
    val initialMode: Int = 0,
    val modes: List<Mode>,
) {
    init {
        require(modes.size == Mode.MODE_COUNT) { "A genome has ${Mode.MODE_COUNT} modes, got ${modes.size}" }
    }

    fun mode(index: Int): Mode = modes[index]

    fun withMode(index: Int, mode: Mode): Genome = copy(modes = modes.toMutableList().also { it[index] = mode })

    /** Every value forced into its allowed range (used after imports and mutations). */
    fun sanitized(): Genome = copy(
        initialMode = initialMode.coerceIn(0, Mode.MODE_COUNT - 1),
        modes = modes.mapIndexed { index, mode -> mode.sanitized(index) },
    )

    /** Problems that would make the genome unsafe to simulate; empty when it is valid (SPEC «Валидность»). */
    fun problems(): List<String> {
        val result = mutableListOf<String>()
        if (initialMode !in 0 until Mode.MODE_COUNT) result += "initial mode $initialMode"
        modes.forEachIndexed { index, mode ->
            if (mode.sanitized(index) != mode) result += "mode ${index + 1} has values out of range"
        }
        return result
    }

    /** Modes reachable from [start] through divisions and stemocyte paths, [start] included. */
    fun reachableModes(start: Int): Set<Int> {
        val seen = linkedSetOf(start)
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty()) {
            val mode = modes[queue.removeFirst()]
            val next = mutableListOf(mode.child1Mode, mode.child2Mode)
            if (mode.cellType == CellType.STEMOCYTE) next += listOf(mode.path1Mode, mode.path2Mode)
            if (mode.cellType == CellType.GAMETE) next += mode.fusionMode
            for (m in next) if (seen.add(m)) queue.addLast(m)
        }
        return seen
    }

    /**
     * Modes the organism grown from the initial mode can ever take: the reachable ones and those its virocytes
     * copy into their victims, with every mode reachable from them. The initial mode comes first.
     */
    fun usedModes(): Set<Int> {
        val used = LinkedHashSet(reachableModes(initialMode))
        var grew = true
        while (grew) {
            grew = false
            for (index in used.toList()) {
                val mode = modes[index]
                if (mode.cellType == CellType.VIROCYTE && used.addAll(reachableModes(mode.virusMode))) grew = true
            }
        }
        return used
    }

    /** The lowest mode the organism does not use (see [usedModes]), or −1 when it uses all of them. */
    fun freeMode(): Int {
        val used = usedModes()
        return (0 until Mode.MODE_COUNT).firstOrNull { it !in used } ?: -1
    }

    companion object {
        /** The genome the synthesizer starts with: a phagocyte that splits into two free phagocytes. */
        fun default(): Genome = Genome(modes = List(Mode.MODE_COUNT) { Mode.default(it) })
    }
}

internal fun Float.clampTo(range: ClosedFloatingPointRange<Float>): Float =
    if (isNaN()) range.start else coerceIn(range.start, range.endInclusive)

/** Degrees into [0, 360). */
internal fun wrapDegrees(degrees: Float): Float {
    if (degrees.isNaN()) return 0f
    val wrapped = degrees - 360f * floor(degrees / 360f)
    return if (wrapped >= 360f) 0f else wrapped
}

internal fun hsvToRgb(hue: Float, saturation: Float, value: Float): Triple<Float, Float, Float> {
    val h = (hue % 1f) * 6f
    val chroma = value * saturation
    val x = chroma * (1f - abs(h % 2f - 1f))
    val m = value - chroma
    val (r, g, b) = when (h.toInt()) {
        0 -> Triple(chroma, x, 0f)
        1 -> Triple(x, chroma, 0f)
        2 -> Triple(0f, chroma, x)
        3 -> Triple(0f, x, chroma)
        4 -> Triple(x, 0f, chroma)
        else -> Triple(chroma, 0f, x)
    }
    return Triple(r + m, g + m, b + m)
}
