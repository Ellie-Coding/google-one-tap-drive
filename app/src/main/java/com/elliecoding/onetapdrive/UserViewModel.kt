package com.elliecoding.onetapdrive

import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.api.client.http.HttpRequestInitializer
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

private const val TAG = "UserViewModel"

class UserViewModel : ViewModel() {
    interface UserEventCallback {
        fun onDownloadError(cause: Throwable)
        fun onUploadError(cause: Throwable)
    }

    private var downloadCallback: UserEventCallback? = null
    private val downloadExceptionHandler = CoroutineExceptionHandler { _, exception ->
        println("CoroutineExceptionHandler got $exception")
        downloadCallback?.onDownloadError(exception)
    }
    var credentials: HttpRequestInitializer? = null
    val userLoginStatus = MutableLiveData(false)
    val storedData = MutableLiveData<String>()

    fun download(userEventCallback: UserEventCallback, context: Context) {
        downloadCallback = userEventCallback
        viewModelScope.launch(downloadExceptionHandler) {
            val result = DriveStorage.download(credentials!!)
            Log.i(TAG, "download: $result")
            storedData.value = result
        }
    }

    fun upload(context: Context, data: String) {
        viewModelScope.launch {
            DriveStorage.upload(context, credentials!!, data)
        }
    }

    fun inTwoHours(): Date {
        val calendar: Calendar = Calendar.getInstance()
        calendar.time = Date()
        calendar.add(Calendar.HOUR_OF_DAY, 2)
        return calendar.time
    }

    fun delete(context: Context) {
        storedData.value = ""
        viewModelScope.launch { DriveStorage.delete(context, credentials!!) }
    }
}
