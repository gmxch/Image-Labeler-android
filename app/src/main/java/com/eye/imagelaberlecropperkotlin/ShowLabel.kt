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
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions

class ShowLabel : AppCompatActivity() {
    private var labeledImage: ImageView? = null
    private var textView: TextView? = null
    private var xmlUri: Uri? = null
    private var targetFileName: String = ""
    private lateinit var rect: Rect

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) startCropActivity(uri, rect)
        else {
            Toast.makeText(this, "Gambar dibatalkan", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private val cropImageLauncher = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val resultUri = result.uriContent
            val cropRect = result.cropRect
            if (resultUri != null && cropRect != null) {
                labeledImage?.setImageURI(resultUri)
                textView?.visibility = View.GONE
                updateXmlFile(cropRect)
            }
        } else {
            Toast.makeText(this, "Crop gagal atau dibatalkan", Toast.LENGTH_SHORT).show()
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
            startCropActivity(foundUri, rect)
        } else {
            Toast.makeText(this, "Pilih gambar '$targetFileName' dari galeri", Toast.LENGTH_LONG).show()
            pickImageLauncher.launch("image/*")
        }
    }

    private fun findImageInMediaStore(fileName: String): Uri? {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val cursor: Cursor? = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection,
            "${MediaStore.Images.Media.DISPLAY_NAME} = ?", arrayOf(fileName), null
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
            cropImageLauncher.launch(
                CropImageContractOptions(
                    uri = imageUri,
                    cropImageOptions = CropImageOptions(
                        cropMenuCropButtonTitle = "CHANGE",
                        activityTitle = "Image Labeled",
                        autoZoomEnabled = false,
                        initialCropWindowRectangle = cropRect
                    )
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Crop Error: ${e.message}")
            Toast.makeText(this, "Gagal membuka crop", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateXmlFile(cropRect: Rect) {
        if (xmlUri == null) return
        try {
            val oldContent = contentResolver.openInputStream(xmlUri!!)?.bufferedReader()?.use { it.readText() } ?: ""
            val startIndex = oldContent.indexOf("<bndbox>")
            val endIndex = oldContent.indexOf("</bndbox>")

            if (startIndex != -1 && endIndex != -1) {
                val newBndbox = "<bndbox>\n<xmin>${cropRect.left}</xmin>\n<ymin>${cropRect.top}</ymin>\n<xmax>${cropRect.right}</xmax>\n<ymax>${cropRect.bottom}</ymax>\n"
                val updatedContent = oldContent.replaceRange(startIndex, endIndex + "</bndbox>".length, newBndbox)
                
                contentResolver.openOutputStream(xmlUri!!)?.use { it.write(updatedContent.toByteArray()) }
                Toast.makeText(this, "XML berhasil diupdate!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menulis XML: ${e.message}")
        }
    }
}