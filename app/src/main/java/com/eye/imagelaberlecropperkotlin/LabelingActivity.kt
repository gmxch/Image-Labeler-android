package com.eye.imagelaberlecropperkotlin

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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
        
        imageUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("image_uris", Uri::class.java) ?: emptyList()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>("image_uris") ?: emptyList()
        }
        
        folderUri = intent.getStringExtra("folder_uri")?.let { Uri.parse(it) }

        if (folderUri == null || imageUris.isEmpty()) {
            Toast.makeText(this, "Data tidak valid atau folder kosong", Toast.LENGTH_LONG).show()
            finish()
            return
        }

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

        loadImage(0)
    }

    private fun loadImage(index: Int) {
        // Simpan kotak gambar saat ini ke Map sebelum pindah
        labelsMap[currentIndex] = drawingView.getBoxes()
        currentIndex = index
        tvCounter.text = "Gambar ${currentIndex + 1} / ${imageUris.size}"
        
        contentResolver.openInputStream(imageUris[index])?.use { stream ->
            val bmp = BitmapFactory.decodeStream(stream)
            
            if (bmp == null) {
                Toast.makeText(this, "Gagal memuat gambar (format tidak didukung/rusak)", Toast.LENGTH_SHORT).show()
                return@use
            }
            
            imgWidth = bmp.width
            imgHeight = bmp.height
            drawingView.setBitmap(bmp)
            
            labelsMap[currentIndex]?.let { savedBoxes ->
                drawingView.setBoxes(savedBoxes)
            }
        }
    }

    private fun showClassDialog(rect: android.graphics.Rect) {
        val input = EditText(this).apply {
            hint = "person, car, dll"
            maxLines = 1
            imeOptions = EditorInfo.IME_ACTION_DONE 
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("Nama Kelas Objek")
            .setView(input)
            .setPositiveButton("Simpan") { _, _ -> 
                drawingView.addConfirmedBox(rect, input.text.toString().trim().ifBlank { "object" }) 
            }
            .setNegativeButton("Batal") { _, _ -> drawingView.cancelDrawing() }
            .create()
            
        input.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE || 
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                true
            } else {
                false
            }
        }
        
        dialog.show()
        
        input.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
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