package io.github.torintheone.petri.sim

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** A file that cannot be read; [problem] says why, so the app can show a clear message instead of crashing. */
class FormatException(val problem: Problem, cause: Throwable? = null) : Exception(problem.name, cause) {
    enum class Problem {
        /** Not a file of this kind: e.g. a dish where a genome was expected. */
        WRONG_KIND,

        /** Written by a newer version of the app. */
        NEWER_VERSION,

        /** Broken or incomplete. */
        DAMAGED,
    }
}

/**
 * Genome files (SPEC «Сохранение»): JSON with `formatVersion`, the name, the initial mode and all 40 modes with
 * every parameter, defaults included, so a shared genome means the same in any later version.
 */
object GenomeCodec {
    const val VERSION = 1
    const val EXTENSION = ".petri-genome.json"

    /** Turns an older file into the next version: index = the version the step starts from. */
    private val migrations: Map<Int, (JsonObject) -> JsonObject> = emptyMap()

    fun encode(genome: Genome): String {
        val body = Formats.full.encodeToJsonElement(Genome.serializer(), genome) as JsonObject
        return JsonObject(linkedMapOf<String, JsonElement>(FORMAT_VERSION to JsonPrimitive(VERSION)) + body).toString()
    }

    /** Reads a genome file; older versions are migrated, out-of-range values are clamped. */
    fun decode(text: String): Genome {
        val root = Formats.parse(text)
        if ("modes" !in root || "cells" in root) throw FormatException(FormatException.Problem.WRONG_KIND)
        val json = Formats.migrate(root, VERSION, migrations)
        val modes = json["modes"] as? JsonArray ?: throw FormatException(FormatException.Problem.DAMAGED)
        // A hand-edited file may have lost or gained modes: missing ones become fresh modes, extra ones are dropped.
        val fixedModes = JsonArray(
            List(Mode.MODE_COUNT) { index ->
                modes.getOrNull(index) ?: Formats.full.encodeToJsonElement(Mode.serializer(), Mode.default(index))
            },
        )
        val genome = Formats.decode { Formats.full.decodeFromJsonElement(Genome.serializer(), JsonObject(json + ("modes" to fixedModes))) }
        return genome.sanitized()
    }
}

/**
 * Dish files (SPEC «Сохранение»): gzip-compressed JSON of a [DishState]. Values equal to their defaults are left
 * out to keep files small; `FormatsTest` guards the defaults, because changing one changes what old files mean.
 */
object DishCodec {
    const val VERSION = 1
    const val EXTENSION = ".petri-dish.gz"

    /** Turns an older file into the next version: index = the version the step starts from. */
    private val migrations: Map<Int, (JsonObject) -> JsonObject> = emptyMap()

    fun encode(state: DishState): ByteArray {
        val bytes = ByteArrayOutputStream()
        GZIPOutputStream(bytes).use { it.write(Formats.compact.encodeToString(DishState.serializer(), state).toByteArray()) }
        return bytes.toByteArray()
    }

    fun decodeState(data: ByteArray): DishState {
        val gzipped = data.size >= 2 && data[0] == GZIP_MAGIC_0 && data[1] == GZIP_MAGIC_1
        if (!gzipped) throw FormatException(FormatException.Problem.WRONG_KIND)
        val text = try {
            GZIPInputStream(data.inputStream()).use { it.readBytes() }.decodeToString()
        } catch (e: IOException) {
            throw FormatException(FormatException.Problem.DAMAGED, e)
        }
        val root = Formats.parse(text)
        if ("cells" !in root || "modes" in root) throw FormatException(FormatException.Problem.WRONG_KIND)
        val json = Formats.migrate(root, VERSION, migrations)
        return Formats.decode { Formats.compact.decodeFromJsonElement(DishState.serializer(), json) }
    }

    /** Reads a dish file and builds its world; see [DishState.toWorld]. */
    fun decode(data: ByteArray): World = decodeState(data).toWorld()

    private const val GZIP_MAGIC_0 = 0x1f.toByte()
    private const val GZIP_MAGIC_1 = 0x8b.toByte()
}

internal object Formats {
    /** Every value written: for files people share. */
    val full = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        allowSpecialFloatingPointValues = true
        encodeDefaults = true
    }

    /** Defaults left out: for dishes, which carry many genomes. */
    val compact = Json(full) { encodeDefaults = false }

    fun parse(text: String): JsonObject {
        val element = try {
            full.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw FormatException(FormatException.Problem.WRONG_KIND, e)
        }
        return element as? JsonObject ?: throw FormatException(FormatException.Problem.WRONG_KIND)
    }

    /** Brings [root] up to [version] one step at a time; a file from the future is refused. */
    fun migrate(root: JsonObject, version: Int, steps: Map<Int, (JsonObject) -> JsonObject>): JsonObject {
        val fileVersion = try {
            root[FORMAT_VERSION]?.jsonPrimitive?.intOrNull ?: 1
        } catch (e: IllegalArgumentException) {
            throw FormatException(FormatException.Problem.DAMAGED, e)
        }
        if (fileVersion > version) throw FormatException(FormatException.Problem.NEWER_VERSION)
        if (fileVersion < 1) throw FormatException(FormatException.Problem.DAMAGED)
        var json = root
        for (from in fileVersion until version) {
            val step = steps[from] ?: throw FormatException(FormatException.Problem.DAMAGED)
            json = step(json)
        }
        return JsonObject(json + (FORMAT_VERSION to JsonPrimitive(version)))
    }

    /** Runs a decoder and turns its failures into [FormatException]. */
    inline fun <T> decode(block: () -> T): T = try {
        block()
    } catch (e: SerializationException) {
        throw FormatException(FormatException.Problem.DAMAGED, e)
    } catch (e: IllegalArgumentException) {
        throw FormatException(FormatException.Problem.DAMAGED, e)
    }
}

private const val FORMAT_VERSION = "formatVersion"
