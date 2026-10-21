package io.github.torintheone.petri.data

import android.content.Context
import android.net.Uri
import io.github.torintheone.petri.sim.DishCodec
import io.github.torintheone.petri.sim.Genome
import io.github.torintheone.petri.sim.GenomeCodec
import java.io.File
import java.io.IOException
import java.util.Base64

/** A genome in the genome bank. */
data class GenbankEntry(val name: String, val genome: Genome, val modified: Long)

/** A saved dish. */
data class DishEntry(val name: String, val modified: Long, val bytes: Long)

/**
 * Files in the app's own folder (SPEC «Сохранение»): the genome bank, saved dishes and the autosave.
 * A file name is the item's name in URL-safe Base64, so any name works on any file system and saving
 * under an existing name replaces that item. All methods block: call them off the main thread.
 */
class Storage(context: Context) {
    private val genomeDir = File(context.filesDir, "genomes")
    private val dishDir = File(context.filesDir, "dishes")
    private val autosaveDir = File(context.filesDir, "autosave")
    private val shareDir = File(context.cacheDir, "share")
    private val presetsFile = File(context.filesDir, "presets.txt")
    private val legacyPresetsMarker = File(context.filesDir, "presets-1")
    private val resolver = context.contentResolver

    // ---- Genome bank ----

    /** Every readable genome, newest first; unreadable files are skipped. */
    fun genomes(): List<GenbankEntry> =
        files(genomeDir, GenomeCodec.EXTENSION).mapNotNull { (name, file) ->
            runCatching { GenbankEntry(name, GenomeCodec.decode(file.readText()), file.lastModified()) }.getOrNull()
        }.sortedByDescending { it.modified }

    fun saveGenome(name: String, genome: Genome) {
        write(file(genomeDir, name, GenomeCodec.EXTENSION), GenomeCodec.encode(genome.copy(name = name)).toByteArray())
    }

    fun deleteGenome(name: String) {
        file(genomeDir, name, GenomeCodec.EXTENSION).delete()
    }

    /** Names of the example genomes already put into the bank once (see [io.github.torintheone.petri.sim.Preset]). */
    fun offeredPresets(): Set<String> {
        if (presetsFile.exists()) return presetsFile.readLines().filter { it.isNotBlank() }.toSet()
        // Version 0.3.0 kept only a marker for its four examples.
        return if (legacyPresetsMarker.exists()) LEGACY_PRESETS else emptySet()
    }

    fun markPresetsOffered(names: List<String>) {
        val all = offeredPresets() + names
        write(presetsFile, all.joinToString("\n").toByteArray())
        legacyPresetsMarker.delete()
    }

    // ---- Dishes ----

    fun dishes(): List<DishEntry> =
        files(dishDir, DishCodec.EXTENSION).map { (name, file) -> DishEntry(name, file.lastModified(), file.length()) }
            .sortedByDescending { it.modified }

    fun saveDish(name: String, data: ByteArray) {
        write(file(dishDir, name, DishCodec.EXTENSION), data)
    }

    fun readDish(name: String): ByteArray = file(dishDir, name, DishCodec.EXTENSION).readBytes()

    fun deleteDish(name: String) {
        file(dishDir, name, DishCodec.EXTENSION).delete()
    }

    // ---- Autosave ----

    fun writeAutosave(dish: ByteArray, genome: Genome) {
        write(File(autosaveDir, "dish" + DishCodec.EXTENSION), dish)
        write(File(autosaveDir, "genome" + GenomeCodec.EXTENSION), GenomeCodec.encode(genome).toByteArray())
    }

    fun readAutosaveDish(): ByteArray? = File(autosaveDir, "dish" + DishCodec.EXTENSION).takeIf { it.exists() }?.readBytes()

    fun readAutosaveGenome(): String? = File(autosaveDir, "genome" + GenomeCodec.EXTENSION).takeIf { it.exists() }?.readText()

    // ---- Sharing ----

    /** Writes [genome] to a file for the share sheet; the file name is the genome's name, readable to people. */
    fun shareableGenome(genome: Genome, fallbackName: String): File {
        shareDir.mkdirs()
        shareDir.listFiles()?.forEach { it.delete() }
        val readable = genome.name.ifBlank { fallbackName }.replace(UNSAFE_CHARACTERS, "_").take(MAX_NAME_LENGTH).ifBlank { "genome" }
        val file = File(shareDir, readable + GenomeCodec.EXTENSION)
        file.writeText(GenomeCodec.encode(genome))
        return file
    }

    /** Reads a file the person picked; refuses anything bigger than [limit] bytes. */
    fun readPicked(uri: Uri, limit: Int): ByteArray {
        val stream = resolver.openInputStream(uri) ?: throw IOException("cannot open $uri")
        return stream.use { input ->
            val data = input.readNBytesCompat(limit + 1)
            if (data.size > limit) throw IOException("file is larger than $limit bytes")
            data
        }
    }

    // ---- Files ----

    private fun file(dir: File, name: String, extension: String): File = File(dir, encodeName(name) + extension)

    private fun files(dir: File, extension: String): List<Pair<String, File>> =
        dir.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(extension) }
            .mapNotNull { file -> decodeName(file.name.removeSuffix(extension))?.let { it to file } }

    /** Writes through a temporary file, so a crash halfway never leaves a broken file behind. */
    private fun write(target: File, data: ByteArray) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeBytes(data)
        if (!temp.renameTo(target)) {
            target.delete()
            if (!temp.renameTo(target)) throw IOException("cannot write ${target.name}")
        }
    }

    private fun encodeName(name: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(name.toByteArray(Charsets.UTF_8))

    private fun decodeName(encoded: String): String? =
        runCatching { String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8) }.getOrNull()

    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_BYTES)
        while (out.size() < max) {
            val read = read(buffer, 0, minOf(buffer.size, max - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    companion object {
        /** Longest name of a genome or a dish: keeps file names within the usual 255-byte limit. */
        const val MAX_NAME_LENGTH = 40

        /** Largest genome file accepted on import. */
        const val MAX_GENOME_BYTES = 2 * 1024 * 1024

        private const val BUFFER_BYTES = 16 * 1024
        private val LEGACY_PRESETS = setOf("SINGLE_PHAGOCYTES", "CHAIN", "ARC", "PHOTOCYTE_COLONY")
        private val UNSAFE_CHARACTERS = Regex("[^\\p{L}\\p{N} _-]")
    }
}
