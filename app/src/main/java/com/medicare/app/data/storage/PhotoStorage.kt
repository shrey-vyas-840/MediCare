package com.medicare.app.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class PhotoStorage(private val context: Context) {
    private val photosDir: File
        get() = File(context.filesDir, "medication_photos").apply {
            if (!exists()) mkdirs()
        }

    fun saveBitmap(bitmap: Bitmap, prefix: String = "photo"): String {
        val fileName = "${prefix}_${UUID.randomUUID()}.jpg"
        val file = File(photosDir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return file.absolutePath
    }

    fun loadBitmap(path: String): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    fun deletePhoto(path: String): Boolean {
        val file = File(path)
        if (file.exists()) {
            return file.delete()
        }
        return false
    }
}
