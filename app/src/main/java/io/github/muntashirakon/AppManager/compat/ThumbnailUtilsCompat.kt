// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.util.Size
import java.io.IOException
import java.util.*

object ThumbnailUtilsCompat {
    /**
     * Create a thumbnail for given audio file.
     * <p>
     * This method should only be used for files that you have direct access to;
     * if you'd like to work with media hosted outside your app, consider using
     * {@link android.content.ContentResolver#loadThumbnail(Uri, Size, CancellationSignal)}
     * which enables remote providers to efficiently cache and invalidate
     * thumbnails.
     *
     * @param context The Context to use when resolving the audio Uri.
     * @param uri     The audio Uri.
     * @param size    The desired thumbnail size.
     * @throws IOException If any trouble was encountered while generating or loading the thumbnail, or if
     *                     {@link CancellationSignal#cancel()} was invoked.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun createAudioThumbnail(context: Context, uri: Uri, size: Size, signal: CancellationSignal?): Bitmap {
        // Checkpoint before going deeper
        signal?.throwIfCanceled()

        try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)
                val raw = retriever.embeddedPicture
                if (raw != null) {
                    val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size)
                    return getThumbnail(bitmap, size, true)
                }
            }
        } catch (e: RuntimeException) {
            throw IOException("Failed to create thumbnail", e)
        }
        throw IOException("No album art found")
    }

    /**
     * Create a thumbnail for given video file.
     * <p>
     * This method should only be used for files that you have direct access to;
     * if you'd like to work with media hosted outside your app, consider using
     * {@link android.content.ContentResolver#loadThumbnail(Uri, Size, CancellationSignal)}
     * which enables remote providers to efficiently cache and invalidate
     * thumbnails.
     *
     * @param context The Context to use when resolving the video Uri.
     * @param uri     The video file.
     * @param size    The desired thumbnail size.
     * @throws IOException If any trouble was encountered while generating or
     *                     loading the thumbnail, or if
     *                     {@link CancellationSignal#cancel()} was invoked.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun createVideoThumbnail(context: Context, uri: Uri, size: Size, signal: CancellationSignal?): Bitmap {
        // Checkpoint before going deeper
        signal?.throwIfCanceled()

        try {
            MediaMetadataRetriever().use { mmr ->
                mmr.setDataSource(context, uri)

                // Try to retrieve thumbnail from metadata
                val raw = mmr.embeddedPicture
                if (raw != null) {
                    val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size)
                    return getThumbnail(bitmap, size, true)
                }

                val params: MediaMetadataRetriever.BitmapParams? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    MediaMetadataRetriever.BitmapParams().apply {
                        preferredConfig = Bitmap.Config.ARGB_8888
                    }
                } else null

                // Fall back to middle of video
                // Note: METADATA_KEY_DURATION unit is in ms, not us.
                val durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val thumbnailTimeUs = durationStr!!.toLong() * 1000 / 2

                // If we're okay with something larger than native format, just
                // return a frame without up-scaling it
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    getThumbnail(mmr.getFrameAtTime(thumbnailTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, params)!!, size, false)
                } else {
                    getThumbnail(mmr.getFrameAtTime(thumbnailTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)!!, size, false)
                }
            }
        } catch (e: RuntimeException) {
            throw IOException("Failed to create thumbnail", e)
        }
    }

    private fun getThumbnail(bitmap: Bitmap, size: Size, recycle: Boolean): Bitmap {
        return ThumbnailUtils.extractThumbnail(
            Objects.requireNonNull(bitmap), size.width, size.height,
            if (recycle) ThumbnailUtils.OPTIONS_RECYCLE_INPUT else 0
        )
    }
}
