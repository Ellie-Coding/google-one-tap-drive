package com.elliecoding.onetapdrive

import android.content.Context
import android.util.Log
import androidx.annotation.WorkerThread
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.FileContent
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.HttpResponseException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.util.Collections

private const val TAG = "DriveStorage"

/**
 * Global instance of the JSON factory.
 */
private val JSON_FACTORY: JsonFactory = GsonFactory.getDefaultInstance()

class DriveStorage {

    companion object {

        /**
         * Creates a file in the application data folder.
         *
         * @return Created file's Id.
         */
        @WorkerThread
        suspend fun upload(
            context: Context,
            credentials: HttpRequestInitializer,
            data: String
        ): String? {
            // Build a new authorized API client service.
            val service: Drive = Drive.Builder(
                NetHttpTransport(),
                JSON_FACTORY,
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
                    FileOutputStream(androidFile).use { stream ->
                        stream.write(data.toByteArray())
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

        @WorkerThread
        suspend fun delete(context: Context, credentials: HttpRequestInitializer) {
            upload(context, credentials, "")
        }

        @WorkerThread
        fun download(credentials: HttpRequestInitializer): String? {
            val service =
                Drive.Builder(
                    NetHttpTransport(),
                    JSON_FACTORY,
                    credentials
                )
                    .setApplicationName("OneTapDrive")
                    .build()
            return try {
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
