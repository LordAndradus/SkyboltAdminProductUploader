package edu.utsa.cs3443.adminproductuploader

import android.content.Intent
import android.content.Intent.ACTION_GET_CONTENT
import android.graphics.Bitmap
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import edu.utsa.cs3443.adminproductuploader.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.UUID
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

class MainActivity : AppCompatActivity()
{
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private var selectedImages = mutableListOf<Uri>()
    private val selectedColors = mutableListOf<Int>()
    private val productStorage = Firebase.storage.reference
    private val firestore = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        val categories = arrayOf("Category", "Fashion", "Electronics", "Accessories", "Furniture", "Medical", "Pets")

        binding.edCategory.adapter = ArrayAdapter<String>(this, androidx.appcompat.R.layout.support_simple_spinner_dropdown_item, categories)

        binding.buttonColorPicker.setOnClickListener {
            ColorPickerDialog.Builder(this)
                .setTitle("Product color")
                .setPositiveButton("Select", object : ColorEnvelopeListener {
                    override fun onColorSelected(envelope: ColorEnvelope?, fromUser: Boolean) {
                        envelope?.let {
                            selectedColors.add(it.color)
                            updateColors()
                        }
                    }
                })
                .setNegativeButton("Cancel") { colorPicker, _ ->
                        colorPicker.dismiss()
                }.show()
        }

