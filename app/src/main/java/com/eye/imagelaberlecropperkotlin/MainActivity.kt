package com.eye.imagelaberlecropperkotlin

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private var folderUri: Uri? = null
    private val PREFS_NAME = "app_prefs"
    private val KEY_FOLDER_URI = "folder_uri"

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(it, takeFlags)
                folderUri = it
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(KEY_FOLDER_URI, it.toString()).apply()
                Toast.makeText(this, "Folder dipilih", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Error izin folder: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnSelect = findViewById<Button>(R.id.btn_select_folder)
        val btnStart = findViewById<Button>(R.id.btn_start_labeling)

        folderUri = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_FOLDER_URI, null)?.let { Uri.parse(it) }

        btnSelect.setOnClickListener { folderPickerLauncher.launch(null) }
        
        btnStart.setOnClickListener {
            try {
                if (folderUri != null) {
                    val images = scanFolderForImages(folderUri!!)
                    if (images.isNotEmpty()) {
                        val uriList = ArrayList<Uri>(images)
                        startActivity(Intent(this, LabelingActivity::class.java).apply {
                            putParcelableArrayListExtra("image_uris", uriList)
                            putExtra("folder_uri", folderUri.toString())
                        })
                    } else {
                        Toast.makeText(this, "Tidak ada gambar di folder ini", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Pilih folder terlebih dahulu", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "CRASH: ${e.message}", Toast.LENGTH_LONG).show()
                android.util.Log.e("MainActivity", "Error", e)
            }
        }
    }

    private fun scanFolderForImages(treeUri: Uri): List<Uri> {
        val imageUris = mutableListOf<Uri>()
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE)
        
        contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                if (cursor.getString(mimeIndex)?.startsWith("image/") == true) {
                    imageUris.add(DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idIndex)))
                }
            }
        }
        return imageUris
    }
}