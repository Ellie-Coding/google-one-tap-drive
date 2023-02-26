package com.elliecoding.onetapdrive

import android.accounts.Account
import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
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
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.services.drive.DriveScopes
import java.util.Collections

private const val REQUEST_CODE_ONE_TAP = 0
private const val REQUEST_CODE_LEGACY = 1

class MainActivity : AppCompatActivity(), UserViewModel.UserEventCallback {

    private lateinit var oneTapClient: SignInClient
    private lateinit var signInRequest: BeginSignInRequest
    private lateinit var legacyClient: GoogleSignInClient
    private lateinit var binding: ActivityMainBinding
    private val userViewModel: UserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.delete.setOnClickListener { userViewModel.delete(this) }
        binding.inputButton.setOnClickListener { storeText(binding.inputData.text.toString()) }
        binding.startLegacy.setOnClickListener { startLegacySignIn() }
        binding.startOnetap.setOnClickListener { startOneTapSignIn() }
        userViewModel.userLoginStatus.observe(this) { isLoggedIn ->
            binding.textLogin.text = if (isLoggedIn) "Logged in" else "Not logged in"
            binding.startOnetap.isEnabled = !isLoggedIn
            binding.startLegacy.isEnabled = !isLoggedIn
            binding.inputData.isEnabled = isLoggedIn
            binding.inputButton.isEnabled = isLoggedIn
            binding.delete.isEnabled = isLoggedIn
            if (isLoggedIn) {
                try {
                    userViewModel.download(this)
                } catch (e: UserRecoverableAuthIOException) {
                    startActivityForResult(e.intent, 9);
                }
            }
        }

        userViewModel.storedData.observe(this) {
            binding.textData.text = it
        }

        prepareOneTapSignIn()
        prepareLegacySignIn()
    }

    private fun startLegacySignIn() {
        startActivityForResult(legacyClient.signInIntent, REQUEST_CODE_LEGACY)
    }

    private fun storeText(text: String) {
        binding.textData.text = text
        userViewModel.upload(this, text)
    }

    private fun processLegacySignIn(data: Intent?) {
        if (data == null) return
        GoogleSignIn.getSignedInAccountFromIntent(data)
            .addOnSuccessListener { googleAccount: GoogleSignInAccount ->
                Log.d(TAG, "Signed in as " + googleAccount.email)
                makeDriveCredential(googleAccount)
                userViewModel.userLoginStatus.value = true

            }
            .addOnFailureListener { exception: Exception? ->
                Log.e(TAG, "Unable to sign in.", exception)
                userViewModel.userLoginStatus.value = false
            }
    }

    private fun makeDriveCredential(googleAccount: GoogleSignInAccount) {
        // Use the authenticated account to sign in to the Drive service.
        val credential: GoogleAccountCredential = GoogleAccountCredential.usingOAuth2(
            this, Collections.singleton(DriveScopes.DRIVE_APPDATA)
        )
        credential.selectedAccount = googleAccount.account
        userViewModel.credentials = credential
    }

    private fun makeDriveCredential(oneTapSignIn: SignInCredential) {
        // Use the authenticated account to sign in to the Drive service.
        val credential: GoogleAccountCredential = GoogleAccountCredential.usingOAuth2(
            this, Collections.singleton(DriveScopes.DRIVE_APPDATA)
        )
        credential.selectedAccount = Account(oneTapSignIn.id, packageName)
        userViewModel.credentials = credential
    }

    private fun processOneTapSignIn(data: Intent) {
        try {
            val oneTapCredential: SignInCredential =
                oneTapClient.getSignInCredentialFromIntent(data)
            Log.d(TAG, "Signed in as " + oneTapCredential.displayName)
            makeDriveCredential(oneTapCredential)
            userViewModel.userLoginStatus.value = true
        } catch (e: ApiException) {
            Log.e(TAG, "Credentials API error", e)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_ONE_TAP -> {
                processOneTapSignIn(data!!)
            }

            REQUEST_CODE_LEGACY -> {
                processLegacySignIn(data)
            }

            9 -> {
                Log.d(TAG, resultCode.toString())
            }
        }
    }

    private fun startOneTapSignIn() {
        oneTapClient.beginSignIn(signInRequest).addOnSuccessListener(this) { result ->
            try {
                // signInLauncher.launch(
                // IntentSenderRequest.Builder(result.pendingIntent.intentSender).build()
                //)
                try {
                    startIntentSenderForResult(
                        result.pendingIntent.intentSender, REQUEST_CODE_ONE_TAP,
                        null, 0, 0, 0, null
                    )
                } catch (e: IntentSender.SendIntentException) {
                    Log.e(TAG, "Couldn't start One Tap UI: ${e.localizedMessage}")
                }
                Log.d(TAG, "SignIn started")
            } catch (e: IntentSender.SendIntentException) {
                Log.e(TAG, "Couldn't start One Tap UI: ${e.localizedMessage}")
            }
        }.addOnFailureListener(this) { e ->
            // No saved credentials found. Launch the One Tap sign-up flow, or
            // do nothing and continue presenting the signed-out UI.
            Log.d(TAG, "SignIn failed", e)
            userViewModel.userLoginStatus.value = false
        }
    }

    private fun prepareLegacySignIn() {
        Log.d(TAG, "Requesting sign-in")
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
                    // Only show accounts previously used to sign in.
                    .setFilterByAuthorizedAccounts(true)
                    .build()
            )
            // Automatically sign in when exactly one credential is retrieved.
            .setAutoSelectEnabled(true)
            .build()
    }

    override fun onDownloadError(cause: Throwable) {
        if (cause is UserRecoverableAuthIOException) {
            startActivityForResult(cause.intent!!, 888)
        }
    }

    override fun onUploadError(cause: Throwable) {
    }

}

private const val TAG = "MainActivity"
