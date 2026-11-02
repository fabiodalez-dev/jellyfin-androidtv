package org.jellyfin.androidtv.util.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.core.content.getSystemService
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.VideoAudioOutput

/**
 * Output types that reach the TV, best first. On the Chromecast with Google TV the built-in "speaker"
 * output is the TV and is honoured with a Bluetooth speaker connected, while the HDMI device is not.
 */
private val tvOutputTypes = listOf(
	AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
	AudioDeviceInfo.TYPE_HDMI,
	AudioDeviceInfo.TYPE_HDMI_ARC,
	AudioDeviceInfo.TYPE_HDMI_EARC,
)

/**
 * Pick the output that reaches the TV, or null when there is none.
 */
fun <T> pickTvAudioOutput(outputs: List<T>, typeOf: (T) -> Int): T? = tvOutputTypes
	.firstNotNullOfOrNull { type -> outputs.firstOrNull { typeOf(it) == type } }

fun Context.findTvAudioOutput(): AudioDeviceInfo? {
	val audioManager = getSystemService<AudioManager>() ?: return null
	return pickTvAudioOutput(audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()) { it.type }
}

/**
 * The output video playback should prefer, or null to follow the system routing.
 */
fun UserPreferences.preferredVideoAudioDevice(context: Context): AudioDeviceInfo? = when (this[UserPreferences.videoAudioOutput]) {
	VideoAudioOutput.AUTO -> null
	VideoAudioOutput.TV -> context.findTvAudioOutput()
}
