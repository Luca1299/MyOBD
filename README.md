# Multipla OBD · 0.3.2

OBD significa On-Board Diagnostics; DTC significa Diagnostic Trouble Code,
PID Parameter Identifier e TXT indica il formato di testo semplice.

App Android nativa in Kotlin/Compose per Fiat Multipla 2005 benzina/metano con
impianto originale. Dispositivo destinatario: Motorola Edge 50 Ultra con
Android 17 dichiarato dall'utente. Adattatore previsto: NK Mini ELM327 Bluetooth
Classic, già associato al telefono con nome **OBDII**.

La versione 0.3.2 corregge il blocco della diagnosi quando il collegamento si
perde durante la preparazione dell’avvio. Una richiesta non accettata conserva
l’eventuale risultato precedente e non parte alla riconnessione; resta possibile
un nuovo avvio manuale. Prove e limiti sono nel
[rapporto sull’avvio concorrente](docs/verification/app-D-0001-avvio-verifica.md).

La versione 0.3.1 corregge le risposte diagnostiche miste, l’interruzione durante
la finalizzazione e l’esportazione del Registro dopo letture successive.
Il [rapporto delle correzioni](docs/verification/app-D-0001-correzioni-verifica.md)
riporta regressioni e risultati. La conservazione dei risultati conclusi
fino alla terminazione del processo, anche dopo Scollega, segue
[ADR-0004](docs/adr/0004-conservazione-risultati-nel-processo.md), approvato
dal proprietario con incarico di implementazione.

La versione 0.3.0 attua la [diagnosi manuale in sola lettura](docs/design/app/D-0001-diagnosi-obd-sola-lettura/README.md),
confermata per il coding il 10 novembre 2026. Il
[rapporto app/D-0001](docs/verification/app-D-0001-verifica.md) separa test,
interfaccia simulata e prove fisiche ancora necessarie. Conserva collegamento,
riconnessione e firma della 0.2.1. I rapporti di
[ADR-0002](docs/verification/adr-0002-verifica.md) e
[ADR-0001](docs/verification/adr-0001-verifica.md) restano evidenze storiche delle
versioni precedenti, non certificazioni dell’hardware corrente.

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
confermato per il coding il 10 novembre 2026. Strumenti e scenario pilota sono
integrati; comandi ed evidenze correnti sono in
[CONTRIBUTING.md](CONTRIBUTING.md#controlli-attualmente-disponibili) e nel
[rapporto D-0001](docs/verification/progetto-D-0001-verifica.md).

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

Dopo che la centralina è raggiunta, aprire **Diagnosi**, annotare facoltativamente
`Benzina`, `Metano` o `Non annotata`, e premere **Avvia diagnosi**. Il
collegamento non avvia automaticamente la diagnosi. Sono raccolti in sequenza:

- Spia richiesta dalla centralina, conteggio dichiarato dei codici confermati
  e readiness dei monitor dall’ultima cancellazione.
- Codici memorizzati (03), in attesa (07) e permanenti (0A), con categorie
  separate. Il [catalogo locale verificato](obd-core/docs/dtc-catalog.md)
  descrive P0133, P0171 e P0300; le altre voci mantengono il codice con
  «Descrizione non disponibile».
- Freeze frame 00 (02), con supporto verificato nel contesto memorizzato,
  codice e valori restituiti. Origine delle risposte e associazioni non
  accertabili restano esplicite; nessun valore attuale riempie dati mancanti.
- Un campione dei sette parametri già gestiti, con unità e indisponibilità.

**Completata**, **Parziale** e **Interrotta** descrivono l’acquisizione.
Ogni sezione conserva il proprio esito: `NO DATA` o una risposta malformata
non diventano un elenco senza guasti né una funzione non supportata.
La diagnosi non certifica la salute della vettura o una scansione di tutte
le centraline, e non identifica automaticamente la gestione benzina/metano.

**Interrompi diagnosi** attende la richiesta corrente entro il suo timeout e
non ne invia altre. **Scollega / annulla** nella scheda Collegamento chiude
invece la sessione con il comando esplicito esistente. Risultati acquisiti e
motivo restano consultabili in memoria fino alla terminazione del processo, anche dopo la distruzione del servizio.
La riconnessione non riprende una diagnosi interrotta; serve un nuovo avvio manuale.

Una nuova diagnosi sostituisce il tentativo precedente. **Esporta rapporto .txt**
nella diagnosi conclusa salva la sua copia senza nuove letture,
anche per risultati parziali o interrotti. Il testo contiene contesto, tempi,
annotazione fissata all’avvio, sezioni e registro originale disponibile.
Durante acquisizione e interruzione in corso l’esportazione è indisponibile.
La copia temporanea nella cache privata viene rimossa dopo salvataggio o annullamento.

L’esportazione dal **Registro** include il campione manuale e gli scambi
correnti, con una sezione distinta per l’eventuale diagnosi conclusa.
Il rapporto della diagnosi conserva il proprio contesto anche dopo nuove letture.

La lettura manuale in **Parametri** resta separata e non riscrive il tentativo
diagnostico concluso. Il contenitore in memoria conserva soltanto l’ultimo risultato concluso
reale e quello demo, separati. Non mantiene attive connessioni o servizi;
Android può terminare il processo ed eliminarli.

La **demo** è identificata nella schermata e nel rapporto;
non modifica profilo, blocco o risultati reali con i dati sintetici.

L’app esegue solo letture. Cancellazione, attuazioni, codifica, storico,
confronti e diagnostica specifica Fiat/metano restano fuori da questo taglio.
Non vengono inviati Mode 04, attuazioni o codifiche alla vettura.

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
disinstallare 0.2.0 e installare 0.3.2. Il profilo dell'app e i permessi vanno
configurati nuovamente; l'associazione Bluetooth del telefono resta disponibile.

La chiave debug conservata dalla versione 0.2.1 è inclusa in `signing/debug.keystore` e
configurata nel progetto (alias `androiddebugkey`, password standard `android`).
Conservarla insieme ai sorgenti per firmare gli aggiornamenti successivi con
la stessa chiave. La 0.3.2 usa `versionCode` 6 e mantiene questa firma per aggiornare la 0.2.1.
L’aggiornamento fisico sul Motorola resta da provare.

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
7. Aprire Diagnosi, annotare l’alimentazione e avviare la lettura manuale;
   controllare gli esiti di ogni sezione ed esportare il rapporto TXT.
   Per misurare giri diversi da zero servirà il motore acceso.
8. Eseguire i casi diagnostici e di continuità nel rapporto app/D-0001, oltre
   ai casi di retry e persistenza già richiesti dai rapporti storici.

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
