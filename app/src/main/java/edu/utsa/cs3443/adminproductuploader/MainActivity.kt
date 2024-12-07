package edu.utsa.cs3443.adminproductuploader

import edu.utsa.cs3443.adminproductuploader.R
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_GET_CONTENT
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
    private var selectedColors = mutableListOf<Int>()
    private val productStorage = Firebase.storage.reference
    private val firestore = Firebase.firestore

    private lateinit var ImagePreviews: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        val categories = arrayOf("Category", "Fashion", "Electronics", "Accessories", "Furniture", "Medical", "Food", "Pets")
        binding.edCategory.adapter = ArrayAdapter<String>(this, androidx.appcompat.R.layout.support_simple_spinner_dropdown_item, categories)

        ImagePreviews = binding.ImagePreviews
        ImagePreviews.layoutManager = GridLayoutManager(this, 3)

        binding.buttonColorPicker.setOnClickListener {
            binding.buttonColorPicker.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            ColorPickerDialog.Builder(this)
                .setTitle("Product color")
                .setPositiveButton("Select", object : ColorEnvelopeListener {
                    override fun onColorSelected(envelope: ColorEnvelope?, fromUser: Boolean) {
                        envelope?.let {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) binding.ColorViewer.performHapticFeedback(HapticFeedbackConstants.CONFIRM) else binding.ColorViewer.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            selectedColors.add(it.color)
                            updateColors()
                        }
                    }
                })
                .setNegativeButton("Cancel") { colorPicker, _ ->
                    binding.buttonColorPicker.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
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
            binding.buttonImagesPicker.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val intent = Intent(ACTION_GET_CONTENT)
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            intent.type = "image/*"
            selectImagesActivityResults.launch(intent)
        }

        binding.buttonClearColors.setOnClickListener{
            binding.buttonClearColors.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            selectedColors.clear()
            updateColors()
        }

        binding.buttonClearImages.setOnClickListener{
            binding.buttonClearImages.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            selectedImages.clear()
            updateImages()
        }
    }

    private fun updateColors()
    {
        binding.ColorViewer.removeAllViews()

        selectedColors.forEach {
            addColorBox(it)
        }

        binding.NumberOfColors.text = selectedColors.size.toString()
        if(selectedColors.size == 0) binding.NumberOfColors.text = ""
    }

    private fun addColorBox(color: Int)
    {
        val colorBox = View(this)

        val layoutParams = LinearLayout.LayoutParams(150, 150)
        layoutParams.marginEnd = 8
        colorBox.layoutParams = layoutParams

        val backgroundColor = ColorDrawable(color)
        val borderDrawable = ContextCompat.getDrawable(this, R.drawable.border)
        val layerDrawable = LayerDrawable(arrayOf(backgroundColor, borderDrawable))

        colorBox.background = layerDrawable


        colorBox.setOnClickListener {
            colorBox.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            ColorPickerDialog.Builder(this)
                .setTitle("Change Color Parameter")
                .setPositiveButton("Change", object : ColorEnvelopeListener {
                    override fun onColorSelected(envelope: ColorEnvelope?, fromUser: Boolean) {
                        envelope?.let {
                            for(i in 0 until binding.ColorViewer.childCount)
                            {
                                if(binding.ColorViewer[i].id == colorBox.id)
                                {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) binding.ColorViewer.performHapticFeedback(HapticFeedbackConstants.CONFIRM) else binding.ColorViewer.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

                                    val backgroundColor = ColorDrawable(it.color)
                                    val borderDrawable = ContextCompat.getDrawable(baseContext, R.drawable.border)
                                    val layerDrawable = LayerDrawable(arrayOf(backgroundColor, borderDrawable))

                                    binding.ColorViewer[i].background = layerDrawable
                                    break
                                }
                            }
                        }
                    }
                })
                .setNegativeButton("Cancel") { colorPicker, _ ->
                    colorBox.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    colorPicker.dismiss()
                }.show()
        }

        colorBox.setOnLongClickListener {
            for(i in 0 until binding.ColorViewer.childCount)
            {
                if(binding.ColorViewer[i].id == colorBox.id)
                {
                    colorBox.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    binding.ColorViewer.removeViewAt(i)
                    selectedColors.removeAt(i)

                    updateColors()
                    return@setOnLongClickListener true
                }
            }

            return@setOnLongClickListener false
        }

        binding.ColorViewer.addView(colorBox)
    }

    private fun updateImages()
    {
        val adapter: GalleryAdapter = GalleryAdapter(selectedImages, this)
        ImagePreviews.adapter = adapter
        adapter.notifyDataSetChanged()

        binding.tvSelectedImages.text = selectedImages.size.toString()
        if(selectedImages.size.toString().isEmpty() || binding.tvSelectedImages.text.equals("0")) binding.tvSelectedImages.text = ""
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean
    {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    /*
     * Feel the rush of Rage! Enjoy every delectable bite of this bloodily enriched cereal.
     * Feel the demon, raise the demon, and unleash the demon!
     * Now comes with a special enemy skill to take the battle to them!
     */

    override fun onOptionsItemSelected(item: MenuItem): Boolean
    {
        if(item.itemId == R.id.saveProduct)
        {
            val productValidation = validateInformation()

            if(!productValidation)
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) binding.buttonClearImages.performHapticFeedback(HapticFeedbackConstants.REJECT) else binding.buttonClearImages.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

                return false
            }

            saveProduct()
        }

        if(item.itemId == R.id.clearSelection)
        {
            Toast.makeText(this, "Selection has been cleared!", Toast.LENGTH_LONG).show()
            clearSelection()
        }

        binding.buttonClearImages.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

        return super.onOptionsItemSelected(item)
    }

    private fun saveProduct(cat: String = binding.edCategory.selectedItem.toString())
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
        val special = binding.SpecialItem.isChecked
        val bestDeal = binding.BestDeal.isChecked
        val bestProduct = binding.BestProduct.isChecked
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
                            val imageStorage = productStorage.child("products/images/$category/$name/$id")
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
                special,
                bestDeal,
                bestProduct,
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
        binding.progressBar.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
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

        if(binding.BestDeal.isChecked && binding.offerPercentage.text.toString().trim().isEmpty())
        {
            Snackbar.make(binding.root, "Best deals need to offer a deal! Dunkhead!!!", Snackbar.LENGTH_SHORT).show()
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
        binding.SpecialItem.isChecked = false
        binding.BestDeal.isChecked = false
        binding.BestProduct.isChecked = false
        selectedImages.clear()
        selectedColors.clear()
        updateColors()
        updateImages()
    }


    companion object
    {
        fun onItemLongClick(position: Int, activity: MainActivity)
        {
            activity.selectedImages.removeAt(position)
            activity.updateImages()
            activity.binding.ImagePreviews.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            Toast.makeText(activity, "Image removed at position: $position", Toast.LENGTH_LONG).show()
        }
    }

    class GalleryAdapter(private val images: List<Uri>, private val context: MainActivity) : RecyclerView.Adapter<GalleryAdapter.ViewHolder>()
    {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view)
        {
            val imageView: ImageView = view.findViewById(R.id.GalleryImage)

            init {
                itemView.setOnClickListener {
                    Log.d("Adapter", adapterPosition.toString())
                    showImageDialog(imageView.drawable)
                }
                itemView.setOnLongClickListener {
                    Log.d("Adapter", "Remove: $adapterPosition")
                    MainActivity.onItemLongClick(adapterPosition, context)
                    return@setOnLongClickListener true
                }
            }
        }

        private fun showImageDialog(image: Drawable)
        {
            val dialog = Dialog(context)
            dialog.setContentView(R.layout.dialog_enlarge_preview)

            val enlargedImage = dialog.findViewById<ImageView>(R.id.enlargedImageView)
            enlargedImage.setImageDrawable(image)

            context.binding.ImagePreviews.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            dialog.show()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryAdapter.ViewHolder
        {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.recyclerview_images, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: GalleryAdapter.ViewHolder, position: Int)
        {
            val uri = images[position]
            holder.imageView.setImageURI(uri)

            val displayMetrics: DisplayMetrics = context.resources.displayMetrics
            val width = displayMetrics.widthPixels
            val padding = 25
            val totalPadding = padding * 2
            val finalWidth = (width - totalPadding) / 3

            //val bitmap = loadScaledDownImage(uri, context, finalWidth
            //val aspect = (bitmap?.height?.toFloat() ?: 0f) / bitmap?.width?.toFloat()!!

            val bitmap = MediaStore.Images.Media.getBitmap(holder.imageView.context.contentResolver, uri)
            val aspect = bitmap.height.toFloat() / bitmap.width.toFloat()

            val height = ((finalWidth * aspect) - totalPadding).toInt()

            val layoutParams = holder.imageView.layoutParams as ViewGroup.MarginLayoutParams

            layoutParams.topMargin = 10
            layoutParams.bottomMargin = 10

            holder.imageView.layoutParams.width = finalWidth
            holder.imageView.layoutParams.height = height
            holder.imageView.layoutParams = layoutParams
        }

        override fun getItemCount(): Int = images.size

        private fun loadScaledDownImage(uri: Uri, context: Context, targetWidth: Int): Bitmap?
        {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true

            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            options.inSampleSize = calculateInSampleSize(options, targetWidth, targetWidth)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.RGB_565

            return context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        }

        private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int
        {
            val (height: Int, width: Int) = options.outHeight to options.outWidth
            var inSampleSize = 1

            if (height > reqHeight || width > reqWidth)
            {
                val halfHeight: Int = height / 2
                val halfWidth: Int = width / 2

                while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth)
                {
                    inSampleSize *= 2
                }
            }
            return inSampleSize
        }

    }
}