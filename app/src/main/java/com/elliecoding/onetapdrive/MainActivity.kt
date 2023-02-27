package com.elliecoding.onetapdrive

import android.accounts.Account
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.elliecoding.onetapdrive.databinding.ActivityMainBinding
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.auth.api.identity.SignInCredential
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch

private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {

    private lateinit var oneTapClient: SignInClient
    private lateinit var signInRequest: BeginSignInRequest
    private lateinit var legacyClient: GoogleSignInClient
    private lateinit var binding: ActivityMainBinding

    private val downloadExceptionHandler = CoroutineExceptionHandler { _, exception ->
        println("CoroutineExceptionHandler got $exception")
        onDownloadError(exception)
    }
    private val uploadExceptionHandler = CoroutineExceptionHandler { _, exception ->
        println("CoroutineExceptionHandler got $exception")
        onUploadError(exception)
    }

    private val userViewModel: UserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.delete.setOnClickListener { userViewModel.delete(this) }
        binding.inputButton.setOnClickListener { storeText(binding.inputData.text.toString()) }
        binding.startLegacy.setOnClickListener { startLegacySignIn() }
        binding.startOnetap.setOnClickListener { startOneTapSignIn() }
        binding.signOut.setOnClickListener { signOut() }
        userViewModel.userLogin.observe(this) { status ->
            val isLoggedIn = status.first != null
            binding.textLogin.text = if (isLoggedIn) "Logged in" else "Not logged in"
            binding.textLoginError.text = status.second?.localizedMessage
            binding.startOnetap.isEnabled = !isLoggedIn
            binding.startLegacy.isEnabled = !isLoggedIn
            binding.signOut.isEnabled = isLoggedIn
            binding.inputData.isEnabled = isLoggedIn
            binding.inputButton.isEnabled = isLoggedIn
            binding.delete.isEnabled = isLoggedIn
            if (isLoggedIn) {
                lifecycleScope.launch(downloadExceptionHandler) {
                    userViewModel.download()
                }
            }
        }
        userViewModel.downloadRunning.observe(this) {
            binding.progressDownload.visibility = if (it) View.VISIBLE else View.GONE
        }

        userViewModel.storedData.observe(this) {
            binding.textData.text = it
        }

        prepareOneTapSignIn()
        prepareLegacySignIn()
    }

    private fun signOut() {
        userViewModel.saveLoginFailure()
        oneTapClient.signOut()
    }

    private fun storeText(text: String) {
        lifecycleScope.launch(uploadExceptionHandler) {
            userViewModel.upload(this@MainActivity, text)
        }
    }

    private val legacyLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            processLegacySignIn(it.data)
        }

    private fun startLegacySignIn() {
        legacyLauncher.launch(legacyClient.signInIntent)
    }

    private val oneTapLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            processOneTapSignIn(it.data)
        }

    private fun startOneTapSignIn() {
        oneTapClient.beginSignIn(signInRequest).addOnSuccessListener(this) { result ->
            try {
                oneTapLauncher.launch(
                    IntentSenderRequest.Builder(result.pendingIntent.intentSender).build()
                )
            } catch (e: IntentSender.SendIntentException) {
                Log.e(TAG, "Couldn't start One Tap UI: ${e.localizedMessage}")
                userViewModel.saveLoginFailure(e)
            } catch (e: ActivityNotFoundException) {
                Log.e(TAG, "Couldn't start One Tap UI: ${e.localizedMessage}")
                userViewModel.saveLoginFailure(e)
            }
            Log.d(TAG, "SignIn started")
        }.addOnFailureListener(this) { e ->
            Log.d(TAG, "SignIn failed", e)
            userViewModel.saveLoginFailure(e)
        }
    }

    private fun prepareLegacySignIn() {
        Log.d(TAG, "Requesting legacy SignIn")
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .build()
        legacyClient = GoogleSignIn.getClient(this, signInOptions)
    }

    private fun prepareOneTapSignIn() {
        oneTapClient = Identity.getSignInClient(this)
        signInRequest = BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    // Your server's client ID, not your Android client ID.
                    .setServerClientId(getString(R.string.web_client_id))
                    .setFilterByAuthorizedAccounts(false)
                    .build()
            )
            // Automatically sign in when exactly one credential is retrieved.
            .setAutoSelectEnabled(true)
            .build()
    }

    private fun processLegacySignIn(data: Intent?) {
        if (data == null) return
        GoogleSignIn.getSignedInAccountFromIntent(data)
            .addOnSuccessListener { googleAccount: GoogleSignInAccount ->
                Log.d(TAG, "Signed in as " + googleAccount.email)
                val account = googleAccount.account
                if (account == null) {
                    userViewModel.saveLoginFailure(null)
                } else {
                    userViewModel.saveLoginSuccess(this, account)
                }
            }
            .addOnFailureListener { exception: Exception? ->
                Log.e(TAG, "Unable to sign in.", exception)
                userViewModel.saveLoginFailure(exception)
            }
    }

    private fun processOneTapSignIn(data: Intent?) {
        try {
            val oneTapCredential: SignInCredential =
                oneTapClient.getSignInCredentialFromIntent(data)
            Log.d(TAG, "Signed in as " + oneTapCredential.displayName)
            userViewModel.saveLoginSuccess(this, Account(oneTapCredential.id, packageName))
        } catch (e: ApiException) {
            Log.e(TAG, "Credentials API error", e)
            userViewModel.saveLoginFailure(e)
        }
    }

    private fun onDownloadError(cause: Throwable) {
        if (cause is UserRecoverableAuthIOException) {
            startActivity(cause.intent)
        }
    }

    private fun onUploadError(cause: Throwable) {

    }

}
