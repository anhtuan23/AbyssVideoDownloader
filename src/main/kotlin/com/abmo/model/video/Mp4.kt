package com.abmo.model.video

import com.abmo.model.SimpleVideo
import com.google.gson.annotations.SerializedName
import java.net.URI

data class Mp4(
    val domains: List<String?>? = null,
    @SerializedName("fristDatas")
    val firstDatas: List<FirstData?>? = null,
    val sources: List<Source?>? = null,
    val slug: String? = null,
    val md5_id: Int? = null
)


fun Mp4.toSimpleVideo(resolution: String): SimpleVideo {
    val matchingSources = sources
        ?.filter { it?.label == resolution }
        .orEmpty()
    val source = matchingSources.firstOrNull { it?.status != false && it?.partSize != 0 }
        ?: matchingSources.firstOrNull { it?.status != false }
        ?: matchingSources.firstOrNull()
    val firstData = firstDatas
        ?.find { it?.res_id == source?.res_id && it?.size == source?.size }
        ?: firstDatas?.find { source?.size == null && it?.res_id == source?.res_id && it?.codec == source?.codec }
        ?: firstDatas?.find { source?.size == null && it?.res_id == source?.res_id }
        ?: firstDatas?.firstOrNull { source == null }
    val preferredPath = source?.takeIf { (it.partSize ?: 0) > 0 }?.path
        ?: extractSegmentPath(source?.url)

    return SimpleVideo(
        slug = slug,
        md5_id = md5_id,
        label = source?.label,
        size = source?.size,
        partSize = source?.partSize?.takeIf { it > 0 }?.toLong()
            ?: firstData?.partSize?.takeIf { source?.partSize == null && it > 0 }?.toLong(),
        url = buildSegmentUrl(
            sourceUrl = source?.url,
            firstDataUrl = firstData?.url,
            domains = domains,
            subdomain = source?.sub,
            size = source?.size
        ),
        directUrl = buildDirectSourceUrl(source),
        path = preferredPath,
        resId = source?.res_id
    )
}

fun Mp4.preferredResolutionLabel(resolution: String): String? {
    val playableSources = sources
        ?.filter { it?.status != false && it?.size != null }
        .orEmpty()

    val supportedSources = playableSources.filterNot { source ->
        source.hasServiceWorkerFirstDataOnly(firstDatas)
    }

    val selectedSource = when (resolution) {
        "h" -> supportedSources.maxByOrNull { it?.size ?: 0L }
        "l" -> supportedSources.minByOrNull { it?.size ?: Long.MAX_VALUE }
        "m" -> supportedSources
            .sortedBy { it?.size ?: Long.MAX_VALUE }
            .let { sorted -> sorted.getOrNull((sorted.size - 1) / 2) }
        else -> supportedSources.maxByOrNull { it?.size ?: 0L }
    }

    return selectedSource?.label
}

private fun Source?.hasServiceWorkerFirstDataOnly(firstDatas: List<FirstData?>?): Boolean {
    if (this == null || !path.isNullOrBlank() || !url.isNullOrBlank()) return false

    return firstDatas?.any { firstData ->
        firstData?.res_id == res_id &&
            firstData?.size == size &&
            firstData.matchesCodec(codec) &&
            (firstData?.partSize ?: 0) > 0
    } == true
}

private fun FirstData?.matchesCodec(sourceCodec: String?): Boolean {
    return this?.codec.isNullOrBlank() ||
        sourceCodec.isNullOrBlank() ||
        this?.codec == sourceCodec
}

private fun buildDirectSourceUrl(source: Source?): String? {
    val path = source?.path?.takeIf { it.isNotBlank() } ?: return null
    val sourceUrl = source.url?.takeIf { it.isNotBlank() } ?: return null
    val baseUrl = normalizeSegmentBaseUrl(sourceUrl)
    return "${baseUrl.trimEnd('/')}/${path.trimStart('/')}"
}


private fun buildSegmentUrl(
    sourceUrl: String?,
    firstDataUrl: String?,
    domains: List<String?>?,
    subdomain: String?,
    size: Long?
): String? {
    sourceUrl?.takeIf { it.isNotBlank() }?.let {
        return normalizeSegmentBaseUrl(it)
    }

    val validDomains = domains
        ?.mapNotNull { it?.takeIf { domain -> domain.isNotBlank() } }
        .orEmpty()

    if (validDomains.isNotEmpty()) {
        val matchedDomain = subdomain
            ?.takeIf { it.isNotBlank() }
            ?.let { sub -> validDomains.find { it.contains(sub) } }

        val selectedDomain = matchedDomain
            ?: size?.let { validDomains[Math.floorMod(it, validDomains.size.toLong()).toInt()] }
            ?: validDomains.first()

        return normalizeDomainBaseUrl(selectedDomain, subdomain?.takeIf { it.isNotBlank() })
    }

    firstDataUrl?.takeIf { it.isNotBlank() }?.let {
        return normalizeSegmentBaseUrl(it)
    }

    return null
}

private fun normalizeSegmentBaseUrl(rawUrl: String): String {
    val normalized = normalizeFullUrl(rawUrl)
    val uri = URI(normalized)
    val scheme = uri.scheme ?: "https"
    val authority = uri.authority ?: return normalized

    return "$scheme://$authority"
}

private fun normalizeFullUrl(rawUrl: String): String {
    return if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
        rawUrl
    } else {
        "https://$rawUrl"
    }
}

private fun normalizeDomainBaseUrl(domain: String, subdomain: String?): String {
    val base = normalizeSegmentBaseUrl(domain)
    if (subdomain.isNullOrBlank()) return base

    val uri = URI(base)
    val suffix = uri.host?.substringAfter(".", missingDelimiterValue = "")?.takeIf { it.isNotBlank() }
        ?: return base

    return "${uri.scheme ?: "https"}://$subdomain.$suffix"
}

private fun extractSegmentPath(rawUrl: String?): String? {
    if (rawUrl.isNullOrBlank()) return null

    val normalized = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
        rawUrl
    } else {
        "https://$rawUrl"
    }

    val path = runCatching { URI(normalized).path }.getOrNull()
    return path?.takeIf { it.isNotBlank() && it != "/" }
}
