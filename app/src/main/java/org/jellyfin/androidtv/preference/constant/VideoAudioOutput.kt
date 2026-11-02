package org.jellyfin.androidtv.preference.constant

import org.jellyfin.androidtv.R
import org.jellyfin.preference.PreferenceEnum

/**
 * Where video playback sends its audio.
 */
enum class VideoAudioOutput(
	override val nameRes: Int,
) : PreferenceEnum {
	/**
	 * Follow the system routing (a connected Bluetooth speaker wins).
	 */
	AUTO(R.string.video_audio_output_auto),

	/**
	 * Prefer the output towards the TV, even with a Bluetooth speaker connected.
	 */
	TV(R.string.video_audio_output_tv),
}
