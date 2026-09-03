package dev.snippet.tv.data.model

import kotlinx.serialization.Serializable

/** One bundled track. `rank` is Deezer's popularity score frozen at list-generation time. */
@Serializable
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val rank: Long,
)

/** Top-level shape of assets/songs.json. Unknown keys (_note, generatedAtUtc) are ignored. */
@Serializable
data class SongFile(
    val version: Int = 1,
    val songs: List<Song> = emptyList(),
)
