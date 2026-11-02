# Jellyfin Android TV — uscita audio "TV" — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Un pulsante "Audio: TV / Automatico" nella schermata di dettaglio di film ed episodi; con "TV" il player video suona sull'uscita verso la TV anche con una cassa Bluetooth connessa, Dolby/DTS compreso.

**Architecture:** Una preferenza ricordata (`videoAudioOutput`) e un piccolo helper che sceglie l'uscita TV tra quelle che Android espone. Il player video legacy (`VideoManager`) la passa a `ExoPlayer.setPreferredAudioDevice`, e il controllo di passthrough del device profile interroga la stessa uscita. Tutto il resto dell'app resta upstream.

**Tech Stack:** Kotlin/Java, Media3 1.11.1, Koin, kotest + mockk (JUnit Platform), Gradle (JDK 21), GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-11-02-jellyfin-uscita-audio-tv-design.md`

## Global Constraints

- Base: tag upstream `v0.20.0-beta.4` (`344d26eb5`); la patch è una serie di commit sopra quel tag sul branch `audio-tv` del fork `fabiodalez-dev/jellyfin-androidtv`.
- applicationId del fork: `it.fabiodalez.jellyfintv`; nome app release: `Jellyfin Audio TV`. Deve convivere con `org.jellyfin.androidtv` installato.
- Ordine di ricerca dell'uscita TV: `TYPE_HDMI` (9), poi `TYPE_HDMI_ARC` (10) / `TYPE_HDMI_EARC` (29), poi `TYPE_BUILTIN_SPEAKER` (2). Nessuna trovata → nessuna preferenza.
- Valore predefinito della preferenza: Automatico (comportamento upstream identico).
- Etichette: EN `Audio: TV` / `Audio: Automatic`; IT `Audio: TV` / `Audio: Automatico`.
- Nessuna modifica a `playback/media3/exoplayer/ExoPlayerBackend.kt` né alle schermate musicali.
- Commit, PR e release a nome di Fabio, in prima persona singolare, senza alcuna attribuzione AI.
- Dispositivo di prova: Chromecast `192.168.1.64:5555` (adb), cassa Esinkin `FC:58:FA:4D:75:82` connessa.

## Review Focus

- Uscita TV assente (HDMI scollegato, nessuna uscita compatibile): il video deve partire e suonare dove suonerebbe senza patch → test `pickTvAudioOutput returns null when no tv-capable output` (Task 2) + verifica su dispositivo (Task 6).
- Preferenza cambiata nella schermata di dettaglio e subito dopo Riproduci: il nuovo valore deve valere per quella riproduzione → verifica su dispositivo in Task 6 (VideoManager e device profile sono creati a ogni avvio).
- Avvio da "Continua a guardare" senza passare dal dettaglio: la preferenza vale lo stesso → verifica su dispositivo in Task 6.
- Musica di Jellyfin e altre app con preferenza TV: devono restare sulla cassa → verifica su dispositivo in Task 6.
- Dolby/E-AC3 con preferenza TV: passthrough se la TV lo supporta, altrimenti decodifica PCM sulla TV, mai errore di riproduzione → verifica su dispositivo in Task 6.

---

### Task 1: Fork, identità dell'app e firma

**Files:**
- Modify: `app/build.gradle.kts:9-20,54-70` (applicationId + resValue release basati su applicationId, non su namespace)
- Modify: `app/src/main/res/values/strings.xml:3` (`app_name_release`)
- Create: `docs/superpowers/specs/…-design.md`, `docs/superpowers/plans/…-audio-tv.md` (già presenti nel clone)

**Interfaces:**
- Produces: APK release firmato con applicationId `it.fabiodalez.jellyfintv`; keystore locale fuori dal repo in `~/Documents/GitHub/jellyfin-androidtv-signing/` (keystore + `signing.properties`), con le stesse quattro chiavi lette da `getProperty(...)` in `signingConfigs`.

- [ ] **Step 1:** Creare il fork `fabiodalez-dev/jellyfin-androidtv` con `gh repo fork jellyfin/jellyfin-androidtv --clone=false`, aggiungerlo come remote `fork` al clone locale, creare il branch `audio-tv` dal tag `v0.20.0-beta.4`.
- [ ] **Step 2:** In `defaultConfig` impostare `applicationId = "it.fabiodalez.jellyfintv"` (il `namespace` resta `org.jellyfin.androidtv`); nel build type `release` i tre `resValue` di package (`app_id`, `app_search_suggest_authority`, `app_search_suggest_intent_data`) usano l'applicationId, come già fa `debug` con il suffisso. `app_name_release` → `Jellyfin Audio TV`.
- [ ] **Step 3:** Generare il keystore con `keytool` (RSA 4096, validità 10000 giorni, alias `jellyfintv`, password casuale) in `~/Documents/GitHub/jellyfin-androidtv-signing/`.
- [ ] **Step 4: Build.** Run: `JELLYFIN_VERSION=0.20.0-beta.4 ./gradlew assembleRelease -Pkeystore.file=… -Pkeystore.password=… -Psigning.key.alias=jellyfintv -Psigning.key.password=…` (se `getProperty` non legge `-P`, usare le variabili d'ambiente `KEYSTORE_FILE` ecc. come nel workflow upstream). Expected: `app/build/outputs/apk/release/jellyfin-androidtv-v0.20.0-beta.4-release.apk`.
- [ ] **Step 5: Verifica.** `aapt2 dump badging <apk> | head -1` riporta `package: name='it.fabiodalez.jellyfintv'`; `adb install` riesce senza toccare `org.jellyfin.androidtv` (`pm list packages | grep jellyfin` mostra entrambi).
- [ ] **Step 6: Commit** spec, piano e identità app: `Fork: own application id and app name to install next to the official app`.

### Task 2: Preferenza e scelta dell'uscita TV

**Files:**
- Create: `app/src/main/java/org/jellyfin/androidtv/preference/constant/VideoAudioOutput.kt`
- Modify: `app/src/main/java/org/jellyfin/androidtv/preference/UserPreferences.kt` (vicino alle preferenze bitstream, riga ~140)
- Create: `app/src/main/java/org/jellyfin/androidtv/util/audio/tvAudioOutput.kt`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-it/strings.xml`
- Test: `app/src/test/kotlin/util/audio/TvAudioOutputTests.kt`

