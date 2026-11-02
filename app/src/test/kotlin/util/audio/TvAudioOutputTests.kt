package org.jellyfin.androidtv.util.audio

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TvAudioOutputTests : FunSpec({
	// AudioDeviceInfo type constants
	val speaker = 2
	val a2dp = 8
	val hdmi = 9
	val hdmiArc = 10
	val bleSpeaker = 26
	val hdmiEarc = 29

	fun pick(vararg types: Int) = pickTvAudioOutput(types.toList()) { it }

	test("pickTvAudioOutput prefers the built-in speaker output over HDMI") {
		pick(hdmi, a2dp, speaker) shouldBe speaker
	}

	test("pickTvAudioOutput falls back to HDMI, then ARC, then eARC") {
		pick(a2dp, hdmiEarc, hdmi) shouldBe hdmi
		pick(hdmiEarc, hdmiArc, a2dp) shouldBe hdmiArc
		pick(a2dp, hdmiEarc) shouldBe hdmiEarc
	}

	test("pickTvAudioOutput returns null when no tv-capable output") {
		pick(a2dp, bleSpeaker) shouldBe null
		pick() shouldBe null
	}
})
