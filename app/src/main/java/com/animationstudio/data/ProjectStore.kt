package com.animationstudio.data

import android.content.Context
import android.graphics.Bitmap
import com.animationstudio.domain.Project
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Guarda cada projeto em filesDir/projects/<id>.json (+ miniatura <id>.png) com escrita atómica. */
class ProjectStore(context: Context) {
    private val dir = File(context.filesDir, "projects").apply { mkdirs() }

    private fun fileOf(id: String) = File(dir, "$id.json")

    fun thumbnailFile(id: String) = File(dir, "$id.png")

    fun list(): List<ProjectMeta> =
        (dir.listFiles { f -> f.extension == "json" } ?: emptyArray())
            .mapNotNull { f -> try { ProjectJson.decodeMeta(f.readText()) } catch (e: Exception) { null } }
            .sortedByDescending { it.modifiedAt }

    fun load(id: String): Pair<ProjectMeta, Project>? = try {
        ProjectJson.decode(fileOf(id).readText())
    } catch (e: Exception) {
        null
    }

    fun save(meta: ProjectMeta, project: Project) {
        val target = fileOf(meta.id)
        val tmp = File(dir, "${meta.id}.json.tmp")
        tmp.writeText(ProjectJson.encode(meta, project))
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    fun saveThumbnail(id: String, bitmap: Bitmap) {
        val tmp = File(dir, "$id.png.tmp")
        FileOutputStream(tmp).use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }
        Files.move(tmp.toPath(), thumbnailFile(id).toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    fun delete(id: String) {
        fileOf(id).delete()
        thumbnailFile(id).delete()
    }
}