**Interfaces:**
- Produces:
  - `enum class VideoAudioOutput(override val nameRes: Int) : PreferenceEnum { AUTO(R.string.video_audio_output_auto), TV(R.string.video_audio_output_tv) }`
  - `UserPreferences.videoAudioOutput = enumPreference("video_audio_output", VideoAudioOutput.AUTO)`
  - `fun <T> pickTvAudioOutput(outputs: List<T>, typeOf: (T) -> Int): T?` — puro, restituisce la prima uscita secondo l'ordine dei Global Constraints.
  - `fun Context.findTvAudioOutput(): AudioDeviceInfo?` — `AudioManager.getDevices(GET_DEVICES_OUTPUTS)` + `pickTvAudioOutput`.
  - `fun UserPreferences.preferredVideoAudioDevice(context: Context): AudioDeviceInfo?` — `null` se AUTO, altrimenti `context.findTvAudioOutput()`.
  - Stringhe: `video_audio_output_auto` = `Audio: Automatic` / `Audio: Automatico`, `video_audio_output_tv` = `Audio: TV`.

- [ ] **Step 1: Test che falliscono** (kotest `FunSpec`, stile di `AndroidVersionTest`), con i tipi come `Int`:
  - `pickTvAudioOutput prefers HDMI over speaker`: `[2, 8, 9]` → `9`
  - `pickTvAudioOutput falls back to HDMI ARC then eARC before speaker`: `[2, 29, 8]` → `29`; `[2, 10, 29]` → `10`
  - `pickTvAudioOutput uses the Chromecast speaker output when no HDMI is exposed`: `[2, 8]` → `2`
  - `pickTvAudioOutput returns null when no tv-capable output`: `[8, 26]` → `null`; `[]` → `null`
