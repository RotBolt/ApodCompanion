package io.kaendagger.apodcompanion.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Environment
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import io.kaendagger.apodcompanion.checkPermissions
import io.kaendagger.apodcompanion.data.APODRepository
import io.kaendagger.apodcompanion.data.Result
import io.kaendagger.apodcompanion.data.model.Apod
import io.kaendagger.apodcompanion.data.model.ApodOffline
import io.kaendagger.apodcompanion.permissions
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Exception
import java.lang.NullPointerException
import javax.inject.Inject

class APODViewModel @Inject
constructor(
    private val context: Context,
    private val picasso: Picasso,
    private val apodRepository: APODRepository
) : ViewModel() {

    private val TAG = "APODViewModel"
    private val targetHolder = TargetHolder()

    private var pastApods: List<ApodOffline>? = null
    private var todayApod: Apod? = null

    var padeIdx = -1

    suspend fun getTodayApod(): Deferred<Result<Apod>> {
        return viewModelScope.async {
            todayApod?.let { return@async Result.Success(it) }

            val response = apodRepository.getTodayAPOD()
            if (response.isSuccessful) {
                val apod = response.body()
                if (apod != null) {
                    if (context.checkPermissions(permissions))
                        downloadImage(apod)
                    todayApod = apod
                    Result.Success(apod)
                } else {
                    Result.Error(NullPointerException("Received Null"))
                }
            } else {
                Result.Error(IOException("Error fetching data"))
            }
        }
    }

    suspend fun getPastAPODs(): Deferred<List<ApodOffline>> = viewModelScope.async(Dispatchers.IO) {
        pastApods?.let { return@async it }
        val list = apodRepository.getPastAPODs()
        pastApods = list
        list
    }

    private suspend fun downloadImage(apod: Apod) {

        val root = Environment.getExternalStorageDirectory().toString()
        val directory = File("$root/apods")
        if (!directory.exists()) {
            directory.mkdir()
        }

        val target = object : Target {
            override fun onPrepareLoad(placeHolderDrawable: Drawable?) {

            }

            override fun onBitmapFailed(e: Exception?, errorDrawable: Drawable?) {
                Log.e(TAG, "bitmap failed ${e?.message}")
            }

            override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
                // file download to disk
                viewModelScope.launch {
                    val file = File("$directory/${apod.date}.jpeg")
                    file.createNewFile()
                    val oStream = FileOutputStream(file)
                    bitmap?.compress(Bitmap.CompressFormat.JPEG, 80, oStream)
                    oStream.apply {
                        flush()
                        close()
                    }

                    val apodOffline = ApodOffline(
                        apod.date,
                        apod.explanation,
                        apod.title,
                        "$directory/${apod.date}.jpeg"
                    )
                    apodRepository.insertApodOffline(apodOffline)
                }
            }
        }
        targetHolder.holdTarget(target)
        Log.i(TAG, "downloaded image $directory, apod ${apod.date}")
        picasso.load(apod.url).into(targetHolder.target)
    }

    private class TargetHolder {
        lateinit var target: Target
        fun holdTarget(t: Target) {
            target = t
        }
    }
}