package io.github.torintheone.petri.sim

/**
 * Radiation and random infection (SPEC «Мутации и эволюция»). A mutation makes a new genome for the hit cell only
 * (copy-on-write): its current mode changes at once, the other modes show up in its descendants.
 */
internal class Mutations(private val world: World) {
    fun run() {
        val s = world.settings
        if (world.previewGrowth >= 0f) return
        if (s.radiation > 0f) irradiate(s)
        if (s.randomInfection) infect(s)
    }

    private fun irradiate(s: SubstrateSettings) {
        val c = world.cells
        val rng = world.rng
        val mutation = SimConfig.mutationsPerHour(s.radiation) * SimConfig.STEP_HOURS_F
        val death = SimConfig.radiationDeathsPerHour(s.radiation) * SimConfig.STEP_HOURS_F
        for (i in 0 until c.count) {
            if (world.isDead(i)) continue
            if (death > 0f && rng.chance(death)) {
                world.kill(i)
                continue
            }
            if (rng.chance(mutation)) {
                val slot = c.genome[i]
                val mutated = Mutator.mutate(world.genomes[slot], c.mode[i], rng, s)
                world.setGenome(i, world.genomes.slotFor(mutated, parentId = world.genomes.id(slot)))
            }
        }
    }

    private fun infect(s: SubstrateSettings) {
        val rng = world.rng
        if (!rng.chance(SimConfig.STEP_HOURS_F / SimConfig.INFECTION_INTERVAL_HOURS)) return
        val genome = Mutator.randomGenome(rng, s.genePool) ?: return
        val (px, py) = world.randomPointInDisk(world.dishRadius * 0.9f)
        world.addCell(genome, px, py)
    }
}

/** Mutations of genomes; every result is a valid genome (SPEC «Валидность»). */
object Mutator {
    /** One random change of [genome] in a cell that is now in [currentMode]. */
    fun mutate(genome: Genome, currentMode: Int, rng: Rng, settings: SubstrateSettings): Genome {
        val index = pickMode(genome, currentMode, rng)
        val structural = !settings.pointMutationsOnly && rng.chance(SimConfig.STRUCTURAL_MUTATION_SHARE)
        val mutated = if (structural) structural(genome, index, rng, settings.genePool) else point(genome, index, rng)
        return mutated.sanitized()
    }

    /**
     * Mostly a mode the organism actually uses (reachable from the initial or the current mode), so radiation shows;
     * sometimes any mode, which may come into play later.
     */
    private fun pickMode(genome: Genome, currentMode: Int, rng: Rng): Int {
        if (rng.chance(SimConfig.ANY_MODE_MUTATION_SHARE)) return rng.nextInt(Mode.MODE_COUNT)
        val used = (genome.reachableModes(genome.initialMode) + genome.reachableModes(currentMode)).toList()
        return used[rng.nextInt(used.size)]
    }

    /** A Gaussian nudge of one number of the mode, clipped to its range; angles wrap around. */
    private fun point(genome: Genome, index: Int, rng: Rng): Genome {
        val mode = genome.modes[index]
        val numbers = ModeNumbers.forType(mode.cellType)
        val number = numbers[rng.nextInt(numbers.size)]
        val width = number.range.endInclusive - number.range.start
        var value = number.get(mode) + rng.nextGaussian() * SimConfig.POINT_MUTATION_SIGMA * width
        value = when {
            number.isAngle -> wrapDegrees(value)
            number.isInteger -> value.toInt().toFloat().coerceIn(number.range.start, number.range.endInclusive)
            else -> value.coerceIn(number.range.start, number.range.endInclusive)
        }
        return genome.withMode(index, number.set(mode, value))
    }

    private fun structural(genome: Genome, index: Int, rng: Rng, genePool: Set<CellType>): Genome {
        val mode = genome.modes[index]
        return when (rng.nextInt(STRUCTURAL_KINDS)) {
            0 -> {
                val pool = genePool.toList().sorted()
                if (pool.isEmpty()) genome else genome.withMode(index, mode.copy(cellType = pool[rng.nextInt(pool.size)]))
            }
            1 -> genome.withMode(index, mode.copy(child1Mode = rng.nextInt(Mode.MODE_COUNT)))
            2 -> genome.withMode(index, mode.copy(child2Mode = rng.nextInt(Mode.MODE_COUNT)))
            3 -> genome.withMode(index, toggleFlag(mode, rng))
            4 -> {
                // Copy another mode over this one: a duplicated cell kind that can then drift on its own.
                val source = rng.nextInt(Mode.MODE_COUNT)
                if (source == index) genome else genome.withMode(index, genome.modes[source])
            }
            else -> genome.withMode(index, rewireInput(mode, rng))
        }
    }

    private fun toggleFlag(mode: Mode, rng: Rng): Mode = when (rng.nextInt(6)) {
        0 -> mode.copy(makeAdhesin = !mode.makeAdhesin)
        1 -> mode.copy(child1KeepBonds = !mode.child1KeepBonds)
        2 -> mode.copy(child2KeepBonds = !mode.child2KeepBonds)
        3 -> mode.copy(child1Mirror = !mode.child1Mirror)
        4 -> mode.copy(child2Mirror = !mode.child2Mirror)
        else -> mode.copy(prioritize = !mode.prioritize)
    }

