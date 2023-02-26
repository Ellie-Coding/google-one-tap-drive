package com.elliecoding.onetapdrive

import android.content.Context
import android.util.Log
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.FileContent
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.HttpResponseException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.util.Collections


private const val TAG = "DriveStorage"

/**
 * Application name.
 */
private const val APPLICATION_NAME = "Google Drive API Java Quickstart"

/**
 * Global instance of the JSON factory.
 */
private val JSON_FACTORY: JsonFactory = GsonFactory.getDefaultInstance()

/**
 * Directory to store authorization tokens for this application.
 */
private const val TOKENS_DIRECTORY_PATH = "tokens"

/**
 * Global instance of the scopes required by this quickstart.
 * If modifying these scopes, delete your previously saved tokens/ folder.
 */
private val SCOPES = listOf(DriveScopes.DRIVE_APPDATA)
private const val CREDENTIALS_FILE_PATH = "/credentials.json"

class DriveStorage {


    companion object {

        /**
         * Creates a file in the application data folder.
         *
         * @return Created file's Id.
         */
        suspend fun upload(
            context: Context,
            credentials: HttpRequestInitializer,
            data: String
        ): String? {
            // Build a new authorized API client service.
            val service: Drive = Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credentials
            )
                .setApplicationName("OneTapDrive")
                .build()
            return withContext(Dispatchers.IO) {

                try {
                    // File's metadata.
                    val fileMetadata = File()
                    fileMetadata.name = "config.txt"
                    fileMetadata.parents = Collections.singletonList("appDataFolder")

                    val path: java.io.File = context.filesDir
                    val androidFile = java.io.File(path, "config.txt")
                    val stream = FileOutputStream(androidFile)
                    try {
                        stream.write(data.toByteArray())
                    } finally {
                        stream.close()
                    }

                    val mediaContent = FileContent(
                        "application/text",
                        androidFile
                    )
                    val file: File = service.files().create(fileMetadata, mediaContent)
                        .setFields("id")
                        .execute()
                    Log.d(TAG, "Upload success into File ID: " + file.id)
                    file.id
                } catch (e: GoogleJsonResponseException) {
                    System.err.println("Unable to create file: " + e.details)
                    throw e
                }
            }
        }

        suspend fun delete(context: Context, credentials: HttpRequestInitializer) {
            upload(context, credentials, "")
        }

        suspend fun download(credentials: HttpRequestInitializer): String? {
            val service =
                Drive.Builder(
                    NetHttpTransport(),
                    JSON_FACTORY,
                    credentials
                )
                    .setApplicationName("OneTapDrive")
                    .build()
            return withContext(Dispatchers.IO) {
                try {
                    var result = ""
                    val files = service.files().list()
                        .setSpaces("appDataFolder")
                        .setFields("nextPageToken, files(id, name)")
                        .execute()
                    for (file in files.files) {
                        Log.i(TAG, "Found file " + file.name)
                        if (file.name.equals("config.txt")) {
                            val outputStream = ByteArrayOutputStream()
                            service.files().get(file.id).executeMediaAndDownloadTo(outputStream)
                            result = String(outputStream.toByteArray())
                            break
                        }
                    }
                    result
                } catch (ex: HttpResponseException) {
                    Log.w(TAG, "Error downloading", ex)
                    null
                } catch (e: GoogleJsonResponseException) {
                    throw e
                }
            }
        }
    }
}
