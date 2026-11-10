# Multipla OBD · 0.2.1

App Android nativa in Kotlin/Compose per Fiat Multipla 2005 benzina/metano con
impianto originale. Dispositivo destinatario: Motorola Edge 50 Ultra con
Android 17 dichiarato dall'utente. Adattatore previsto: NK Mini ELM327 Bluetooth
Classic, già associato al telefono con nome **OBDII**.

La versione 0.2.1 applica [ADR-0002](docs/adr/0002-primo-collegamento-e-riabilitazione-automatica.md),
approvato da Luca il 10 novembre 2026, al collegamento definito da
[ADR-0001](docs/adr/0001-collegamento-android-elm327.md), approvato il 9 novembre.
Il [rapporto ADR-0002](docs/verification/adr-0002-verifica.md) riporta regressioni,
controlli automatici e prove da ripetere sul dispositivo reale. Il
[rapporto ADR-0001](docs/verification/adr-0001-verifica.md) resta l'evidenza storica
della versione 0.2.0.

## Orientarsi nello sviluppo

- [Istruzioni per agenti e sessioni](AGENTS.md): discussione, `APPROVO`,
  domande numerate e conferma delle specifiche.
- [Contribuire](CONTRIBUTING.md): lingua, commit, versioni, comandi e consegne.
- [Stile del codice](docs/guides/code-style.md): regole normative Kotlin/Android.
- [Architettura corrente](docs/architecture/overview.md): moduli e responsabilità.
- [Indice ADR](docs/adr/README.md): Architecture Decision Record, motivazioni
  e ciclo di vita delle decisioni.
- [Specifiche vive](docs/design/README.md): design package e stato di conferma.

Il metodo è adottato con [ADR-0003](docs/adr/0003-metodo-di-sviluppo-e-verifiche.md).
L'integrazione di Cucumber e della formattazione automatica è specificata in
[progetto/D-0001](docs/design/progetto/D-0001-verifiche-automatiche/README.md),
ancora `in design`: il consolidamento documentale non li rende già operativi.

## Collegamento e riconnessione

- Alla prima configurazione scegliere OBDII tra i dispositivi già associati e
  premere **Salva dispositivo OBDII**. Il profilo conserva nome e identificativo
  Bluetooth dello specifico adattatore.
- Alle aperture successive l'app tenta automaticamente il collegamento al
  dispositivo salvato. Un dispositivo omonimo con un identificativo diverso
  non viene scelto come sostituto.
- Ogni ciclo automatico comprende **3 tentativi totali: 1 iniziale + 2 retry**.
  Fra un fallimento e il tentativo successivo trascorrono **3 secondi**.
- Dopo il terzo fallimento viene richiesta la connessione manuale. Il blocco
  persiste anche chiudendo e riaprendo l'app.
- **Connetti manualmente** esegue un singolo tentativo esplicito. Il successo
  rimuove il blocco persistente e riabilita l'autoconnessione futura. Un
  fallimento conserva lo stato precedente del blocco: se l'automatismo era
  abilitato, non viene disabilitato da un solo fallimento manuale. Non parte
  un ciclo automatico immediato dopo quel fallimento.
- Una perdita reale di una sessione attiva avvia un nuovo ciclo con gli stessi
  limiti. Il trasporto rileva anche EOF mentre è inattivo, senza interrogare
  periodicamente la centralina soltanto per rilevare la disconnessione.
- Se l'adattatore risponde ma la centralina non fornisce dati, la connessione
  all'adattatore rimane attiva. **Riprova verifica centralina** ripete la verifica
  sulla stessa connessione.
- La prima risposta reale `SEARCHING...\r..4100BE3EB81100\r>` viene riconosciuta
  senza richiedere un secondo comando. I puntini vengono trattati come
  avanzamento soltanto dopo il messaggio di ricerca e prima del primo dato;
  dati danneggiati o incompleti restano errori. Un errore di lettura viene
  distinto dalla centralina senza risposta.
