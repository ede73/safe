package fi.iki.ede.gpmui.utilities

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import fi.iki.ede.gpmui.BuildConfig
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

// Google Password Manager exports stuff in plain text (*puke*)
// let's copy the file to safety inside app folder
fun getInternalCopyOfGpmCsvAsImportStreamAndDeleteOriginal(
    context: Context,
    selectedDoc: Uri,
    originalNotDeleted: () -> Unit
): InputStream? = copyOriginalGpmCsvToMyAppFolder(context, selectedDoc).apply {
    tryToDeleteOriginalGpmCsv(context, selectedDoc, originalNotDeleted)
} ?: run {
    // oh, we failed to copy..not much we can do here anymore?
    // Read the INPUT and ask to delete
    val i = context.contentResolver.openInputStream(selectedDoc)
    i?.let { ByteArrayInputStream(it.readBytes()) }
}

private fun tryToDeleteOriginalGpmCsv(
    context: Context,
    selectedDoc: Uri,
    originalNotDeleted: () -> Unit
) {
    val document = DocumentFile.fromSingleUri(context, selectedDoc)
    document?.delete()?.let {
        if (!it) originalNotDeleted()
    }
}

private const val IMPORTED_GPM_CSV = "import.csv"
fun doesInternalCopyOfGpmCsvImportExist(context: Context) =
    File(context.filesDir, IMPORTED_GPM_CSV).exists()

fun deleteInternalCopyOfGpmCsvImport(context: Context) =
    File(context.filesDir, IMPORTED_GPM_CSV).let {
        if (it.exists()) {
            it.delete()
        }
    }

fun getInternalCopyOfGpmCsvAsInputStream(context: Context): InputStream? {
    val file = File(context.filesDir, IMPORTED_GPM_CSV)
    if (file.exists()) {
        return file.inputStream()
    } else {
        return null
    }
}

fun copyOriginalGpmCsvToMyAppFolder(context: Context, sourceUri: Uri): InputStream? =
    try {
        val resolver = context.contentResolver
        val destinationFile = File(context.filesDir, IMPORTED_GPM_CSV)
        resolver.openInputStream(sourceUri)?.use { inputStream ->
            FileOutputStream(destinationFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        val copiedFiled = File(context.filesDir, IMPORTED_GPM_CSV)
        // in emulator we will keep this file in app folder
        if (!BuildConfig.DEBUG) {
            copiedFiled.deleteOnExit()
        }
        copiedFiled.inputStream()
    } catch (ex: Exception) {
        val i = context.contentResolver.openInputStream(sourceUri)
        i?.let { ByteArrayInputStream(it.readBytes()) }
    }
