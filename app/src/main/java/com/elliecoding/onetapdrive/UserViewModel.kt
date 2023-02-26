package com.elliecoding.onetapdrive

import android.accounts.Account
import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.lang.Exception
import java.util.Collections

private const val TAG = "UserViewModel"

class UserViewModel : ViewModel() {

    interface UserEventCallback {
        fun onDownloadError(cause: Throwable)
        fun onUploadError(cause: Throwable)
    }

    private var eventCallback: UserEventCallback? = null
    private val downloadExceptionHandler = CoroutineExceptionHandler { _, exception ->
        println("CoroutineExceptionHandler got $exception")
        eventCallback?.onDownloadError(exception)
    }
    private val uploadExceptionHandler = CoroutineExceptionHandler { _, exception ->
        println("CoroutineExceptionHandler got $exception")
        eventCallback?.onUploadError(exception)
    }

    // Pair(Account, Error)
    val userLogin = MutableLiveData(Pair<GoogleAccountCredential?, Exception?>(null, null))
    val storedData = MutableLiveData<String?>()
    val downloadRunning = MutableLiveData(false)

    fun saveLoginSuccess(context: Context, account: Account) {
        // Use the authenticated account to sign in to the Drive service.
        userLogin.value = Pair(
            GoogleAccountCredential.usingOAuth2(
                context, Collections.singleton(DriveScopes.DRIVE_APPDATA)
            ).setSelectedAccount(account), null
        )
    }

    fun saveLoginFailure(exception: Exception? = null) {
        userLogin.value = Pair(null, exception)
    }

    fun download() {
        viewModelScope.launch(downloadExceptionHandler) {
            downloadRunning.value = true
            val deferred = async(Dispatchers.IO) {
                DriveStorage.download(userLogin.value!!.first!!)
            }
            val result = deferred.await()
            downloadRunning.value = false
            Log.i(TAG, "download: $result")
            storedData.value = result
        }
    }

    fun upload(context: Context, data: String) {
        viewModelScope.launch(uploadExceptionHandler) {
            DriveStorage.upload(context, userLogin.value!!.first!!, data)
        }
    }

    fun delete(context: Context) {
        storedData.value = ""
        userLogin.value?.first?.let {
            viewModelScope.launch { DriveStorage.delete(context, it) }
        }
    }
}