        val selectImagesActivityResults = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            result ->
            if(result.resultCode == RESULT_OK)
            {
                val intent = result.data

                //Multiple images selected
                if(intent?.clipData != null)
                {
                    val count = intent.clipData?.itemCount ?: 0
                    (0 until count).forEach {
                        val imageUri = intent.clipData?.getItemAt(it)?.uri
                        imageUri?.let {
                            selectedImages.add(it)
                        }
                    }
                }
                else
                {
                    val imageUri = intent?.data
                    imageUri?.let {
                        selectedImages.add(it)
                    }
                }

                updateImages()
            }
        }

        binding.buttonImagesPicker.setOnClickListener {
            val intent = Intent(ACTION_GET_CONTENT)
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            intent.type = "image/*"
            selectImagesActivityResults.launch(intent)
        }

        binding.buttonClearColors.setOnClickListener{
            selectedColors.clear()
            updateColors()
        }

        binding.buttonClearImages.setOnClickListener{
            selectedImages.clear()
            updateImages()
        }
    }

    private fun updateColors()
    {
        var hexColors = ""

        selectedColors.forEach {
            hexColors = "$hexColors ${Integer.toHexString(it)}"
        }

        binding.tvSelectedColors.text = hexColors
    }

    private fun updateImages()
    {
        binding.tvSelectedImages.text = selectedImages.size.toString()
        if(selectedImages.size.toString().isEmpty() || binding.tvSelectedImages.text.equals("0")) binding.tvSelectedImages.text = ""
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean
    {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean
    {
        if(item.itemId == R.id.saveProduct)
        {
            val productValidation = validateInformation()

            if(!productValidation)
            {
                return false
            }

            saveProduct()
        }

        if(item.itemId == R.id.clearSelection)
        {
            Toast.makeText(this, "Selection has been cleared!", Toast.LENGTH_LONG).show()
            clearSelection()
        }

        return super.onOptionsItemSelected(item)
    }

    private fun saveProduct()
    {
        val name = binding.edName.text.toString().trim()
        val category = binding.edCategory.selectedItem.toString().lowercase(Locale.ROOT)
        val price = binding.edPrice.text.toString().trim()
        val percentage: String;

        if(binding.offerPercentage.text.toString().trim().isNotEmpty())
        {
            var scale : Float = binding.offerPercentage.text.toString().trim().toFloat()
            val magnitude: Float = 10.toDouble().pow(floor(log10(scale.toDouble())) + 1).toFloat()
            scale = (scale / magnitude)
            percentage = scale.toString().trim()
        }
        else
        {
            percentage = ""
        }

        val description = binding.edDescription.text.toString().trim()
        val sizes = getSizesList(binding.edSizes.text.toString().trim())
        val imageByteArrays = getImagesByteArrays()
        val imageURLs = mutableListOf<String>()

        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                showLoading()
            }

            try
            {
                async {
                    imageByteArrays.forEach {
                        val id = UUID.randomUUID().toString()
                        launch {
                            val imageStorage = productStorage.child("products/images/$id")
                            val result = imageStorage.putBytes(it).await()
                            val downloadURL = result.storage.downloadUrl.await().toString()
                            imageURLs.add(downloadURL)
                        }
                    }
                }.await()
            }
            catch(e: java.lang.Exception)
            {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    hideLoading()
                }
            }

            val product = Product(
                UUID.randomUUID().toString(),
                name,
                category,
                price.toFloat(),
                if(percentage.isEmpty()) null else percentage.toFloat(),
                description.ifEmpty { null },
                if(selectedColors.isEmpty()) null else selectedColors,
                sizes,
                imageURLs
            )

            firestore.collection("products").add(product)
                .addOnSuccessListener {
                    hideLoading()

                    clearSelection()

                    Snackbar.make(binding.root, "Product information saved successfully!", Snackbar.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    hideLoading()
                    Log.e("Error", it.message.toString())
                    Snackbar.make(binding.root, it.message.toString(), Snackbar.LENGTH_LONG).show()
                }
        }
    }

    private fun hideLoading()
    {
        binding.progressBar.visibility = View.INVISIBLE
    }

    private fun showLoading()
    {
        binding.progressBar.visibility = View.VISIBLE
    }

    private fun getSizesList(sizeStr: String): List<String>?
    {
        if(sizeStr.isEmpty()) return null

        val sizeList = sizeStr.split(",")
        return sizeList
    }

    private fun getImagesByteArrays() : List<ByteArray>
    {
        val imagesByteArray = mutableListOf<ByteArray>()

        selectedImages.forEach {
            val stream = ByteArrayOutputStream()
            val imageBitmap = MediaStore.Images.Media.getBitmap(contentResolver, it)
            if(imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream))
            {
                imagesByteArray.add(stream.toByteArray())
            }
        }

        return imagesByteArray
    }

    private fun validateInformation(): Boolean
    {
        if(checkED(binding.edName))
        {
            Snackbar.make(binding.root, "Make sure to input a name!", Snackbar.LENGTH_SHORT).show()
            return false
        }

        if(checkED(binding.edPrice))
        {
            Snackbar.make(binding.root, "Make sure to input a price!", Snackbar.LENGTH_SHORT).show()
            return false
        }

        if(binding.edCategory.selectedItem.toString().lowercase(Locale.ROOT) == "category")
        {
            Snackbar.make(binding.root, "Make sure to select a category!", Snackbar.LENGTH_SHORT).show()
            return false
        }

        if(selectedImages.isEmpty())
        {
            Snackbar.make(binding.root, "Make sure to upload at least 1 image!", Snackbar.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun checkED(ed: EditText): Boolean
    {
        return ed.text.toString().trim().isEmpty()
    }

    /*fun getAllEditTexts(view: View): MutableList<EditText>
    {
        val editTextList = mutableListOf<EditText>()
        if (view is EditText)
        {
            editTextList.add(view)
        }
        else if (view is ViewGroup)
        {
            for (i in 0 until view.childCount)
            {
                editTextList.addAll(getAllEditTexts(view.getChildAt(i)))
            }
        }
        return editTextList
    }*/

    private fun clearSelection()
    {
        //Reset items here
        binding.edName.text.clear()
        binding.edPrice.text.clear()
        binding.edCategory.setSelection(0)
        binding.offerPercentage.text.clear()
        binding.edSizes.text.clear()
        binding.edDescription.text.clear()
        selectedImages.clear()
        selectedColors.clear()
    }


}