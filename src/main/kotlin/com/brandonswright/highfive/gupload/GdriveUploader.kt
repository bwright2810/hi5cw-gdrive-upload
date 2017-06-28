package com.brandonswright.highfive.gupload

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.FileContent
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream

class GdriveUploader {
	companion object {
		@JvmStatic fun main(args: Array<String>) {
			if (args.isEmpty()) {
				throw RuntimeException("file to upload argument required")
			}

			GdriveUploader().execute(args[0])
		}

		val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport()
		val DATA_STORE_DIR = java.io.File(System.getProperty("user.dir"), "gdrive-credentials")
		val DATA_STORE_FACTORY = FileDataStoreFactory(DATA_STORE_DIR)
		val JSON_FACTORY = JacksonFactory.getDefaultInstance()
		val SCOPES = listOf(DriveScopes.DRIVE)
	}

	val driveService: Drive by lazy {
		Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, authorize())
			.setApplicationName("H5 GDrive Uploader")
			.build()
	}

	fun authorize(): Credential {
		val inStream = FileInputStream("client_secret.json")

		val clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, InputStreamReader(inStream))

		val flow = GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
				.setDataStoreFactory(DATA_STORE_FACTORY)
				.setAccessType("offline")
				.build()

		val credential = AuthorizationCodeInstalledApp(flow, LocalServerReceiver()).authorize("user")

		println("Credentials saved to ${DATA_STORE_DIR.absolutePath}")

		return credential
	}

	fun execute(fileToUpload: String) {
		val fileMetaData = File()
		val brokenDownFilePath = fileToUpload.replace("\\", "/").split("/")
		fileMetaData.title = brokenDownFilePath[brokenDownFilePath.size - 1]
		val filePath = java.io.File(fileToUpload)
		val mediaContent = FileContent("*/*", filePath)
		val file = driveService.files().insert(fileMetaData, mediaContent).setFields("id").execute();
		println("File ID: ${file.id}")
	}

	fun exportResource(resource: String): String {
		var isStream: InputStream? = null
		var osStream: OutputStream? = null
		var jarFolder = ""

		try {
			isStream = GdriveUploader::class.java.getResourceAsStream(resource)
			if (isStream == null) {
				throw RuntimeException("null resource")
			}

			var readBytes = 0
			val buffer = ByteArray(4096)
			jarFolder = java.io.File(GdriveUploader::class.java.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getParentFile().getPath().replace('\\', '/')
			osStream = FileOutputStream(jarFolder + "\\" + resource)

			readBytes = isStream.read(buffer)
			while (readBytes > 0) {
				osStream.write(buffer, 0, readBytes)
				readBytes = isStream.read(buffer)
			}
		} finally {
			isStream?.close()
			osStream?.close()
		}

		return jarFolder + "\\" + resource
	}
}