    /** Points one programmable value of the mode's type at another input. */
    private fun rewireInput(mode: Mode, rng: Rng): Mode {
        val values = programmableOf(mode)
        if (values.isEmpty()) return toggleFlag(mode, rng)
        val (get, set) = values[rng.nextInt(values.size)]
        val input = SignalInput.entries[rng.nextInt(SignalInput.entries.size)]
        return set(mode, get(mode).copy(input = input).sanitized())
    }

    private fun programmableOf(mode: Mode): List<Pair<(Mode) -> ProgrammableValue, (Mode, ProgrammableValue) -> Mode>> =
        when (mode.cellType) {
            CellType.FLAGELLOCYTE -> listOf({ m: Mode -> m.swimForce } to { m: Mode, v: ProgrammableValue -> m.copy(swimForce = v) })
            CellType.BUOYOCYTE -> listOf({ m: Mode -> m.density } to { m: Mode, v: ProgrammableValue -> m.copy(density = v) })
            CellType.MYOCYTE -> listOf(
                { m: Mode -> m.contraction } to { m: Mode, v: ProgrammableValue -> m.copy(contraction = v) },
                { m: Mode -> m.bend } to { m: Mode, v: ProgrammableValue -> m.copy(bend = v) },
                { m: Mode -> m.lift } to { m: Mode, v: ProgrammableValue -> m.copy(lift = v) },
            )
            CellType.NEUROCYTE -> listOf(
                { m: Mode -> m.neuronOutput1 } to { m: Mode, v: ProgrammableValue -> m.copy(neuronOutput1 = v) },
                { m: Mode -> m.neuronOutput2 } to { m: Mode, v: ProgrammableValue -> m.copy(neuronOutput2 = v) },
                { m: Mode -> m.neuronOutput3 } to { m: Mode, v: ProgrammableValue -> m.copy(neuronOutput3 = v) },
                { m: Mode -> m.neuronOutput4 } to { m: Mode, v: ProgrammableValue -> m.copy(neuronOutput4 = v) },
            )
            CellType.STEMOCYTE -> listOf(
                { m: Mode -> m.path1Signal } to { m: Mode, v: ProgrammableValue -> m.copy(path1Signal = v) },
                { m: Mode -> m.path2Signal } to { m: Mode, v: ProgrammableValue -> m.copy(path2Signal = v) },
            )
            CellType.CILIOCYTE -> listOf(
                { m: Mode -> m.leftCilia } to { m: Mode, v: ProgrammableValue -> m.copy(leftCilia = v) },
                { m: Mode -> m.rightCilia } to { m: Mode, v: ProgrammableValue -> m.copy(rightCilia = v) },
            )
            else -> emptyList()
        }

    /**
     * A random but valid genome for «Заражение случайными клетками»: a few modes of types from [genePool] that refer
     * to each other, so the newcomer grows into something; null when the pool is empty.
     */
    fun randomGenome(rng: Rng, genePool: Set<CellType>): Genome? {
        val pool = genePool.toList().sorted()
        if (pool.isEmpty()) return null
        val used = 1 + rng.nextInt(RANDOM_GENOME_MODES)
        val modes = MutableList(Mode.MODE_COUNT) { index ->
            var mode = Mode.default(index)
            if (index < used) {
                mode = mode.copy(
                    cellType = pool[rng.nextInt(pool.size)],
                    makeAdhesin = rng.nextBoolean(),
                    child1Mode = rng.nextInt(used),
                    child2Mode = rng.nextInt(used),
                    child1KeepBonds = rng.nextBoolean(),
                    child2KeepBonds = rng.nextBoolean(),
                    splitMass = rng.nextFloat(RANDOM_SPLIT_MASS.start, RANDOM_SPLIT_MASS.endInclusive),
                    splitRatio = rng.nextFloat(RANDOM_SPLIT_RATIO.start, RANDOM_SPLIT_RATIO.endInclusive),
                    splitAngle = SNAP_ANGLE * rng.nextInt(ANGLE_STEPS),
                    child1Angle = SNAP_ANGLE * rng.nextInt(ANGLE_STEPS),
                    child2Angle = SNAP_ANGLE * rng.nextInt(ANGLE_STEPS),
                    adhesinStiffness = rng.nextFloat(),
                    nutrientPriority = rng.nextFloat(),
                    red = rng.nextFloat(),
                    green = rng.nextFloat(),
                    blue = rng.nextFloat(),
                )
            }
            mode
        }
        return Genome(initialMode = 0, modes = modes).sanitized()
    }

    private const val STRUCTURAL_KINDS = 6
    private const val RANDOM_GENOME_MODES = 4
    private val RANDOM_SPLIT_MASS = 1.5f..3.4f
    private val RANDOM_SPLIT_RATIO = 0.3f..0.7f
    private const val SNAP_ANGLE = 15f
    private const val ANGLE_STEPS = 24
}
