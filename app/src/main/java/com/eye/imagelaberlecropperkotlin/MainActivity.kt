package com.eye.imagelaberlecropperkotlin

import android.Manifest
import android.content.ContentValues.TAG
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.canhub.cropper.CropImage
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

    // --- XML Parser ---
    private fun parseXmlToString(uri: Uri) {
        val inputStream = contentResolver.openInputStream(uri) ?: return
        val builderFactory = DocumentBuilderFactory.newInstance()
        val documentBuilder = builderFactory.newDocumentBuilder()
        val document = documentBuilder.parse(inputStream)

        val output = StringBuilder()
        val rootNode = document.documentElement
        parseNode(rootNode, output)

        xmlString = output.toString()
        Log.d(TAG, "parseXmlToString: $xmlString")
    }

    private fun parseNode(node: Node, output: StringBuilder) {
        when (node.nodeType) {
            Node.ELEMENT_NODE -> {
                output.append("<${node.nodeName}>")
                val childNodes = node.childNodes
                for (i in 0 until childNodes.length) {
                    parseNode(childNodes.item(i), output)
                }
                output.append("</${node.nodeName}>")
            }
            Node.TEXT_NODE -> {
                output.append(node.nodeValue)
            }
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
            if (!checkStoragePermission()) {
                requestStoragePermission()
            } else {
                showFileXmlDialog()
            }
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
            .setPositiveButton("Gallery") { dialog, _ ->
                if (!checkStoragePermission()) {
                    requestStoragePermission()
                } else {
                    pickFromGallery()
                }
            }
            .create().show()
    }

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun pickFromGallery() {
        CropImage.activity()
            .setCropMenuCropButtonTitle("Label")
            .start(this@MainActivity)
    }

    private fun requestStoragePermission() {
        storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        requestPermissions(storagePermission!!, STORAGE_REQUEST)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_REQUEST && grantResults.isNotEmpty()) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pickFromGallery()
            } else {
                Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getFileFromUri(uri: Uri): String? {
        return uri.path 
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "requestCode: $requestCode, resultCode: $resultCode")

        // --- Handle XML File Selected ---
        if (requestCode == PICK_XML_REQUEST_CODE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                parseXmlToString(uri)
                
                var fileName: String? = null
                var pathWithoutName: String? = null
                
                val cursor = contentResolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.DATA), null, null, null)
                if (cursor != null && cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(MediaStore.Downloads.DISPLAY_NAME)
                    if (nameIndex >= 0) fileName = cursor.getString(nameIndex)
                    cursor.close()
                }

                Toast.makeText(this, "XML file successfully parsed", Toast.LENGTH_SHORT).show()
                
                val intent = Intent(this, ShowLabel::class.java)
                intent.putExtra("xmlString", xmlString)
                intent.putExtra("xmlName", fileName)
                intent.putExtra("xmlPath", pathWithoutName)
                intent.putExtra("xmlUri", uri)
                startActivity(intent)
            }
        }

        // --- Handle Crop Image Result ---
        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE && data != null) {
            val result: CropImage.ActivityResult = CropImage.getActivityResult(data) ?: return
            val originalUri = result.originalUri
            
            if (resultCode == RESULT_OK) {
                val imageUri = result.uri
                val cropname = imageUri.lastPathSegment

                val origOpts = BitmapFactory.Options()
                val originalStream = originalUri?.let { contentResolver.openInputStream(it) }
                BitmapFactory.decodeStream(originalStream, null, origOpts)
                originalStream?.close()
                val origWidth = origOpts.outWidth
                val origHeight = origOpts.outHeight

                val opts = BitmapFactory.Options()
                opts.inJustDecodeBounds = true
                val imageStream = contentResolver.openInputStream(imageUri)
                BitmapFactory.decodeStream(imageStream, null, opts)
                imageStream?.close()
                val width = opts.outWidth
                val height = opts.outHeight

                val fileName = getFileFromUri(originalUri)
                var fileParentPath: String? = "null"
                var filePath: String? = "null"
                var fileNameOnly: String? = "null"

                if (fileName != null) {
                    val file = File(fileName)
                    fileParentPath = file.parentFile?.name
                    filePath = file.parent
                    fileNameOnly = file.name
                } else {
                    val dummyFile = File("Not_Saved_No_Address_Available")
                    Toast.makeText(this, "Image not saved locally, using rect data only", Toast.LENGTH_LONG).show()
                    fileParentPath = dummyFile.parentFile?.name
                    filePath = dummyFile.parent
                    fileNameOnly = dummyFile.name
                }

                val intent = Intent(this, CompleteActivity::class.java)
                intent.putExtra("imageUri", imageUri.toString())
                
                val cropRect = result.cropRect
                intent.putExtra("minX", cropRect.left.toFloat())
                intent.putExtra("minY", cropRect.top.toFloat())
                intent.putExtra("maxX", cropRect.right.toFloat())
                intent.putExtra("maxY", cropRect.bottom.toFloat())
                intent.putExtra("height", height)
                intent.putExtra("orgHeight", origHeight)
                intent.putExtra("width", width)
                intent.putExtra("orgWidth", origWidth)
                intent.putExtra("folder", fileParentPath)
                intent.putExtra("path", filePath)
                intent.putExtra("fileName", fileNameOnly)

                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "StartActivity error: ${e.message}")
                }
            }
        }
    }
}