- [ ] **Step 2:** Run `./gradlew :app:testDebugUnitTest --tests "*TvAudioOutputTests*"` → FAIL (funzione non definita).
- [ ] **Step 3:** Implementare enum, preferenza, stringhe e le tre funzioni.
- [ ] **Step 4:** Run lo stesso comando → PASS.
- [ ] **Step 5: Commit** `Add video audio output preference and TV output lookup`.

### Task 3: Il player video suona sulla TV

**Files:**
- Modify: `app/src/main/java/org/jellyfin/androidtv/ui/playback/VideoManager.java:104` (subito dopo `mExoPlayer = configureExoplayerBuilder(...).build();`)

**Interfaces:**
- Consumes: `UserPreferencesKt`/estensione `preferredVideoAudioDevice(context)` da Task 2 (da Java: funzione top-level del file `tvAudioOutput.kt`).

- [ ] **Step 1:** Dopo la creazione del player: `mExoPlayer.setPreferredAudioDevice(<preferredVideoAudioDevice(userPreferences, activity)>)` — `null` per AUTO lascia il comportamento upstream.
- [ ] **Step 2: Verifica su dispositivo** (nessun test unitario: dipende da ExoPlayer reale). Build e install come Task 1; con cassa connessa e preferenza TV forzata temporaneamente (o già dal Task 5 se fatto prima), riprodurre un film: `adb shell dumpsys media.audio_flinger | grep "Output devices"` deve riportare `AUDIO_DEVICE_OUT_SPEAKER` (o HDMI) durante la riproduzione, e `AUDIO_DEVICE_OUT_BLUETOOTH_A2DP` con AUTO.
- [ ] **Step 3: Commit** `Play video on the TV output when the preference asks for it`.

### Task 4: Passthrough verso la TV

**Files:**
- Modify: `app/src/main/java/org/jellyfin/androidtv/util/profile/codec/audioPassthrough.kt` (parametro `routedDevice: AudioDeviceInfo? = null`, passato come terzo argomento di `AudioCapabilities.getCapabilities` al posto di `null`)
- Modify: `app/src/main/java/org/jellyfin/androidtv/util/profile/deviceProfile.kt:86-89` (`isBitstreamAudioEnabled` passa `preferredVideoAudioDevice(context)`)

**Interfaces:**
- Consumes: `UserPreferences.preferredVideoAudioDevice(context)` da Task 2.
- Produces: `fun isPassthroughAudioAvailable(context: Context, mimetype: String, routedDevice: AudioDeviceInfo? = null): Boolean` (default compatibile con eventuali altri chiamanti).

- [ ] **Step 1:** Applicare le due modifiche.
- [ ] **Step 2: Verifica** con il report del device profile (`deviceProfileReport.kt`, raggiungibile da Impostazioni → informazioni/debug) o con un film AC3/E-AC3: con cassa connessa e preferenza TV, `DirectPlayProfiles`/codec audio includono `ac3`/`eac3` se la TV li supporta; con AUTO tornano come upstream. Riproduzione AC3 con TV: nessun errore, audio sulla TV.
- [ ] **Step 3:** Run `./gradlew :app:testDebugUnitTest` → PASS (nessuna regressione).
- [ ] **Step 4: Commit** `Check audio passthrough against the TV output when it is preferred`.

### Task 5: Pulsante nella schermata del film

**Files:**
- Modify: `app/src/main/java/org/jellyfin/androidtv/ui/itemdetail/FullDetailsFragment.java` (`addButtons(int buttonSize)`, riga ~766)

