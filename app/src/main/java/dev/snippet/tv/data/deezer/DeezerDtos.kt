package dev.snippet.tv.data.deezer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeezerArtistDto(val name: String = "")

@Serializable
data class DeezerAlbumDto(
    @SerialName("cover_big") val coverBig: String = "",
    @SerialName("cover_medium") val coverMedium: String = "",
)

@Serializable
data class DeezerErrorDto(
    val type: String = "",
    val message: String = "",
    val code: Int = 0,
)

/** Deezer reports failures as HTTP 200 with an `error` object instead of track fields. */
@Serializable
data class DeezerEnvelopeDto(val error: DeezerErrorDto? = null)

@Serializable
data class DeezerTrackDto(
    val id: Long = 0,
    val title: String = "",
    val preview: String = "",
    val artist: DeezerArtistDto? = null,
    val album: DeezerAlbumDto? = null,
)
