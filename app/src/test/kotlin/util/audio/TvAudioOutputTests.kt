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

	test("pickTvAudioOutput prefers HDMI over speaker") {
		pick(speaker, a2dp, hdmi) shouldBe hdmi
	}

	test("pickTvAudioOutput falls back to HDMI ARC then eARC before speaker") {
		pick(speaker, hdmiEarc, a2dp) shouldBe hdmiEarc
		pick(speaker, hdmiArc, hdmiEarc) shouldBe hdmiArc
	}

	test("pickTvAudioOutput uses the Chromecast speaker output when no HDMI is exposed") {
		pick(speaker, a2dp) shouldBe speaker
	}

	test("pickTvAudioOutput returns null when no tv-capable output") {
		pick(a2dp, bleSpeaker) shouldBe null
		pick() shouldBe null
	}
})
