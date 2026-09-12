package com.eye.imagelaberlecropperkotlin

import android.content.ContentValues.TAG
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.squareup.picasso.Picasso
import java.io.OutputStream

class CompleteActivity : AppCompatActivity() {
    private var dataString: String = ""
    private var userImage: ImageView? = null
    private var exportBtn: ConstraintLayout? = null
    private var addYOLO: ConstraintLayout? = null
    private lateinit var autoCompleteTextView: AutoCompleteTextView
    private lateinit var autoCompleteName: AutoCompleteTextView
    
    private val SAVE_FILE_REQUEST_CODE = 42
    private val PICK_YOLO_REQUEST_CODE = 23

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_complete)
        
        userImage = findViewById(R.id.image_final)
        exportBtn = findViewById(R.id.export_btn)
        addYOLO = findViewById(R.id.addYOLO)

        val intent = intent
        val imageUri = intent.getStringExtra("imageUri")

        // ✅ PERBAIKAN 1: Picasso.get() lebih stabil dan tidak deprecated
        Picasso.get().load(imageUri).into(userImage)

        val minX = intent.getFloatExtra("minX", 0F)
        val minY = intent.getFloatExtra("minY", 0F)
        val maxX = intent.getFloatExtra("maxX", 0F)
        val maxY = intent.getFloatExtra("maxY", 0F)
        val height = intent.getIntExtra("height", 0)
        val width = intent.getIntExtra("width", 0)
        val origWidth = intent.getIntExtra("orgWidth", 0)
        val origHeight = intent.getIntExtra("orgHeight", 0)
        val fileParentPath = intent.getStringExtra("folder")
        val filePath = intent.getStringExtra("path")
        val fileName = intent.getStringExtra("fileName")

        autoCompleteTextView = findViewById(R.id.autoComplete)
        autoCompleteName = findViewById(R.id.autoCompleteName)

        val utils = Utils()
        autoCompleteTextView.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            val selectedItem = parent.adapter.getItem(position) as String
            Log.d(TAG, "onCreate: Object Name Is $selectedItem")
            Toast.makeText(this, "Selected $selectedItem", Toast.LENGTH_SHORT).show()

            when (position) {
                0 -> addYOLO?.visibility = View.GONE
                1 -> addYOLO?.visibility = View.VISIBLE
            }

            exportBtn?.setOnClickListener {
                when (position) {
                    0 -> {
                        addYOLO?.visibility = View.GONE
                        dataString = utils.generateXml(
                            folder = fileParentPath.toString(),
                            filename = fileName.toString(),
                            path = filePath.toString(),
                            database = "Unknown",
                            width = origWidth,
                            height = origHeight,
                            name = autoCompleteName.text.toString(),
                            xmin = minX, ymin = minY, xmax = maxX, ymax = maxY
                        )
                        Log.d(TAG, "onCreate: xmlFile $dataString")
                        val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "text/xml"
                            putExtra(Intent.EXTRA_TITLE, "labels.xml")
                        }
                        startActivityForResult(saveIntent, SAVE_FILE_REQUEST_CODE)
                    }
                    1 -> {
                        dataString = utils.generateYoloFormat(
                            className = autoCompleteName.text.toString(),
                            minX = minX, minY = minY, maxX = maxX, maxY = maxY,
                            imgWidth = origWidth, imgHeight = origHeight
                        )
                        Log.d(TAG, "onCreate: yoloFile is $dataString")
                        val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TITLE, "labels.txt")
                        }
                        startActivityForResult(saveIntent, SAVE_FILE_REQUEST_CODE)
                    }
                    else -> {
                        Toast.makeText(this, "No Option is Selected!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        addYOLO?.setOnClickListener {
            dataString = utils.generateYoloFormat(
                className = autoCompleteName.text.toString(),
                minX = minX, minY = minY, maxX = maxX, maxY = maxY,
                imgWidth = origWidth, imgHeight = origHeight
            )
            Log.d(TAG, "onCreate: yoloFile is $dataString")
            val pickIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                title = "Pick YOLO file"
                type = "text/plain"
            }
            startActivityForResult(pickIntent, PICK_YOLO_REQUEST_CODE)
        }

        findViewById<TextView>(R.id.coordText).text = """
            Crop Cords: 
            (minX : $minX, minY : $minY)
            (maxX : $maxX, maxY : $maxY)
            Bounding Box Size:
            (width : $width, height : $height)
        """.trimIndent()
    }

    override fun onResume() {
        super.onResume()
        val options = resources.getStringArray(R.array.exportFormat)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, options)
        autoCompleteTextView.setAdapter(adapter)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult called: $requestCode")

        // ✅ PERBAIKAN 2: Handle YOLO file via URI, BUKAN path string
        if (requestCode == PICK_YOLO_REQUEST_CODE && resultCode == RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                updateYoloFileViaUri(uri, dataString)
            } else {
                Toast.makeText(this, "Gagal mendapatkan URI file", Toast.LENGTH_SHORT).show()
            }
        }

        // Handle Save File (XML / TXT baru)
        if (requestCode == SAVE_FILE_REQUEST_CODE && resultCode == RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                try {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(dataString.toByteArray())
                    }
                    Toast.makeText(this, "File berhasil disimpan", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e(TAG, "Save error: ${e.message}")
                    Toast.makeText(this, "Gagal menyimpan: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ✅ PERBAIKAN 3: Fungsi pengganti writeOnYOLOFile yang 100% aman untuk Android 11+
    private fun updateYoloFileViaUri(uri: Uri, newData: String) {
        try {
            // 1. Baca isi file lama menggunakan ContentResolver (Scoped Storage Safe)
            val oldContent = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            
            // 2. Gabungkan dengan data baru
            val updatedContent = if (oldContent.isNotBlank() && !oldContent.endsWith("\n")) {
                "$oldContent\n$newData"
            } else {
                "$oldContent$newData"
            }

            // 3. Tulis kembali ke URI yang sama (mode "wt" = write truncate)
            contentResolver.openOutputStream(uri, "wt")?.use { outputStream: OutputStream ->
                outputStream.write(updatedContent.toByteArray())
            }
            Toast.makeText(this, "YOLO file berhasil diupdate", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Update YOLO error: ${e.message}")
            Toast.makeText(this, "Gagal update YOLO: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}