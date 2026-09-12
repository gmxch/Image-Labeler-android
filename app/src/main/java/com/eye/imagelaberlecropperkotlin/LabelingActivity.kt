package com.eye.imagelaberlecropperkotlin

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
import androidx.documentfile.provider.DocumentFile

class LabelingActivity : AppCompatActivity() {
    private lateinit var drawingView: DrawingView
    private lateinit var tvCounter: TextView
    private var imageUris: List<Uri> = emptyList()
    private var currentIndex = 0
    private var folderUri: Uri? = null
    private var imgWidth = 0; private var imgHeight = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_labeling)

        drawingView = findViewById(R.id.drawing_view)
        tvCounter = findViewById(R.id.tv_counter)
        
        imageUris = intent.getParcelableArrayListExtra<Uri>("image_uris") ?: emptyList()
        folderUri = intent.getStringExtra("folder_uri")?.let { Uri.parse(it) }

        findViewById<Button>(R.id.btn_prev).setOnClickListener { if (currentIndex > 0) saveAndMove(-1) }
        findViewById<Button>(R.id.btn_next).setOnClickListener { if (currentIndex < imageUris.size - 1) saveAndMove(1) }
        findViewById<Button>(R.id.btn_save).setOnClickListener { saveLabels(); Toast.makeText(this, "Disimpan!", Toast.LENGTH_SHORT).show() }

        drawingView.onBoxDrawn = { rect -> showClassDialog(rect) }
        drawingView.onBoxDeleted = { /* Refresh handled in DrawingView */ }

        if (imageUris.isNotEmpty()) loadImage(0)
    }

    private fun loadImage(index: Int) {
        currentIndex = index
        tvCounter.text = "Gambar ${currentIndex + 1} / ${imageUris.size}"
        contentResolver.openInputStream(imageUris[index])?.use { stream ->
            val bmp = BitmapFactory.decodeStream(stream)
            imgWidth = bmp.width; imgHeight = bmp.height
            drawingView.setBitmap(bmp)
        }
    }

    private fun showClassDialog(rect: android.graphics.Rect) {
        val input = EditText(this).apply { hint = "person, car, dll" }
        AlertDialog.Builder(this).setTitle("Nama Kelas").setView(input)
            .setPositiveButton("Simpan") { _, _ -> 
                drawingView.addConfirmedBox(rect, input.text.toString().trim().ifBlank { "object" }) 
            }
            .setNegativeButton("Batal") { _, _ -> drawingView.cancelDrawing() }
            .show()
    }

    private fun saveAndMove(direction: Int) { saveLabels(); loadImage(currentIndex + direction) }

    private fun saveLabels() {
        val boxes = drawingView.getBoxes()
        if (boxes.isEmpty()) return
        val uri = imageUris[currentIndex]
        val fileName = DocumentsContract.getDocumentId(uri)?.split(":")?.lastOrNull() ?: "image"
        val baseName = fileName.substringBeforeLast(".")
        val treeDoc = DocumentFile.fromTreeUri(this, folderUri!!) ?: return

        // Simpan YOLO
        val txtFile = treeDoc.findFile("$baseName.txt") ?: treeDoc.createFile("text/plain", "$baseName.txt")
        txtFile?.let { contentResolver.openOutputStream(it.uri)?.use { os -> os.write(Utils().generateYoloFormat(boxes, imgWidth, imgHeight).toByteArray()) } }

        // Simpan XML
        val xmlFile = treeDoc.findFile("$baseName.xml") ?: treeDoc.createFile("application/xml", "$baseName.xml")
        xmlFile?.let { contentResolver.openOutputStream(it.uri)?.use { os -> os.write(Utils().generateXml("dataset", fileName, "", "Unknown", imgWidth, imgHeight, boxes).toByteArray()) } }
    }
}