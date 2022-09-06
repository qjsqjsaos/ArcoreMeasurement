package com.shibuiwilliam.arcoremeasurement

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.ByteArrayOutputStream
import java.io.OutputStream

object Constants {
    const val maxNumMultiplePoints = 6
    const val multipleDistanceTableHeight = 300

    const val arrowViewSize = 45


    // Convert the bitmap to uri
    // Use more than api29
    @RequiresApi(Build.VERSION_CODES.Q)
    fun getImageUriQ(context: Context, bitmap: Bitmap, isSave: Boolean): Uri? {
        val filename = "Size_It_${System.currentTimeMillis()}.jpg"
        var fos: OutputStream?
        var imageUri: Uri?
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
            // 폴더명 선택 하는 곳 원래는 Environment.DIRECTORY_PICTURES 이 값이 여서, Pictures에 저장이 되었음
            put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/SizeIt")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        //use application context to get contentResolver
        val contentResolver = context.contentResolver

        contentResolver.also { resolver ->
            imageUri =
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            fos = imageUri?.let { resolver.openOutputStream(it) }
        }

        fos?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 70, it) }

        contentValues.clear()
        contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)

        if (isSave) {
            //갤러리에 새로운 이미지를 저장하길 원하면 아래 브로드캐스터를 업데이트 해준다.
            contentResolver.update(imageUri!!, contentValues, null, null)
            return null
        }


        return imageUri!!
    }

    // Convert the bitmap to uri
    // Use less than 24api
    @Suppress("DEPRECATION")
    fun getImageUri(context: Context, inImage: Bitmap): Uri? {
        val bytes = ByteArrayOutputStream()
        inImage.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        val path =
            MediaStore.Images.Media.insertImage(context.contentResolver, inImage, "Title", null)

        return Uri.parse(path)
    }
}