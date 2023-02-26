package com.elliecoding.onetapdrive

import android.accounts.Account
import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import android.util.Log
import android.view.Menu
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.elliecoding.onetapdrive.databinding.ActivityMainBinding
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.auth.api.identity.SignInCredential
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.material.navigation.NavigationView
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.services.drive.DriveScopes
import java.util.Collections


private const val REQUEST_CODE_ONE_TAP = 0
private const val REQUEST_CODE_LEGACY = 111

class MainActivity : AppCompatActivity(), UserViewModel.UserEventCallback {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private val userViewModel: UserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        binding.appBarMain.fab.setOnClickListener { userViewModel.delete(this) }
        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        oneTapSignIn()
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
        }
    }

    private lateinit var oneTapClient: SignInClient
    private lateinit var signInRequest: BeginSignInRequest

    private fun legacySignIn() {
        Log.d(TAG, "Requesting sign-in")
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .build()
        val client = GoogleSignIn.getClient(this, signInOptions)

        // The result of the sign-in Intent is handled in onActivityResult.
        startActivityForResult(client.signInIntent, REQUEST_CODE_LEGACY)
    }

    private fun oneTapSignIn() {
        oneTapClient = Identity.getSignInClient(this)
        signInRequest = BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    // Your server's client ID, not your Android client ID.
                    .setServerClientId(getString(R.string.web_client_id))
                    // Only show accounts previously used to sign in.
                    .setFilterByAuthorizedAccounts(false)
                    .build()
            )
            // Automatically sign in when exactly one credential is retrieved.
            .setAutoSelectEnabled(true)
            .build()
        startOneTapSignIn()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
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
