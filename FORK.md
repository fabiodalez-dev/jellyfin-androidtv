# Jellyfin Audio TV

Questo è il mio fork di [Jellyfin Android TV](https://github.com/jellyfin/jellyfin-androidtv) con una sola aggiunta: nella schermata di un film o di un episodio c'è un pulsante **"Audio: TV / Automatico"**.

Lo uso sul Chromecast with Google TV, che di giorno resta collegato in Bluetooth a una cassa in cucina. Con "Audio: TV" film e serie suonano dalla TV anche con la cassa connessa, Dolby e DTS compresi quando la TV li supporta; con "Automatico" l'app si comporta esattamente come quella ufficiale. La scelta viene ricordata, e non tocca né la musica né le altre app: chiuso il film, la cassa riprende come prima.

L'app si chiama "Jellyfin Audio TV" e ha un package tutto suo (`it.fabiodalez.jellyfintv`), quindi si installa accanto al Jellyfin ufficiale senza sostituirlo. Gli APK firmati sono nelle [release](../../releases): una GitHub Action riapplica la modifica a ogni nuova release ufficiale (dalla 0.20.0-beta.4 in poi) e pubblica l'APK; se la modifica non si applica più pulita, la build si ferma senza pubblicare niente.

Il progetto e il piano di lavoro sono in `docs/superpowers/`.
