package com.abmo

import com.abmo.common.Constants
import com.abmo.common.Constants.ABYSS_BASE_URL
import com.abmo.common.Logger
import com.abmo.model.Config
import com.abmo.model.video.preferredResolutionLabel
import com.abmo.services.ProviderDispatcher
import com.abmo.services.VideoDownloader
import com.abmo.util.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import java.io.File
import kotlin.system.exitProcess

class Application(private val args: Array<String>) : KoinComponent {

    private val videoDownloader: VideoDownloader by inject()
    private val providerDispatcher: ProviderDispatcher by inject()
    private val cliArguments: CliArguments by inject { parametersOf(args) }

    suspend fun run() {

        val outputPath = cliArguments.getOutputFileName()
        val headers = cliArguments.getHeaders()
        val numberOfConnections = cliArguments.getParallelConnections()
        val videoIdsOrUrls = cliArguments.getVideoIdsOrUrlsWithResolutions()
        Constants.VERBOSE = cliArguments.isVerboseEnabled()

        videoIdsOrUrls.forEach { pairs ->
            val videoUrl = pairs.first
            val resolution = pairs.second

            val dispatcher = providerDispatcher.getProviderForUrl(videoUrl)
            val downloadTargets = dispatcher.getDownloadTargets(videoUrl)
            val defaultHeader = if (videoUrl.isValidUrl()) {
                mapOf("Referer" to videoUrl.extractReferer())
            } else { emptyMap() }

            if (downloadTargets.isEmpty()) {
                Logger.error("No downloadable videos found for input: $videoUrl")
                return@forEach
            }

            val multipleTargets = downloadTargets.size > 1
            val outputDirectory = when {
                multipleTargets -> {
                    if (outputPath != null && outputPath.endsWith(".mp4", ignoreCase = true)) {
                        Logger.error("When downloading multiple episodes, -o must point to a directory, not an .mp4 file.")
                        exitProcess(0)
                    }
                    val explicitDirectory = outputPath?.let(::ensureDirectory)
                    if (outputPath != null && explicitDirectory == null) {
                        exitProcess(0)
                    }
                    explicitDirectory ?: File(".").canonicalFile
                }
                outputPath != null && !outputPath.endsWith(".mp4", ignoreCase = true) -> {
                    ensureDirectory(outputPath) ?: exitProcess(0)
                }
                else -> null
            }

            downloadTargets.forEachIndexed { index, target ->
                if (multipleTargets) {
                    val existingEpisodeFile = findExistingEpisodeFile(outputDirectory, target.fileStem ?: target.videoId)
                    if (existingEpisodeFile != null) {
                        Logger.info("Skipping existing episode: ${existingEpisodeFile.absolutePath}")
                        return@forEachIndexed
                    }
                }

                val videoID = target.videoId
                val url = "$ABYSS_BASE_URL/?v=$videoID"
                val videoMetadata = videoDownloader.getVideoMetaData(url, headers ?: defaultHeader)
                val videoSources = videoMetadata?.sources
                    ?.sortedBy { it?.label?.filter { char -> char.isDigit() }?.toIntOrNull() }

                if (videoSources == null) {
                    Logger.error("Video with ID $videoID not found")
                    return@forEachIndexed
                }

                val mappedResolution = videoMetadata.preferredResolutionLabel(resolution)

                if (mappedResolution == null) return@forEachIndexed

                val outputFile = when {
                    multipleTargets -> {
                        val fileName = "${target.fileStem ?: videoID} [$mappedResolution].mp4"
                        File(outputDirectory, fileName)
                    }
                    outputPath != null && outputPath.endsWith(".mp4", ignoreCase = true) -> {
                        if (!isValidPath(outputPath)) {
                            exitProcess(0)
                        }
                        File(outputPath)
                    }
                    else -> {
                        val directory = outputDirectory ?: File(".")
                        val defaultFileName = "${target.fileStem ?: videoID} [$mappedResolution].mp4"
                        if (outputPath == null) {
                            Logger.warn("No output file specified. The video will be saved to '${directory.path}/$defaultFileName'.\n")
                        }
                        File(directory, defaultFileName)
                    }
                }

                if (outputFile.exists()) {
                    Logger.info("Skipping existing file: ${outputFile.absolutePath}")
                    return@forEachIndexed
                }

                val config = Config(url, mappedResolution, outputFile, headers, numberOfConnections)
                Logger.info("video with id $videoID and resolution $mappedResolution being processed...\n")
                try {
                    videoDownloader.downloadSegmentsInParallel(config, videoMetadata)
                } catch (e: Exception) {
                    Logger.error(e.message.toString())
                }

                if (multipleTargets && index < downloadTargets.lastIndex) {
                    println("-------------------------------------------------------------------------------------------------")
                }
            }

            if (videoIdsOrUrls.size > 1) {
                println("-----------------------------------------${downloadTargets.last().videoId}--------------------------------------------------------")
            }
        }
    }

    private fun findExistingEpisodeFile(directory: File?, fileStem: String): File? {
        if (directory == null || !directory.isDirectory) return null

        val episodeFilePrefix = "$fileStem ["
        return directory.listFiles { file ->
            file.isFile &&
                file.extension.equals("mp4", ignoreCase = true) &&
                file.name.startsWith(episodeFilePrefix)
        }?.maxByOrNull { it.lastModified() }
    }

}
