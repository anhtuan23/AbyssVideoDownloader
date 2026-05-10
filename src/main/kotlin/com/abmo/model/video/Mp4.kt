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
    val source = sources?.find { it?.label == resolution }
    val firstData = firstDatas
        ?.find { it?.res_id == source?.res_id && it?.size == source?.size }
        ?: firstDatas?.find { it?.res_id == source?.res_id && it?.codec == source?.codec }
        ?: firstDatas?.find { it?.res_id == source?.res_id }
        ?: firstDatas?.firstOrNull()
    val preferredPath = source?.path
        ?: extractSegmentPath(source?.url)

    return SimpleVideo(
        slug = slug,
        md5_id = md5_id,
        label = source?.label,
        size = source?.size,
        partSize = source?.partSize?.toLong() ?: firstData?.partSize?.toLong(),
        url = buildSegmentUrl(
            sourceUrl = source?.url,
            firstDataUrl = firstData?.url,
            domains = domains,
            subdomain = source?.sub,
            size = source?.size
        ),
        path = preferredPath,
        resId = source?.res_id
    )
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
    val normalized = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
        rawUrl
    } else {
        "https://$rawUrl"
    }

    val uri = URI(normalized)
    val scheme = uri.scheme ?: "https"
    val authority = uri.authority ?: return normalized

    return "$scheme://$authority"
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
