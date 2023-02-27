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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.lang.Exception
import java.util.Collections

private const val TAG = "UserViewModel"

/**
 * ViewModel for the state of the user and the data they hold/stored.
 */
class UserViewModel : ViewModel() {

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

    suspend fun download() = coroutineScope {
        downloadRunning.value = true
        val deferred = async(Dispatchers.IO) {
            DriveStorage.download(userLogin.value!!.first!!)
        }
        val result = deferred.await()
        downloadRunning.value = false
        Log.i(TAG, "download: $result")
        storedData.value = result
    }

    suspend fun upload(context: Context, data: String) {
        storedData.value = data
        DriveStorage.upload(context, userLogin.value!!.first!!, data)
    }

    fun delete(context: Context) {
        storedData.value = ""
        userLogin.value?.first?.let {
            viewModelScope.launch { DriveStorage.delete(context, it) }
        }
    }
}
