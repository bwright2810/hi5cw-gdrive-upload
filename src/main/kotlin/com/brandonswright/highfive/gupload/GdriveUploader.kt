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
import com.google.api.services.drive.model.ParentReference
import com.google.api.services.drive.model.Permission
import org.apache.commons.io.FileUtils
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.charset.Charset

class GdriveUploader {
	companion object {
		@JvmStatic fun main(args: Array<String>) {
			if (args.isEmpty()) {
				throw RuntimeException("file to upload argument required")
			}

			val gdriveFolder = if (args.size > 1) args[1] else ""

			GdriveUploader().execute(args[0], gdriveFolder)
		}

		val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport()
		val DATA_STORE_DIR = java.io.File(System.getProperty("user.dir"), "gdrive-credentials")
		val DATA_STORE_FACTORY = FileDataStoreFactory(DATA_STORE_DIR)
		val JSON_FACTORY = JacksonFactory.getDefaultInstance()
		val SCOPES = listOf(DriveScopes.DRIVE, DriveScopes.DRIVE_FILE, DriveScopes.DRIVE_APPDATA)
		val GDRIVE_DL_URL = "https://drive.google.com/uc?export=download&id="
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

	fun execute(fileToUpload: String, gdriveFolder: String = "") {
		var folderId = ""
		if (gdriveFolder.isNotEmpty()) {
			val result = driveService.files().list()
					.setQ("title='${gdriveFolder}'")
					.execute()

			val folder = result.items.first()
			folderId = folder.id
		}

		val fileMetaData = File()
		val brokenDownFilePath = fileToUpload.replace("\\", "/").split("/")
		fileMetaData.title = brokenDownFilePath[brokenDownFilePath.size - 1]
		if (folderId.isNotEmpty()) {
			fileMetaData.parents = listOf(ParentReference().setId(folderId))
		}

		val filePath = java.io.File(fileToUpload)
		val mediaContent = FileContent("*/*", filePath)

		val fields = if (folderId.isNotEmpty()) "id, parents" else "id"
		val file = driveService.files().insert(fileMetaData, mediaContent).setFields(fields).execute();

		println("Uploaded ${fileMetaData.title}")
		
		val dlLink = GDRIVE_DL_URL + file.id

		driveService.permissions().insert(file.id, Permission()
				.setType("anyone")
				.setRole("reader"))
				.setFields("id")
				.execute()
		
		println("Download link: ${dlLink}")

		val campersQ = if (folderId.isNotEmpty()) "title='campers.txt' and '${folderId}' in parents" else "title='campers.txt'"

		val campersTxtResult = driveService.files().list()
				.setQ(campersQ)
				.execute()

		val campersTxtFile = campersTxtResult.items.first()
		val outputStream = ByteArrayOutputStream();
		driveService.files().get(campersTxtFile.id).executeMediaAndDownloadTo(outputStream)
		var txtFileString = String(outputStream.toByteArray(), Charset.defaultCharset())
		val newTxtFileString = txtFileString + fileMetaData.title + " - " + dlLink + "\n"

		val campersFile = java.io.File("campers.txt")
		FileUtils.writeStringToFile(campersFile, newTxtFileString)

		val campersFileMetaData = File()
		campersFileMetaData.title = "campers.txt"
		if (folderId.isNotEmpty()) {
			fileMetaData.parents = listOf(ParentReference().setId(folderId))
		}

		val campersMediaContent = FileContent("*/*", campersFile)

		val campersFields = if (folderId.isNotEmpty()) "id, parents" else "id"
		driveService.files().update(campersTxtFile.id, campersFileMetaData, campersMediaContent).setFields(campersFields).execute();
		
		println("Updated campers.txt")
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