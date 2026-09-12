package com.eye.imagelaberlecropperkotlin

import android.Manifest
import android.content.ContentValues.TAG
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class MainActivity : AppCompatActivity() {
    private var userpic: ImageView? = null
    private val STORAGE_REQUEST = 200
    private var PICK_XML_REQUEST_CODE = 23
    private var storagePermission: Array<String>? = null
    private var click: TextView? = null
    private var importXML: TextView? = null
    private var xmlString = "empty"

    private val cropImageLauncher = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val imageUri = result.uriContent
            val cropRect = result.cropRect
            val originalUri = result.originalUri

            if (imageUri != null && cropRect != null) {
                processCroppedImage(imageUri, originalUri, cropRect)
            } else {
                Toast.makeText(this, "Gagal mendapatkan hasil crop", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.e(TAG, "Crop error: ${result.error?.message}")
            Toast.makeText(this, "Crop dibatalkan atau error", Toast.LENGTH_SHORT).show()
        }
    }

    private fun parseXmlToString(uri: Uri) {
        val inputStream = contentResolver.openInputStream(uri) ?: return
        val builderFactory = DocumentBuilderFactory.newInstance()
        val documentBuilder = builderFactory.newDocumentBuilder()
        val document = documentBuilder.parse(inputStream)

        val output = StringBuilder()
        parseNode(document.documentElement, output)
        xmlString = output.toString()
    }

    private fun parseNode(node: Node, output: StringBuilder) {
        when (node.nodeType) {
            Node.ELEMENT_NODE -> {
                output.append("<${node.nodeName}>")
                val childNodes = node.childNodes
                for (i in 0 until childNodes.length) parseNode(childNodes.item(i), output)
                output.append("</${node.nodeName}>")
            }
            Node.TEXT_NODE -> output.append(node.nodeValue)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        click = findViewById(R.id.click)
        importXML = findViewById(R.id.importxml)
        userpic = findViewById(R.id.set_profile_image)

        click?.setOnClickListener { showImagePicDialog() }
        importXML?.setOnClickListener {
            if (!checkStoragePermission()) requestStoragePermission() else showFileXmlDialog()
        }
    }

    private fun showFileXmlDialog() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            setTitle("Pick XML file")
            type = "text/xml"
        }
        startActivityForResult(intent, PICK_XML_REQUEST_CODE)
    }

    private fun showImagePicDialog() {
        AlertDialog.Builder(this)
            .setTitle("Pick Image From")
            .setPositiveButton("Gallery") { _, _ ->
                if (!checkStoragePermission()) requestStoragePermission() else pickFromGallery()
            }.create().show()
    }

    private fun checkStoragePermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
    }

    private fun pickFromGallery() {
        cropImageLauncher.launch(
            CropImageContractOptions(
                uri = null, 
                cropImageOptions = CropImageOptions(cropMenuCropButtonTitle = "Label")
            )
        )
    }

    private fun requestStoragePermission() {
        storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        requestPermissions(storagePermission!!, STORAGE_REQUEST)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_REQUEST && grantResults.isNotEmpty()) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) pickFromGallery()
            else Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_XML_REQUEST_CODE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                parseXmlToString(uri)
                var fileName: String? = null
                val cursor = contentResolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)
                if (cursor != null && cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(MediaStore.Downloads.DISPLAY_NAME)
                    if (nameIndex >= 0) fileName = cursor.getString(nameIndex)
                    cursor.close()
                }
                Toast.makeText(this, "XML file successfully parsed", Toast.LENGTH_SHORT).show()
                
                val intent = Intent(this, ShowLabel::class.java)
                intent.putExtra("xmlString", xmlString)
                intent.putExtra("xmlName", fileName)
                intent.putExtra("xmlUri", uri)
                startActivity(intent)
            }
        }
    }

    private fun processCroppedImage(imageUri: Uri, originalUri: Uri?, cropRect: Rect) {
        val origOpts = BitmapFactory.Options()
        val originalStream = originalUri?.let { contentResolver.openInputStream(it) }
        BitmapFactory.decodeStream(originalStream, null, origOpts)
        originalStream?.close()

        val opts = BitmapFactory.Options()
        opts.inJustDecodeBounds = true
        val imageStream = contentResolver.openInputStream(imageUri)
        BitmapFactory.decodeStream(imageStream, null, opts)
        imageStream?.close()

        val fileName = originalUri?.path ?: imageUri.path
        var fileParentPath: String? = "null"
        var filePath: String? = "null"
        var fileNameOnly: String? = "null"

        if (fileName != null) {
            val file = File(fileName)
            fileParentPath = file.parentFile?.name
            filePath = file.parent
            fileNameOnly = file.name
        }

        val intent = Intent(this, CompleteActivity::class.java).apply {
            putExtra("imageUri", imageUri.toString())
            putExtra("minX", cropRect.left.toFloat())
            putExtra("minY", cropRect.top.toFloat())
            putExtra("maxX", cropRect.right.toFloat())
            putExtra("maxY", cropRect.bottom.toFloat())
            putExtra("height", opts.outHeight)
            putExtra("orgHeight", origOpts.outHeight)
            putExtra("width", opts.outWidth)
            putExtra("orgWidth", origOpts.outWidth)
            putExtra("folder", fileParentPath)
            putExtra("path", filePath)
            putExtra("fileName", fileNameOnly)
        }
        startActivity(intent)
    }
}