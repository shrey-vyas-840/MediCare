package com.medicare.app.data.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MediaStorePhotoStorage(private val context: Context) {
    companion object {
        const val TAG = "MEDICARE_MEDIASTORE"
        const val ALBUM_NAME = "MediCare"
    }

    fun saveBitmapToGallery(bitmap: Bitmap, medicineName: String): Pair<String?, String?> {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
        val sanitizedName = medicineName.replace(Regex("[^A-Za-z0-9_]"), "_")
        val fileName = "Medicine_${sanitizedName}_${timeStamp}_${UUID.randomUUID().toString().take(8)}.jpg"

        Log.d("MEDICARE_PHOTO_SAVE", "Attempting to save photo to Gallery Pictures/$ALBUM_NAME/$fileName")

        val resolver = context.contentResolver
        val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val relativePath = "Pictures/$ALBUM_NAME"

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        var imageUri: Uri? = null
        try {
            imageUri = resolver.insert(imageCollection, contentValues)
            if (imageUri == null) {
                Log.e(TAG, "Failed to insert MediaStore entry for $fileName")
                return Pair(null, null)
            }

            resolver.openOutputStream(imageUri)?.use { outputStream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)) throw IOException("JPEG compression failed for $fileName")
            } ?: throw IOException("Failed to open output stream for URI: $imageUri")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
            }

            Log.d(TAG, "Successfully saved photo to MediaStore URI: $imageUri, fileName: $fileName")

            // Verify readability
            resolver.openInputStream(imageUri)?.use { inputStream ->
                val decoded = BitmapFactory.decodeStream(inputStream)
                if (decoded == null) {
                    Log.e("MEDICARE_CAMERA", "Verification failed: Saved image could not be decoded from URI: $imageUri")
                } else {
                    Log.d("MEDICARE_CAMERA", "Verification successful: Saved image decoded successfully (width=${decoded.width}, height=${decoded.height})")
                }
            }

            return Pair(imageUri.toString(), fileName)
        } catch (e: Exception) {
            Log.e("MEDICARE_PHOTO_SAVE", "Error saving bitmap to MediaStore Gallery", e)
            if (imageUri != null) {
                try {
                    resolver.delete(imageUri, null, null)
                } catch (_: Exception) {}
            }
            return Pair(null, null)
        }
    }

    fun loadBitmap(uriString: String): Bitmap? {
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            try {
                val file = File(uriString)
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath)
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }
}
