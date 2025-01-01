package fi.iki.ede.safe.gpm.ui.utilities

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import fi.iki.ede.safe.BuildConfig
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

// Google Password Manager exports stuff in plain text (*puke*)
// let's give user a chance to delete the export immediately
// or move to our app folder (which android OS keeps quite safe)
fun getImportStreamAndMoveOrDeleteOriginal(
    context: Context,
    selectedDoc: Uri,
    originalNotDeleted: () -> Unit
): InputStream? = copyImportToMyAppFolder(context, selectedDoc) ?: run {
    // oh, we failed to copy..not much we can do here anymore?
    // Read the INPUT and ask to delete
    val i = context.contentResolver.openInputStream(selectedDoc)
    i?.let { ByteArrayInputStream(it.readBytes()) }
}.apply {
    val document = DocumentFile.fromSingleUri(context, selectedDoc)
    document?.delete()?.let {
        if (!it) originalNotDeleted()
    }
}

private const val importedDocFile = "import.csv"
fun doesInternalDocumentExist(context: Context) =
    File(context.filesDir, importedDocFile).exists()

fun deleteInternalCopy(context: Context) =
    File(context.filesDir, importedDocFile).let {
        if (it.exists()) {
            it.delete()
        }
    }

fun getInternalDocumentInputStream(context: Context): InputStream? {
    val file = File(context.filesDir, importedDocFile)
    if (file.exists()) {
        return file.inputStream()
    } else {
        return null
    }
}

fun copyImportToMyAppFolder(context: Context, sourceUri: Uri): InputStream? =
    try {
        val resolver = context.contentResolver
        val destinationFile = File(context.filesDir, importedDocFile)
        resolver.openInputStream(sourceUri)?.use { inputStream ->
            FileOutputStream(destinationFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        val copiedFiled = File(context.filesDir, importedDocFile)
        // in emulator we will keep this file in app folder
        if (!BuildConfig.DEBUG) {
            copiedFiled.deleteOnExit()
        }
        copiedFiled.inputStream()
    } catch (ex: Exception) {
        val i = context.contentResolver.openInputStream(sourceUri)
        i?.let { ByteArrayInputStream(it.readBytes()) }
    }
