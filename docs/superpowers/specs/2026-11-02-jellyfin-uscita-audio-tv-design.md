# Jellyfin Android TV: uscita audio "TV" per film e serie

Data: 2026-11-02 · Destinazione: fork di `jellyfin/jellyfin-androidtv` (va in `docs/superpowers/specs/` del fork quando viene creato)

## Perché

Il Chromecast with Google TV di Fabio (sabrina, Google TV 14, `192.168.1.64`) di giorno è collegato in Bluetooth a una cassa in cucina (Esinkin BT Adapter, `FC:58:FA:4D:75:82`) e l'audio deve uscire lì. La sera Fabio guarda film e serie con Jellyfin davanti alla TV: la cassa è di lato, quindi il suono arriva dalla direzione sbagliata. Vuole che film e serie suonino dalla TV senza disconnettere il Bluetooth (impossibile comunque: il telecomando del Chromecast è Bluetooth LE) e senza passare dalle impostazioni di sistema.

Né Google TV né Jellyfin Android TV (0.19.10 stabile, 0.20.0-beta.4 in sviluppo) offrono una scelta dell'uscita audio. Nessuna issue o PR upstream lo propone; l'unica richiesta simile è la #1656 del 2022, chiusa senza implementazione. Jellyfin non ha plugin lato client: i plugin girano sul server e non possono toccare l'audio del Chromecast.

## Cosa è già stato verificato sul dispositivo

