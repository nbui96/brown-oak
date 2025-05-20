package com.apani.moodieapigateway.service

import com.apani.moodieapigateway.model.rest.ModelMetadata
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import software.amazon.awssdk.services.s3.S3Client
import org.springframework.stereotype.Service
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.core.sync.ResponseTransformer
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import java.io.*
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

@Service
class S3Service(
    @Value("\${aws.bucket}") private val bucket: String,
    @Value("\${aws.region}") private val region: String,
    @Value("\${aws.downloadDir}") private val downloadDir: String,
) {

    private val log = LoggerFactory.getLogger(S3Service::class.java)
    private val objectMapper = jacksonObjectMapper()

    /**
     * 1. Download latest metadata to temporary file
     *
     * Get the latest tracker without modifying any permanent files
     * This isolates the "checking" from the "updating" steps
     *
     *
     * Parse and compare versions
     *
     * Extract version from temp metadata
     * Compare with currently loaded version
     *
     *
     * Make a decision
     *
     * If version is the same: delete temp file only
     * If version is newer: proceed with model download and update permanent metadata
     */
    fun downloadLatestModel() {
        val s3Client: S3Client = S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build()

        val metadataKey = "models/metadata/latest.json"
        val tmpPath = Paths.get(downloadDir, "temp", "latest.json")
        Files.createDirectories(tmpPath.parent)
        val metadataTmpFile = tmpPath.toFile()

        log.info("Downloading metadata from s3://$bucket//$metadataKey")
        s3Client.getObject(
            GetObjectRequest.builder()
                .bucket(bucket)
                .key(metadataKey)
                .build(),
            ResponseTransformer.toFile(metadataTmpFile)
        )

        // Parse the metadata to get the latest model version
        val metadata = objectMapper.readValue<ModelMetadata>(metadataTmpFile)
        val latestVersion = metadata.version
        log.info("Latest model version is: $latestVersion")

        val currentModelDir = Paths.get(downloadDir, "latest-models")
        val currentMetadataFile = currentModelDir.resolve("metadata.json")

        if (Files.exists(currentMetadataFile)) {
            val currentMetadata = objectMapper.readValue<ModelMetadata>(currentMetadataFile.toFile())

            if (currentMetadata.version == latestVersion) {
                log.info("Current model is latest, No need to update...")
                metadataTmpFile.parentFile.deleteRecursively()
                return
            }
        }

        val modelDir = Paths.get(downloadDir, "latest-models")
        Files.createDirectories(modelDir)

        val metadataFile = modelDir.resolve("metadata.json")
        Files.copy(metadataTmpFile.toPath(), metadataFile, StandardCopyOption.REPLACE_EXISTING)
        metadataTmpFile.parentFile.deleteRecursively()

        val modelKey = "models/movie_recommender_model_$latestVersion.zip"
        val modelFile = modelDir.resolve("model.zip")

        log.info("Downloading model from s3://$bucket/$modelKey to $modelFile")
        s3Client.getObject(
            GetObjectRequest.builder()
                .bucket(bucket)
                .key(modelKey)
                .build(),
            ResponseTransformer.toFile(modelFile.toFile())
        )

        // Extract the zip file
        try {
            log.info("Extracting model file using Java's ZIP tools")
            extractZipUsingJava(modelFile.toFile(), modelDir.toFile())
            log.info("Extraction completed successfully")
        } catch (e: Exception) {
            log.error("Error during ZIP extraction: ${e.message}", e)
            throw e
        }

        // Delete the zip file to save space (optional)
        Files.deleteIfExists(modelFile)

        log.info("Successfully downloaded and extracted model version $latestVersion to $modelDir")
    }

    /**
     * Extract a ZIP file using Java's built-in ZIP capabilities.
     * This doesn't rely on external commands being installed.
     */
    private fun extractZipUsingJava(zipFile: File, destDir: File) {
        // Create destination directory if it doesn't exist
        if (!destDir.exists()) {
            destDir.mkdirs()
        }

        // Buffer for reading ZIP entries
        val buffer = ByteArray(1024)

        try {
            // Create input streams
            val fileInputStream = FileInputStream(zipFile)
            val bufferedInputStream = BufferedInputStream(fileInputStream)
            val zipInputStream = ZipInputStream(bufferedInputStream)

            // Process each entry in the ZIP file
            var entry: ZipEntry? = zipInputStream.nextEntry
            var entriesExtracted = 0

            while (entry != null) {
                val entryName = entry.name
                val filePath = File(destDir, entryName)

                // Create parent directories if they don't exist
                val parent = filePath.parentFile
                if (!parent.exists()) {
                    parent.mkdirs()
                }

                // If the entry is a directory, create it and skip to next entry
                if (entry.isDirectory) {
                    filePath.mkdirs()
                } else {
                    // Extract the file
                    val fileOutputStream = FileOutputStream(filePath)
                    var len: Int

                    while (zipInputStream.read(buffer).also { len = it } > 0) {
                        fileOutputStream.write(buffer, 0, len)
                    }

                    fileOutputStream.close()
                    entriesExtracted++

                    // Log progress for large archives
                    if (entriesExtracted % 100 == 0) {
                        log.debug("Extracted $entriesExtracted entries...")
                    }
                }

                // Close the current entry and get the next one
                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }

            // Close the ZIP input stream
            zipInputStream.close()
            log.info("Extracted $entriesExtracted files from ZIP archive")

        } catch (e: Exception) {
            log.error("Error extracting ZIP file: ${e.message}", e)
            throw RuntimeException("Failed to extract ZIP file: ${e.message}", e)
        }
    }
}


