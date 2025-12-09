// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.audio

import android.app.Application
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Collections

class AudioPlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val _audioMetadataLiveData = MutableLiveData<AudioMetadata>()
    val audioMetadataLiveData: LiveData<AudioMetadata> = _audioMetadataLiveData

    private val _mediaPlayerPreparedLiveData = MutableLiveData<Boolean>()
    val mediaPlayerPreparedLiveData: LiveData<Boolean> = _mediaPlayerPreparedLiveData

    private val _playlistLoadedLiveData = MutableLiveData<Boolean>()
    val playlistLoadedLiveData: LiveData<Boolean> = _playlistLoadedLiveData

    private val playlist = Collections.synchronizedList(ArrayList<AudioMetadata>())

    var currentPlaylistIndex = -1
        private set

    fun playlistSize(): Int = playlist.size

    fun addToPlaylist(uriList: Array<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            for (uri in uriList) {
                val audioMetadata = fetchAudioMetadata(uri)
                playlist.add(audioMetadata)
                val index = currentPlaylistIndex
                if (index == -1) {
                    // Start playing immediately if nothing is playing
                    currentPlaylistIndex = 0
                    _audioMetadataLiveData.postValue(audioMetadata)
                }
            }
            _playlistLoadedLiveData.postValue(true)
        }
    }

    fun playNext(@RepeatMode repeatMode: Int) {
        when (repeatMode) {
            RepeatMode.NO_REPEAT -> playNext(false)
            RepeatMode.REPEAT_INDEFINITELY -> playNext(true)
            RepeatMode.REPEAT_SINGLE_INDEFINITELY -> {
                _audioMetadataLiveData.postValue(playlist[currentPlaylistIndex])
            }
        }
    }

    fun playNext(repeat: Boolean) {
        var index = currentPlaylistIndex
        when {
            index < (playlist.size - 1) -> {
                index++
                currentPlaylistIndex = index
                _audioMetadataLiveData.postValue(playlist[index])
            }
            repeat -> {
                // Reset index
                index = 0
                currentPlaylistIndex = index
                _audioMetadataLiveData.postValue(playlist[index])
            }
        }
    }

    fun playPrevious() {
        val index = currentPlaylistIndex
        if (index > 0) {
            currentPlaylistIndex = index - 1
            _audioMetadataLiveData.postValue(playlist[currentPlaylistIndex])
        }
    }

    fun prepareMediaPlayer(mediaPlayer: MediaPlayer, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                mediaPlayer.reset()
                mediaPlayer.setDataSource(getApplication(), uri)
                mediaPlayer.prepare()
                _mediaPlayerPreparedLiveData.postValue(true)
            } catch (e: IOException) {
                e.printStackTrace()
                _mediaPlayerPreparedLiveData.postValue(false)
            }
        }
    }

    private fun fetchAudioMetadata(uri: Uri): AudioMetadata {
        val audioMetadata = AudioMetadata()
        audioMetadata.uri = uri
        try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(getApplication(), uri)
                val raw = retriever.embeddedPicture
                if (raw != null) {
                    audioMetadata.cover = BitmapFactory.decodeByteArray(raw, 0, raw.size)
                }
                var title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                if (title == null) {
                    title = uri.lastPathSegment ?: "<Unknown Title>"
                }
                var artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: "<Unknown Artist>"
                var album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    ?: "<Unknown Album>"
                var albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                    ?: "<Unknown Artist>"

                audioMetadata.title = title
                audioMetadata.album = album
                audioMetadata.albumArtist = albumArtist
                audioMetadata.artist = artist
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val title = uri.lastPathSegment ?: "<Unknown Title>"
            audioMetadata.title = title
            audioMetadata.album = "<Unknown Album>"
            audioMetadata.albumArtist = "<Unknown Artist>"
            audioMetadata.artist = "<Unknown Artist>"
        }
        return audioMetadata
    }
}
