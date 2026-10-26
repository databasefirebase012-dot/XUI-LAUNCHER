package com.movtery.zalithlauncher.utils.error

import com.movtery.zalithlauncher.renderer.Renderers
import com.movtery.zalithlauncher.utils.path.PathManager
import java.io.File
import java.util.zip.ZipFile

object XuiErrorAnalyzer {
    data class Report(val renderer: String, val findings: List<String>, val actions: List<String>, val logExcerpt: String)

    fun analyze(exitCode: Int): Report {
        val root = runCatching { File(PathManager.DIR_GAME_HOME) }.getOrNull()
        val renderer = runCatching { Renderers.getCurrentRenderer().getRendererName() }
            .getOrElse { "Tidak diketahui (renderer belum dapat dibaca)" }
        val logs = root?.let { findLogs(it) }.orEmpty()
        val combined = logs.joinToString("\n") { runCatching { it.readText().takeLast(700_000) }.getOrDefault("") }
        val lower = combined.lowercase()
        val findings = mutableListOf<String>()
        val actions = mutableListOf<String>()

        fun evidence(title: String, needles: List<String>): String {
            val line = combined.lineSequence().firstOrNull { row -> needles.any { row.contains(it, true) } }
                ?.trim().orEmpty().take(220)
            return if (line.isBlank()) title else "$title: $line"
        }

        if (lower.contains("duplicate") && (lower.contains("mod") || lower.contains("modid"))) {
            findings += evidence("Log mendeteksi mod duplikat", listOf("duplicate", "already contains"))
            actions += "Periksa mod yang disebut di log dan hapus salinan yang tidak diperlukan."
        }
        if (lower.contains("modresolutionexception") || lower.contains("missing dependency") ||
            lower.contains("requires version") || lower.contains("depends on")) {
            findings += evidence("Dependency mod hilang atau versinya tidak cocok", listOf("ModResolutionException", "missing dependency", "requires version", "depends on"))
            actions += "Pasang dependency yang disebut dengan versi sesuai Minecraft dan loader."
        }
        if (lower.contains("incompatible mod set") || lower.contains("incompatible mods") ||
            lower.contains("requires minecraft") || lower.contains("only supports") ||
            lower.contains("unsupported minecraft version")) {
            findings += evidence("Kemungkinan versi mod/Minecraft/loader tidak kompatibel", listOf("incompatible", "requires minecraft", "only supports", "unsupported"))
            actions += "Cocokkan versi Minecraft, Fabric/Forge/NeoForge, dan versi mod."
        }
        if (lower.contains("mixin apply failed") || lower.contains("invalidinjectionexception") ||
            lower.contains("mixinapplyerror") || lower.contains("injectionerror")) {
            findings += evidence("Konflik Mixin/mod", listOf("Mixin apply failed", "InvalidInjectionException", "MixinApplyError", "InjectionError"))
            actions += "Uji dengan menonaktifkan mod yang disebut pada error Mixin, lalu cocokkan versinya."
        }
        if (lower.contains("outofmemoryerror") || lower.contains("could not reserve enough space") || lower.contains("failed to allocate")) {
            findings += "Kegagalan alokasi memori (Out of Memory / RAM)"
            actions += "Turunkan alokasi RAM Minecraft dan tutup aplikasi lain."
        }
        if (lower.contains("unsupportedclassversionerror") || lower.contains("class file version") ||
            lower.contains("unrecognized vm option") || lower.contains("could not create the java virtual machine")) {
            findings += evidence("Kemungkinan Java runtime/argumen JVM tidak cocok", listOf("UnsupportedClassVersionError", "class file version", "Unrecognized VM option", "Could not create"))
            actions += "Pilih Java runtime yang sesuai versi Minecraft dan periksa argumen JVM."
        }
        if (lower.contains("shader") && (lower.contains("compile") || lower.contains("failed") || lower.contains("error"))) {
            findings += evidence("Kemungkinan shader gagal dimuat/dikompilasi", listOf("shader", "compile", "failed"))
            actions += "Matikan shader sementara dan cocokkan versi shader dengan Iris/OptiFine serta renderer."
        }
        if (listOf("egl_bad", "opengl", "vulkan", "libgl4es", "zink", "failed to create context", "graphics adapters").any { lower.contains(it) }) {
            findings += evidence("Kemungkinan masalah grafis/driver; renderer aktif: $renderer", listOf("EGL_BAD", "OpenGL", "Vulkan", "GL4ES", "Zink", "graphics adapters", "failed to create context"))
            actions += "Coba renderer lain yang tersedia; matikan shader sementara untuk mengisolasi masalah."
        }
        if (lower.contains("nosuchmethoderror") || lower.contains("noclassdeffounderror") || lower.contains("classnotfoundexception")) {
            findings += evidence("Class/method tidak ditemukan; mungkin mod atau dependency bertabrakan", listOf("NoSuchMethodError", "NoClassDefFoundError", "ClassNotFoundException"))
            actions += "Periksa nama class/mod pada log dan cocokkan dependency serta versi mod."
        }
        val duplicates = findDuplicateModJars(root)
        if (duplicates.isNotEmpty()) {
            findings += "Mod duplikat terdeteksi dari metadata JAR: " + duplicates.joinToString(", ")
            actions += "Hapus salah satu JAR untuk setiap mod ID yang tercantum, setelah memastikan mod tersebut memang tidak diperlukan terpisah."
        }
        if (findings.isEmpty()) {
            findings += when (exitCode) {
                -1 -> "Exit code -1 adalah kode crash umum dan tidak cukup untuk menentukan penyebab tanpa log."
                1 -> "Exit code 1 menandakan game berhenti dengan error; periksa log untuk penyebab sebenarnya."
                else -> "Exit code $exitCode tidak cocok dengan pola penyebab yang dikenali; log mungkin tidak ditemukan."
            }
            actions += "Tekan Share Log Files dan periksa error pertama serta bagian 'Caused by' di latest.log/crash report."
        }
        if (actions.isEmpty()) actions += "Periksa error pertama di log dan coba nonaktifkan mod terakhir yang ditambahkan."
        val excerpt = combined.lineSequence().firstOrNull { row ->
            listOf("Caused by:", "Exception", "ERROR", "FATAL", "crash").any { row.contains(it, true) }
        }?.trim().orEmpty().take(260)
        return Report(renderer, findings.distinct().take(6), actions.distinct().take(6), excerpt)
    }

