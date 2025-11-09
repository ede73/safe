package fi.iki.ede.gpmui.utilities

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import okio.BufferedSource
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import okio.source

// Google Password Manager exports stuff in plain text (*puke*)
// let's copy the file to safety inside app folder
fun getInternalCopyOfGpmCsvAsBufferedSourceAndDeleteOriginal(
    context: Context,
    selectedDoc: Uri,
    originalNotDeleted: () -> Unit
): BufferedSource? {
    val copiedFile = copyOriginalGpmCsvToMyAppFolder(context, selectedDoc)
    tryToDeleteOriginalGpmCsv(context, selectedDoc, originalNotDeleted)

    return copiedFile?.let { path ->
        FileSystem.SYSTEM.source(path.toPath()).buffer()
    } ?: context.contentResolver.openInputStream(selectedDoc)?.use { inputStream ->
        inputStream.source().buffer()
    }
}

private fun tryToDeleteOriginalGpmCsv(
    context: Context,
    selectedDoc: Uri,
    originalNotDeleted: () -> Unit
) {
    try {
        val document = DocumentFile.fromSingleUri(context, selectedDoc)
        if (document?.delete() == false) { // If delete returns false, it failed
            originalNotDeleted()
        }
    } catch (e: Exception) {
        // If an exception occurs (e.g. security exception), we also failed to delete.
        originalNotDeleted()
    }
}

private const val IMPORTED_GPM_CSV = "import.csv"

fun doesInternalCopyOfGpmCsvImportExist(context: Context): Boolean =
    (context.filesDir.absolutePath.toPath() / IMPORTED_GPM_CSV).let { FileSystem.SYSTEM.exists(it) }

fun deleteInternalCopyOfGpmCsvImport(context: Context): Boolean =
    (context.filesDir.absolutePath.toPath() / IMPORTED_GPM_CSV).let { path ->
        if (FileSystem.SYSTEM.exists(path)) {
            FileSystem.SYSTEM.delete(path)
            true
        } else {
            true // Or false, depending on desired semantics. True seems fine.
        }
    }

fun getInternalCopyOfGpmCsvAsBufferedSource(context: Context): BufferedSource? =
    (context.filesDir.absolutePath.toPath() / IMPORTED_GPM_CSV).let { path ->
        if (FileSystem.SYSTEM.exists(path)) {
            try {
                FileSystem.SYSTEM.source(path).buffer()
            } catch (e: Exception) {
                null
            }
        } else null
    }

private fun copyOriginalGpmCsvToMyAppFolder(context: Context, sourceUri: Uri): String? =
    try {
        val destinationPath = (context.filesDir.absolutePath.toPath() / IMPORTED_GPM_CSV)
        context.contentResolver.openInputStream(sourceUri)?.source()?.buffer().use { input ->
            if (input != null) {
                FileSystem.SYSTEM.sink(destinationPath).buffer().use { output ->
                    output.writeAll(input)
                }
            } else return null
        }
        destinationPath.toString()
    } catch (ex: Exception) {
        null
    }
