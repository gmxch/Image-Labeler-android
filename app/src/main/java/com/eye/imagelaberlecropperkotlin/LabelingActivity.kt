package com.eye.imagelaberlecropperkotlin

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class LabelingActivity : AppCompatActivity() {
    private lateinit var drawingView: DrawingView
    private lateinit var tvCounter: TextView
    private var imageUris: List<Uri> = emptyList()
    private var currentIndex = 0
    private var folderUri: Uri? = null
    private var imgWidth = 0
    private var imgHeight = 0

    private val labelsMap = mutableMapOf<Int, List<BoundingBox>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_labeling)

        drawingView = findViewById(R.id.drawing_view)
        tvCounter = findViewById(R.id.tv_counter)
        
        @Suppress("DEPRECATION")
        imageUris = intent.getParcelableArrayListExtra<Uri>("image_uris") ?: emptyList()
        folderUri = intent.getStringExtra("folder_uri")?.let { Uri.parse(it) }

        findViewById<Button>(R.id.btn_prev).setOnClickListener { 
            if (currentIndex > 0) saveAndMove(-1) 
        }
        findViewById<Button>(R.id.btn_next).setOnClickListener { 
            if (currentIndex < imageUris.size - 1) saveAndMove(1) 
        }
        findViewById<Button>(R.id.btn_save).setOnClickListener { 
            saveLabels()
            Toast.makeText(this, "Label disimpan!", Toast.LENGTH_SHORT).show() 
        }

        drawingView.onBoxDrawn = { rect -> showClassDialog(rect) }
        drawingView.onBoxDeleted = { /* Refresh handled in DrawingView */ }

        if (imageUris.isNotEmpty()) loadImage(0)
    }

    private fun loadImage(index: Int) {
        labelsMap[currentIndex] = drawingView.getBoxes()
        
        currentIndex = index
        tvCounter.text = "Gambar ${currentIndex + 1} / ${imageUris.size}"
        
        contentResolver.openInputStream(imageUris[index])?.use { stream ->
            val bmp = BitmapFactory.decodeStream(stream)
            imgWidth = bmp.width
            imgHeight = bmp.height
            drawingView.setBitmap(bmp)
            
            labelsMap[currentIndex]?.let { savedBoxes ->
                drawingView.setBoxes(savedBoxes)
            }
        }
    }

    private fun showClassDialog(rect: android.graphics.Rect) {
        val input = EditText(this).apply { hint = "person, car, dll" }
        AlertDialog.Builder(this)
            .setTitle("Nama Kelas Objek")
            .setView(input)
            .setPositiveButton("Simpan") { _, _ -> 
                drawingView.addConfirmedBox(rect, input.text.toString().trim().ifBlank { "object" }) 
            }
            .setNegativeButton("Batal") { _, _ -> drawingView.cancelDrawing() }
            .show()
    }

    private fun saveAndMove(direction: Int) {
        saveLabels()
        loadImage(currentIndex + direction)
    }

    private fun saveLabels() {
        val boxes = drawingView.getBoxes()
        val currentImageUri = imageUris[currentIndex]
        
        var imageName = "image"
        contentResolver.query(
            currentImageUri, 
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), 
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                if (nameIndex >= 0) imageName = cursor.getString(nameIndex)
            }
        }
        
        val baseName = imageName.substringBeforeLast(".")
        val txtFileName = "$baseName.txt"
        val xmlFileName = "$baseName.xml"
        
        val treeDocId = DocumentsContract.getTreeDocumentId(folderUri!!)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri!!, treeDocId)
        
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME
        )
        
        var txtDocId: String? = null
        var xmlDocId: String? = null
        
        contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            
            while (cursor.moveToNext()) {
                val docName = cursor.getString(nameIndex)
                val docId = cursor.getString(idIndex)
                if (docName == txtFileName) txtDocId = docId
                if (docName == xmlFileName) xmlDocId = docId
            }
        }
        
        val yoloContent = Utils().generateYoloFormat(boxes, imgWidth, imgHeight)
        val xmlContent = Utils().generateXml("dataset", imageName, "", "Unknown", imgWidth, imgHeight, boxes)
        
        val writeFile = { fileName: String, existingDocId: String?, content: String, mimeType: String ->
            val fileUri = if (existingDocId != null) {
                DocumentsContract.buildDocumentUriUsingTree(folderUri!!, existingDocId)
            } else {
                DocumentsContract.createDocument(contentResolver, folderUri!!, mimeType, fileName)
            }
            
            if (fileUri != null) {
                contentResolver.openOutputStream(fileUri, "wt")?.use { os ->
                    os.write(content.toByteArray())
                }
            }
        }
        
        writeFile(txtFileName, txtDocId, yoloContent, "text/plain")
        writeFile(xmlFileName, xmlDocId, xmlContent, "application/xml")
    }
}