- Il registro esplicita tentativi automatici/manuali, sospensione dopo tre
  fallimenti, riabilitazione dopo il successo manuale ed esito della centralina.
- Bluetooth spento, permesso assente o profilo mancante producono uno stato
  esplicito e non consumano tentativi.

La sessione reale è posseduta da un servizio Android `connectedDevice`, con
notifica e comando **Interrompi**. Passare a un'altra app o spegnere lo schermo
non chiude intenzionalmente la connessione. Un wake lock temporaneo, rinnovato
solo durante il lavoro di sessione, sostiene la continuità a schermo spento;
viene rilasciato all'interruzione o quando non è necessario. La durata massima
del singolo tentativo di apertura RFCOMM resta 15 secondi, distinta dalla pausa
fra tentativi e dai timeout ELM/OBD.

Il servizio viene avviato dall'interfaccia visibile. Non è previsto l'avvio
per sola vicinanza quando l'app non è stata aperta. Arresto forzato dell'app e
terminazione del processo da parte del sistema richiedono una nuova apertura.
Il comportamento effettivo a schermo spento va ancora verificato sul Motorola.

## Diagnostica disponibile

- Identificazione dichiarata dall'adattatore, protocollo e PID disponibili.
- Campione manuale dei parametri supportati: spia motore, numero di DTC
  confermati, giri, temperatura refrigerante, velocità, correzioni carburazione
  banco 1, pressione collettore e posizione farfalla.
- Annotazione manuale dell'alimentazione benzina/metano.
- DTC memorizzati (03), in attesa (07) e permanenti (0A), se disponibili.
- Esiti non conclusivi distinti dagli elenchi di errori effettivamente vuoti.
- Registro e rapporto TXT esportabili attraverso il selettore documenti Android.
- Demo con dati sintetici, segnalata nell'interfaccia e nel rapporto. La demo
  non modifica il profilo o il blocco manuale del dispositivo reale.

L'app esegue letture. Cancellazione errori, freeze frame, storico delle scansioni,
confronto automatico e diagnostica specifica Fiat/metano richiederanno un design
dedicato e un ADR quando introducono una decisione durevole da motivare.
Non vengono inviati Mode 04, attuazioni o codifiche alla vettura.

I parametri sono letti in sequenza. Il campione successivo sostituisce quello
precedente nell'interfaccia: esportare il rapporto per conservarlo. Cambiare
l'etichetta benzina/metano invalida i valori del campione precedente.
L'esportazione prepara una copia temporanea nella cache privata prima di aprire
il selettore documenti; la copia viene rimossa dopo il salvataggio o l'annullamento.
La demo è pensata per la prova dell'interfaccia; non certifica la connessione reale.

## Aprire e compilare

Aprire questa cartella in Android Studio e completare la sincronizzazione.
Usare JDK 17 e installare Android SDK Platform 36 e SDK Build-Tools 35.0.0.

| Componente | Versione |
| --- | --- |
| Gradle | 8.13, wrapper incluso con checksum |
| Android Gradle Plugin | 8.13.2 |
| Kotlin e plugin Compose | 2.2.20 |
| Compose BOM | 2025.08.00 |
| Coroutines | 1.10.2 |
| Robolectric, solo test | 4.17 |
| minSdk | 31, Android 12 |
| compileSdk / targetSdk | 36 |

Eseguire su Android 17 e scegliere targetSdk 37 sono operazioni distinte.
Questa versione conserva target 36; l'esecuzione sul telefono Android 17 va
validata. EOF e IOException sono entrambi gestiti nel trasporto.

