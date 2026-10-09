package app.ritela.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import app.ritela.data.PartnerCrypto
import app.ritela.data.PartnerMessage
import java.io.File

object PartnerFiles {
    fun read(context: Context, uri: Uri): ByteArray =
        requireNotNull(context.contentResolver.openInputStream(uri)).use {
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = it.read(buffer)
                if (count == -1) break
                total += count
                require(total <= PartnerCrypto.MAX_PACKET)
                output.write(buffer, 0, count)
            }
            val bytes = output.toByteArray()
            require(bytes.size in 1..PartnerCrypto.MAX_PACKET)
            bytes
        }
    fun share(context: Context, profile: String, message: PartnerMessage) {
        require(java.util.UUID.fromString(profile).toString() == profile)
        val directory = File(context.cacheDir, "partner/$profile").apply {
            check(mkdirs() || isDirectory)
        }
        // Only ciphertext is exposed through the narrow cache FileProvider root.
        val file = File(directory, "${message.id}.ritela").apply { writeText(message.envelope) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.partner-files", file)
        val send = Intent(
            Intent.ACTION_SEND
        ).setType("application/octet-stream").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply {
                clipData =
                    android.content.ClipData.newRawUri("Ritela", uri)
            }
        context.startActivity(Intent.createChooser(send, null))
    }
}