**Interfaces:**
- Consumes: `UserPreferences.videoAudioOutput`, `VideoAudioOutput`, stringhe da Task 2.

- [ ] **Step 1:** In `addButtons`, solo per elementi video riproducibili (film, episodio, serie, video; non musica/album/artisti/Live TV), aggiungere con `TextUnderButton.create(requireContext(), <icona audio esistente>, buttonSize, 0, <etichetta del valore corrente>, listener)` e `mDetailsOverviewRow.addAction(...)` un pulsante subito dopo Riproduci/Riprendi. Il listener alterna AUTO↔TV in `userPreferences` e aggiorna l'etichetta con `setLabel(...)`.
- [ ] **Step 2: Verifica su dispositivo:** il pulsante compare in un film e in un episodio, non in un album; premendolo l'etichetta cambia subito; uscendo e rientrando nella schermata mostra il valore salvato.
- [ ] **Step 3: Commit** `Add an audio output button to the movie and episode screen`.

### Task 6: Verifica completa sul Chromecast

- [ ] **Step 1:** Build release firmata e install (`adb -s 192.168.1.64:5555 install -r <apk>`), login al server.
- [ ] **Step 2:** Con la cassa connessa verificare tutti i punti della Review Focus, guardando `dumpsys media.audio_flinger` durante la riproduzione:
  - TV → film sulla TV; Automatico → film sulla cassa.
  - Preferenza cambiata e subito Riproduci → vale il nuovo valore.
  - Avvio da "Continua a guardare" con TV → TV.
  - Musica di Jellyfin e Bloomee TV con preferenza TV → cassa.
  - Film AC3/E-AC3 con TV → suona sulla TV, nessun errore.
  - Senza uscita TV non è verificabile sul Chromecast (l'uscita "Chromecast" c'è sempre): coperto dal test unitario del Task 2.
- [ ] **Step 3:** Annotare i risultati nel commit o nelle note di release.

### Task 7: Aggiornamenti automatici dalle release upstream

**Files:**
- Create: `.github/workflows/fork-release.yaml`
- Create: `.fork/base-tag` (contenuto: `v0.20.0-beta.4`)
- Create: `FORK.md` (breve spiegazione del fork in italiano: cosa cambia, come si installa)

**Interfaces:**
- Consumes: secrets del fork `KEYSTORE` (base64), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` — stessi nomi del workflow upstream `app-publish.yaml`.

- [ ] **Step 1:** Workflow con `schedule` (giornaliero) + `workflow_dispatch`: elenca i tag upstream (`git ls-remote --tags https://github.com/jellyfin/jellyfin-androidtv`), considera solo quelli `>=` `v0.20.0-beta.4` in ordine semver ed esclude `v0.19.*`; per ogni tag senza release corrispondente nel fork (`gh release view fork-<tag>`): checkout di `audio-tv`, `git rebase --onto <tag> $(cat .fork/base-tag)`; se il rebase fallisce il job fallisce e non pubblica; altrimenti `JELLYFIN_VERSION=<tag>` `./gradlew assembleRelease` firmato e `gh release create fork-<tag>` con l'APK (`--prerelease` se il tag contiene `-beta`/`-rc`). Setup Java/Gradle come in `app-build.yaml` (`java-version-file: .tools-versions`).
- [ ] **Step 2:** Impostare i quattro secrets con `gh secret set` sul fork dal keystore del Task 1.
- [ ] **Step 3:** Push di `audio-tv` sul fork, impostarlo come branch di default, abilitare Actions sul fork.
- [ ] **Step 4: Verifica:** `gh workflow run fork-release.yaml` → il run crea la release `fork-v0.20.0-beta.4` con l'APK; scaricato e confrontato con `aapt2 dump badging` (package `it.fabiodalez.jellyfintv`).
- [ ] **Step 5: Commit** `Rebuild the fork automatically on new upstream releases`.