I comandi per test, compilazione e analisi statica sono in
[CONTRIBUTING.md](CONTRIBUTING.md#controlli-attualmente-disponibili).

Su Windows usare `gradlew.bat`. La prima esecuzione richiede Internet per le
dipendenze e il runtime Android simulato di Robolectric. I test Robolectric non
richiedono un emulatore e usano API 35; non sostituiscono una prova su Android 17.

APK generato: `app/build/outputs/apk/debug/app-debug.apk`.
L'APK distribuito separatamente è una build debug. La chiave originale di 0.2.0
non è disponibile nei file recuperati: **0.2.1 ha una firma diversa e richiede
la reinstallazione**. Esportare prima eventuali rapporti da conservare, quindi
disinstallare 0.2.0 e installare 0.2.1. Il profilo dell'app e i permessi vanno
configurati nuovamente; l'associazione Bluetooth del telefono resta disponibile.

La chiave debug di questa versione è inclusa in `signing/debug.keystore` e
configurata nel progetto (alias `androiddebugkey`, password standard `android`).
Conservarla insieme ai sorgenti per firmare gli aggiornamenti successivi con
la stessa chiave. Le versioni future devono anche incrementare `versionCode`.

## Prima prova sul Motorola

1. Con auto ferma, inserire l'adattatore e portare il quadro su ON.
2. Verificare che **OBDII** sia associato dalle impostazioni Bluetooth.
3. Aprire l'app e concedere il permesso per i dispositivi nelle vicinanze.
   Consentire anche le notifiche per vedere il controllo della sessione.
4. Selezionare OBDII, controllare il suffisso ID e premere **Salva dispositivo
   OBDII**. Il collegamento viene avviato automaticamente.
5. Distinguere lo stato dell'adattatore da quello della centralina. Se il primo
   risponde e la seconda no, usare **Riprova verifica centralina** quando pronta.
6. Passare a un'altra app e spegnere lo schermo; tornare all'app e controllare
   che la sessione sia ancora attiva. **Interrompi** nella notifica la chiude.
7. Leggere parametri ed errori ed esportare il rapporto dalla scheda Registro.
   Per misurare giri diversi da zero servirà il motore acceso.
8. Eseguire i casi di retry e persistenza descritti nel rapporto di verifica.

La negazione delle notifiche è gestita separatamente dal permesso Bluetooth;
la rappresentazione del servizio è soggetta alle regole Android. L'app non
richiede scansione Bluetooth, posizione, Internet o storage generale.

## Architettura

Moduli, dipendenze e proprietà della sessione sono descritti
nell'[overview](docs/architecture/overview.md), fonte di verità architetturale.
Le motivazioni delle decisioni sono negli ADR; i rapporti di verifica
rimangono evidenze delle versioni a cui si riferiscono.

## Limiti diagnostici

- Il parser usa ATH0 e la formattazione CAN automatica dell'ELM; gestisce dati
  compatti/spaziati e blocchi CAN numerati con lunghezza dichiarata.
- La stringa firmware è il valore dichiarato dall'adattatore, non una
  certificazione della sua implementazione.
- Le letture PID usano la prima risposta positiva valida per il PID. I DTC di
  più risposte sono uniti e deduplicati; con ATH0 non vengono attribuiti a una
  specifica centralina di origine.
- Se ATDPN non consente di riconoscere il protocollo, l'interpretazione dei DTC
  viene sospesa. I servizi 07 e 0A possono non essere disponibili sulla vettura.
- Una risposta OBD non dimostra che sia stata raggiunta anche la gestione
  metano. Pressione bombole, elettrovalvole e alimentazione attuale non vengono
  dedotte da parametri generici.
- Il registro è limitato alle ultime 400 voci; la schermata mostra le ultime
  120. I dati non sono uno storico persistente delle diagnosi.
- Hardware NK, centraline effettive e continuità sul Motorola sono ancora da
  validare con una prova reale.

## Regole di lavoro

Il metodo normativo è in [AGENTS.md](AGENTS.md), con le convenzioni degli
[ADR](docs/adr/README.md), delle [specifiche](docs/design/README.md) e dello
[stile](docs/guides/code-style.md). Questo README resta la guida d'uso e di
compilazione dell'app.
