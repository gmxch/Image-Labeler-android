package com.eye.imagelaberlecropperkotlin

import android.content.Context
import android.content.Intent
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
import androidx.documentfile.provider.DocumentFile
import java.io.File

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
        try {
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
            }

            drawingView.onBoxDrawn = { rect -> showClassDialog(rect) }
            drawingView.onBoxDeleted = { }

            loadImage(0)
        } catch (e: Exception) {
            Toast.makeText(this, "CRASH di Labeling: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadImage(index: Int) {
        try {
            labelsMap[currentIndex] = drawingView.getBoxes()
            currentIndex = index
            tvCounter.text = "Gambar ${currentIndex + 1} / ${imageUris.size}"
            
            contentResolver.openInputStream(imageUris[index])?.use { stream ->
                val bmp = BitmapFactory.decodeStream(stream)
                if (bmp == null) {
                    Toast.makeText(this, "Gagal memuat gambar", Toast.LENGTH_SHORT).show()
                    return@use
                }
                imgWidth = bmp.width
                imgHeight = bmp.height
                drawingView.setBitmap(bmp)
                
                labelsMap[currentIndex]?.let { savedBoxes ->
                    drawingView.setBoxes(savedBoxes)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "CRASH load gambar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showClassDialog(rect: android.graphics.Rect) {
        try {
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
        } catch (e: Exception) {
            Toast.makeText(this, "CRASH Dialog: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveAndMove(direction: Int) {
        saveLabels()
        loadImage(currentIndex + direction)
    }

    private fun saveLabels() {
        try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(folderUri!!, takeFlags)

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
            
            val baseName = imageName.substringBeforeLast(".").replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val txtFileName = "$baseName.txt"
            val xmlFileName = "$baseName.xml"
            
            val treeDoc = DocumentFile.fromTreeUri(this, folderUri!!) 
                ?: throw Exception("Gagal mengakses folder. Izin mungkin dicabut.")

            val saveFile = { fileName: String, content: String, mimeType: String ->
                val oldFile = treeDoc.findFile(fileName)
                if (oldFile != null && oldFile.exists()) {
                    oldFile.delete()
                }
                
                val newFile = treeDoc.createFile(mimeType, fileName)
                if (newFile != null) {
                    contentResolver.openOutputStream(newFile.uri, "wt")?.use { os ->
                        os.write(content.toByteArray())
                    }
                } else {
                    throw Exception("Gagal membuat file: $fileName")
                }
            }

            val yoloContent = Utils().generateYoloFormat(boxes, imgWidth, imgHeight)
            val xmlContent = Utils().generateXml("dataset", imageName, "", "Unknown", imgWidth, imgHeight, boxes)
            
            saveFile(txtFileName, yoloContent, "text/plain")
            saveFile(xmlFileName, xmlContent, "application/xml")
            
            Toast.makeText(this, "Label berhasil disimpan!", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            val log = android.util.Log.getStackTraceString(e)
            try {
                val crashFile = File(getExternalFilesDir(null), "crash_log.txt")
                crashFile.writeText("=== SAVE CRASH LOG ===\n$log")
            } catch (ex: Exception) { }
            
            Toast.makeText(this, "Gagal simpan: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}