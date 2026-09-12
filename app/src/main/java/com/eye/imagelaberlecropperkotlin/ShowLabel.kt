package com.eye.imagelaberlecropperkotlin

import android.content.ContentValues.TAG
import android.content.Intent
import android.database.Cursor
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE

class ShowLabel : AppCompatActivity() {
    private var labeledImage: ImageView? = null
    private var textView: TextView? = null
    private var xmlUri: Uri? = null
    private var targetFileName: String = ""
    private lateinit var rect: Rect

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            startCropActivity(uri, rect)
        } else {
            Toast.makeText(this, "Gambar dibatalkan", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_show_label)
        
        labeledImage = findViewById(R.id.imagelabled)
        textView = findViewById(R.id.textView)
        textView?.visibility = View.VISIBLE

        val xmlString = intent.getStringExtra("xmlString") ?: ""
        xmlUri = intent.getParcelableExtra("xmlUri")

        val nameRegex = "<filename>(.*?)</filename>".toRegex()
        targetFileName = nameRegex.find(xmlString)?.groupValues?.get(1) ?: "unknown.jpg"

        val xmin = "<xmin>(.*?)</xmin>".toRegex().find(xmlString)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val ymin = "<ymin>(.*?)</ymin>".toRegex().find(xmlString)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val xmax = "<xmax>(.*?)</xmax>".toRegex().find(xmlString)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val ymax = "<ymax>(.*?)</ymax>".toRegex().find(xmlString)?.groupValues?.get(1)?.toIntOrNull() ?: 0

        rect = Rect(xmin, ymin, xmax, ymax)
        Log.d(TAG, "Target File: $targetFileName, Rect: $rect")

        val foundUri = findImageInMediaStore(targetFileName)

        if (foundUri != null) {
            Log.d(TAG, "Gambar ditemukan otomatis: $foundUri")
            startCropActivity(foundUri, rect)
        } else {
            Log.w(TAG, "Gambar tidak ditemukan otomatis. Meminta user memilih manual.")
            Toast.makeText(this, "Pilih gambar '$targetFileName' dari galeri", Toast.LENGTH_LONG).show()
            pickImageLauncher.launch("image/*")
        }
    }

    private fun findImageInMediaStore(fileName: String): Uri? {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(fileName)

        val cursor: Cursor? = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, selection, selectionArgs, null
        )

        var imageUri: Uri? = null
        cursor?.use {
            if (it.moveToFirst()) {
                val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                imageUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
            }
        }
        return imageUri
    }

    private fun startCropActivity(imageUri: Uri, cropRect: Rect) {
        try {
            CropImage.activity(imageUri)
                .setCropMenuCropButtonTitle("CHANGE")
                .setActivityTitle("Image Labeled")
                .setAutoZoomEnabled(false)
                .setInitialCropWindowRectangle(cropRect)
                .start(this@ShowLabel)
        } catch (e: Exception) {
            Log.e(TAG, "Crop Error: ${e.message}")
            Toast.makeText(this, "Gagal membuka crop: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == CROP_IMAGE_ACTIVITY_REQUEST_CODE && resultCode == RESULT_OK) {
            val result = CropImage.getActivityResult(data) ?: return
            labeledImage?.setImageURI(result.uri)
            textView?.visibility = View.GONE

            val cropRect = result.cropRect
            val newBndbox = "<bndbox>\n<xmin>${cropRect.left}</xmin>\n<ymin>${cropRect.top}</ymin>\n<xmax>${cropRect.right}</xmax>\n<ymax>${cropRect.bottom}</ymax>\n"

            updateXmlFile(newBndbox)
        }
    }

    private fun updateXmlFile(newBndbox: String) {
        if (xmlUri == null) {
            Toast.makeText(this, "URI XML tidak valid, tidak bisa menyimpan", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val oldContent = contentResolver.openInputStream(xmlUri!!)?.bufferedReader()?.use { it.readText() } ?: ""
            
            val startIndex = oldContent.indexOf("<bndbox>")
            val endIndex = oldContent.indexOf("</bndbox>")

            if (startIndex != -1 && endIndex != -1) {
                val updatedContent = oldContent.replaceRange(startIndex, endIndex + "</bndbox>".length, newBndbox)
                
                contentResolver.openOutputStream(xmlUri!!)?.use { outputStream ->
                    outputStream.write(updatedContent.toByteArray())
                }
                Toast.makeText(this, "XML berhasil diupdate!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Format XML tidak memiliki tag <bndbox>", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menulis XML: ${e.message}")
            Toast.makeText(this, "Gagal menyimpan: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}