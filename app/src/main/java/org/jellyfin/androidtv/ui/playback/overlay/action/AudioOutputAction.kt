package org.jellyfin.androidtv.ui.playback.overlay.action

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.PopupMenu
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.VideoAudioOutput
import org.jellyfin.androidtv.ui.playback.PlaybackController
import org.jellyfin.androidtv.ui.playback.overlay.CustomPlaybackTransportControlGlue
import org.jellyfin.androidtv.ui.playback.overlay.VideoPlayerAdapter
import org.jellyfin.androidtv.util.popupMenu

/**
 * Fork: switch video audio between the system output and the TV while playing.
 */
class AudioOutputAction(
	context: Context,
	customPlaybackTransportControlGlue: CustomPlaybackTransportControlGlue,
	private val userPreferences: UserPreferences,
) : CustomAction(context, customPlaybackTransportControlGlue) {
	private var popup: PopupMenu? = null

	init {
		initializeWithIcon(R.drawable.ic_tv)
	}

	override fun handleClickAction(
		playbackController: PlaybackController,
		videoPlayerAdapter: VideoPlayerAdapter,
		context: Context,
		view: View,
	) {
		videoPlayerAdapter.leanbackOverlayFragment.setFading(false)
		dismissPopup()
		val current = userPreferences[UserPreferences.videoAudioOutput]
		popup = popupMenu(context, view, Gravity.END) {
			for (output in VideoAudioOutput.entries) {
				item(context.getString(output.nameRes)) {
					userPreferences[UserPreferences.videoAudioOutput] = output
					playbackController.applyVideoAudioOutput()
				}.apply {
					isChecked = output == current
				}
			}
		}
		popup?.menu?.setGroupCheckable(0, true, true)
		popup?.setOnDismissListener {
			videoPlayerAdapter.leanbackOverlayFragment.setFading(true)
			popup = null
		}
		popup?.show()
	}

	fun dismissPopup() {
		popup?.dismiss()
	}
}
