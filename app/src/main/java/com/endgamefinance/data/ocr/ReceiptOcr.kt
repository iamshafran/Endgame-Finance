package com.endgamefinance.data.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device OCR over a captured receipt image (Milestone 8.2). Uses ML Kit's
 * *bundled* Latin text recognizer — the model ships in the APK and runs fully
 * offline, so no image or text ever leaves the device.
 */
object ReceiptOcr {

    /** Recognizes all text in [uri]. Returns the raw recognized text (blocks
     *  joined newline-wise, top-to-bottom as ML Kit orders them). */
    suspend fun recognize(context: Context, uri: Uri): String =
        suspendCancellableCoroutine { cont ->
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            cont.invokeOnCancellation { recognizer.close() }
            try {
                val image = InputImage.fromFilePath(context, uri)
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        recognizer.close()
                        cont.resume(result.text)
                    }
                    .addOnFailureListener { e ->
                        recognizer.close()
                        cont.resumeWithException(e)
                    }
            } catch (e: Exception) {
                recognizer.close()
                cont.resumeWithException(e)
            }
        }
}
