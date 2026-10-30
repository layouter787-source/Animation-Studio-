package com.animationstudio.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.ui.graphics.asAndroidBitmap
import com.animationstudio.domain.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Exportações em PNG (um frame, sequência numa pasta, ou ZIP com todos os frames). */
internal object Exporter {
    private fun frameBitmap(project: Project, index: Int, sw: Int, sh: Int): Bitmap =
        renderComposite(project, index, sw, sh, sw, sh, PaperColor, useCache = false).asAndroidBitmap()

    private fun writePng(bmp: Bitmap, out: OutputStream) {
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
    }

    private fun name(base: String, i: Int) = "${base}_${(i + 1).toString().padStart(4, '0')}.png"

    suspend fun exportPng(context: Context, uri: Uri, project: Project, index: Int, sw: Int, sh: Int): Boolean =
        withContext(Dispatchers.Default) {
            try {
                val out = context.contentResolver.openOutputStream(uri) ?: return@withContext false
                out.use { writePng(frameBitmap(project, index, sw, sh), it) }
                true
            } catch (e: Exception) {
                false
            }
        }

    suspend fun exportZip(context: Context, uri: Uri, project: Project, sw: Int, sh: Int, base: String): Boolean =
        withContext(Dispatchers.Default) {
            try {
                val out = context.contentResolver.openOutputStream(uri) ?: return@withContext false
                ZipOutputStream(out.buffered()).use { zip ->
                    for (i in project.frameIds.indices) {
                        zip.putNextEntry(ZipEntry(name(base, i)))
                        writePng(frameBitmap(project, i, sw, sh), zip)
                        zip.closeEntry()
                    }
                }
                true
            } catch (e: Exception) {
                false
            }
        }

    suspend fun exportSequence(context: Context, treeUri: Uri, project: Project, sw: Int, sh: Int, base: String): Boolean =
        withContext(Dispatchers.Default) {
            try {
                val resolver = context.contentResolver
                val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
                for (i in project.frameIds.indices) {
                    val doc = DocumentsContract.createDocument(resolver, parent, "image/png", name(base, i)) ?: return@withContext false
                    val out = resolver.openOutputStream(doc) ?: return@withContext false
                    out.use { writePng(frameBitmap(project, i, sw, sh), it) }
                }
                true
            } catch (e: Exception) {
                false
            }
        }
}