- Con la cassa Bluetooth connessa, una `AudioTrack` di un processo qualsiasi che chiama `setPreferredDevice(...)` sull'uscita "Chromecast" suona sulla TV: `getRoutedDevice()` riporta `type=2 Chromecast`, `dumpsys media.audio_flinger` riporta `AUDIO_DEVICE_OUT_SPEAKER`, e Fabio ha confermato a orecchio che il suono esce dalla TV. Non servono permessi speciali né ADB.
- Alle app il Chromecast **non espone un dispositivo HDMI**: `getDevices(GET_DEVICES_OUTPUTS)` restituisce solo `TYPE_BUILTIN_SPEAKER` ("Chromecast", è l'uscita verso la TV) e `TYPE_BLUETOOTH_A2DP` ("Esinkin BT Adapter").
- La preferenza vale solo per la traccia che la chiede: finita la traccia, il routing di sistema resta quello di prima.

## Comportamento voluto

Nella schermata di dettaglio di un film (e di un episodio, che usa la stessa schermata) compare, tra i pulsanti d'azione, un pulsante che mostra l'uscita scelta: **"Audio: TV"** oppure **"Audio: Automatico"**. Premerlo passa da un valore all'altro e aggiorna subito l'etichetta.

La scelta è **ricordata**: vale per tutti i video riprodotti dopo, finché Fabio non la cambia, anche se il video viene avviato senza passare dalla schermata di dettaglio (per esempio da "Continua a guardare" in home).

- **TV**: il player video chiede a ExoPlayer di suonare sull'uscita verso la TV. Ordine di ricerca tra le uscite disponibili: `TYPE_BUILTIN_SPEAKER`, poi `TYPE_HDMI`, `TYPE_HDMI_ARC`, `TYPE_HDMI_EARC`. Aggiornamento dopo la verifica sul dispositivo: sul Chromecast l'uscita HDMI (che a volte compare tra le uscite) viene registrata come preferita ma Android resta sulla cassa Bluetooth, mentre lo speaker "Chromecast" è la TV e viene rispettato. Se nessuna è presente, nessuna preferenza (il video suona dove suonerebbe comunque).
- **Automatico** (predefinito, comportamento attuale): nessuna preferenza, l'audio segue il sistema, quindi la cassa Bluetooth se è connessa.

Fuori dal video di Jellyfin non cambia niente: la musica di Jellyfin, Bloomee TV e tutte le altre app continuano a seguire il routing di sistema. Nessuna impostazione di sistema viene modificata, quindi non c'è niente da ripristinare.

## Obiettivi

- Scegliere TV o Automatico dalla schermata del film, con la scelta ricordata.
- Film ed episodi suonano dalla TV con il Bluetooth connesso quando la scelta è TV.
- Patch **piccola e isolata**, facile da riapplicare sulle release ufficiali.
- Compilazione automatica dell'APK patchato a ogni nuova release upstream.

## Non obiettivi

- Scegliere tra più dispositivi Bluetooth o scegliere la cassa come destinazione esplicita: "Automatico" basta.
- (Superato: Fabio lo ha chiesto dopo la prima prova. Il player ha ora un pulsante "Uscita audio" che cambia la scelta e la applica subito al film in corso.)
- Toccare la musica di Jellyfin o il nuovo motore di riproduzione (`playback/media3/exoplayer/ExoPlayerBackend.kt`), che oggi su 0.20 serve la musica; per il video è attivo solo se l'opzione sperimentale `playback_new` è accesa (spenta di default).
- Instradamento di sistema, servizi con permessi della shell, overlay: scartati in questa conversazione perché la scelta dentro Jellyfin basta.
- Proporre la funzione upstream (possibile in futuro, non fa parte di questo lavoro).

## Anti-obiettivi (fallimento anche se "funziona")

- Il film resta muto o l'audio si interrompe quando l'uscita TV non c'è o sparisce: in quel caso deve suonare dove suonerebbe senza patch.
- La patch modifica il routing di sistema e lascia la cassa della cucina "staccata" dopo un film.
- La patch si sparge in molti file o riscrive codice upstream, rendendo ogni aggiornamento un lavoro manuale.

## Vincoli

- Base: tag `v0.20.0-beta.4` di `jellyfin/jellyfin-androidtv` (ultimo commit su master 2026-10-29), così la patch non va rifatta all'uscita della 0.20 stabile. Fabio è d'accordo a basarsi sulla 0.20.
- Dispositivo: Android 14 (API 34), Media3 ExoPlayer.
- Installazione: sideload via ADB sul Chromecast. Il fork usa un **applicationId diverso** da `org.jellyfin.androidtv` e un nome app distinguibile, così convive con la 0.19.10 ufficiale del Play Store; Fabio toglierà l'ufficiale quando sarà convinto (decisione: Fabio ha chiesto di procedere con la mia raccomandazione). Eventuali `authorities` o riferimenti che dipendono dal package vanno adeguati perché le due app non vadano in conflitto.
- **Surround**: con "Audio: TV" il Dolby/DTS deve poter arrivare alla TV in passthrough come farebbe senza cassa Bluetooth (decisione: Fabio ha chiesto di includere anche questo). Oggi `util/profile/deviceProfile.kt` decide la capacità di passthrough tramite `isPassthroughAudioAvailable()` in `util/profile/codec/audioPassthrough.kt`, che interroga `AudioCapabilities` sull'uscita predefinita (la cassa, di sera). Con la preferenza TV quel controllo deve interrogare l'uscita TV scelta. Il passthrough verso l'uscita "Chromecast" non è ancora stato provato sul dispositivo: va verificato con un file AC3/E-AC3; se il dispositivo non lo supporta, il comportamento corretto è decodificare in PCM sulla TV, non fallire.
- Testi rivolti all'utente in italiano, con le stringhe aggiunte anche in inglese come base.
- Niente attribuzioni AI in commit, PR o release del fork; tutto a nome di Fabio, in prima persona singolare.

## Punti di aggancio nel codice (verificati su `v0.20.0-beta.4`)

- **Preferenza**: `app/src/main/java/org/jellyfin/androidtv/preference/UserPreferences.kt`, che definisce le preferenze con `enumPreference(...)` / `booleanPreference(...)`. Una nuova preferenza (per esempio `enumPreference("video_audio_output", VideoAudioOutput.AUTO)` con un enum in `preference/constant/`).
- **Player video**: `app/src/main/java/org/jellyfin/androidtv/ui/playback/VideoManager.java`. Il costruttore legge già `UserPreferences` e crea il player con `configureExoplayerBuilder(activity, assHandler).build()` (riga ~104). Dopo la creazione, se la preferenza è TV, cercare l'uscita con `AudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)` nell'ordine sopra e chiamare `mExoPlayer.setPreferredAudioDevice(device)`.
- **Pulsante**: `app/src/main/java/org/jellyfin/androidtv/ui/itemdetail/FullDetailsFragment.java`, metodo `addButtons(int buttonSize)` (riga ~766), dove i pulsanti si creano con `TextUnderButton.create(context, icona, buttonSize, maxLines, etichetta, listener)` e si aggiungono con `mDetailsOverviewRow.addAction(...)`.

## Automazione degli aggiornamenti

Il fork tiene la patch come uno o pochi commit sopra il tag upstream. Una GitHub Action pianificata controlla le nuove release di `jellyfin/jellyfin-androidtv`; per ogni nuovo tag riapplica la patch (rebase o cherry-pick), compila l'APK e lo pubblica come release del fork. Se la patch non si applica pulita, l'Action fallisce e segnala il conflitto senza pubblicare nulla.

Tag da compilare (scelta mia): tutti i tag upstream uguali o successivi a `v0.20.0-beta.4`, con beta e RC pubblicate come pre-release del fork; i tag di manutenzione `0.19.x` vengono ignorati perché sono più vecchi della base della patch.

## Lasciato a chi costruisce

- Nomi di classi, enum e chiavi di preferenza; icona del pulsante (una di quelle già nel progetto va bene).
- Posizione esatta del pulsante nella riga delle azioni, purché sia nella schermata di dettaglio di film ed episodi e non nelle schermate musicali.
- Se aggiungere la stessa impostazione anche nelle Impostazioni → Riproduzione (utile, non richiesto).
- Firma dell'APK e dettagli della GitHub Action (cron, nome degli artefatti).