    private fun findLogs(root: File): List<File> {
        val found = mutableListOf<File>()
        val queue = ArrayDeque<Pair<File, Int>>()
        queue.add(root to 0)
        while (queue.isNotEmpty() && found.size < 50) {
            val (dir, depth) = queue.removeFirst()
            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                if (child.isDirectory && depth < 4 && child.name !in setOf("libraries", "assets", "versions", "runtimes", ".git")) {
                    queue.add(child to depth + 1)
                } else if (child.isFile && child.length() in 1..(8L * 1024 * 1024) &&
                    (child.name.equals("latest.log", true) || child.name.equals("latestlog.txt", true) ||
                        child.name.endsWith(".log", true) || child.name.startsWith("hs_err_pid"))) {
                    found += child
                }
            }
        }
        return found.sortedByDescending { it.lastModified() }.take(3)
    }

    private fun findDuplicateModJars(root: File?): List<String> {
        if (root == null) return emptyList()
        val modsDirs = mutableListOf<File>()
        val queue = ArrayDeque<Pair<File, Int>>()
        queue.add(root to 0)
        while (queue.isNotEmpty()) {
            val (dir, depth) = queue.removeFirst()
            if (dir.name.equals("mods", true)) modsDirs += dir
            if (depth >= 4) continue
            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            children.filter { it.isDirectory && it.name !in setOf("assets", "libraries", "runtimes") }
                .forEach { queue.add(it to depth + 1) }
        }
        val result = mutableListOf<String>()
        modsDirs.forEach { dir ->
            val jars = dir.listFiles { file -> file.isFile && file.extension.equals("jar", true) } ?: return@forEach
            jars.groupBy { it.name.lowercase() }.filterValues { it.size > 1 }.forEach { (name, files) ->
                result += "$name (${files.size} salinan)"
            }
            val ids = mutableMapOf<String, MutableList<String>>()
            jars.take(250).forEach jarLoop@{ jar ->
                val id = readModId(jar) ?: return@jarLoop
                ids.getOrPut(id) { mutableListOf() }.add(jar.name)
            }
            ids.filterValues { it.size > 1 }.forEach { (id, files) ->
                result += "$id: ${files.joinToString(", ")}"
            }
        }
        return result.distinct().take(8)
    }

    private fun readModId(file: File): String? = runCatching {
        ZipFile(file).use { zip ->
            val fabric = zip.getEntry("fabric.mod.json")
            if (fabric != null) {
                val json = zip.getInputStream(fabric).bufferedReader().use { it.readText().take(100_000) }
                Regex("\\\"id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(json)?.groupValues?.get(1)?.let { return@use it }
            }
            val toml = zip.getEntry("META-INF/mods.toml")
            if (toml != null) {
                val text = zip.getInputStream(toml).bufferedReader().use { it.readText().take(150_000) }
                Regex("(?m)^\\s*modId\\s*=\\s*\\\"([^\\\"]+)\\\"").find(text)?.groupValues?.get(1)?.let { return@use it }
            }
            null
        }
    }.getOrNull()
}
