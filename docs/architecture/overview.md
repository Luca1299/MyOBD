# Multipla OBD — Architettura corrente

Architettura implementata documentata sulla versione applicativa 0.3.2.
Aggiornamento documentale del 12 novembre 2026: l’estensione progettata per
l’osservazione continua è descritta separatamente e non è ancora implementata.
OBD significa On-Board Diagnostics. Questa overview descrive il sistema
presente e gli strumenti integrati; non aggiunge
funzionalità alla vettura o all'app.

Le motivazioni sono negli Architecture Decision Record (ADR)
[indicizzati](../adr/README.md); le specifiche vive seguono le convenzioni di
[design](../design/README.md). Il metodo di aggiornamento è in
[AGENTS.md](../../AGENTS.md). La tabella delle versioni e i prerequisiti di
compilazione restano nel [README](../../README.md), i comandi in
[CONTRIBUTING.md](../../CONTRIBUTING.md).

## Moduli e dipendenze

`app` dipende da `obd-core`; il nucleo non dipende dal modulo Android.

- `obd-core`: Kotlin per Java Virtual Machine (JVM), protocollo, decodifica,
  coordinamento della connessione e contratti del trasporto e della persistenza.
  La politica dei tentativi è verificabile con trasporto e tempo simulati.
- `app`: integrazione Android, Bluetooth Classic, persistenza delle preferenze,
  servizio di sessione, interfaccia Jetpack Compose ed esportazione tramite
  il selettore documenti del sistema.

La diagnosi manuale confermata da [app/D-0001](../design/app/D-0001-diagnosi-obd-sola-lettura/README.md)
riusa questi proprietari. `DiagnosticsSession` conserva un solo tentativo
attivo, contesto fissato all’avvio e progressi per richiesta;
`DiagnosisReport` prepara il testo dai risultati conclusi e `DiagnosisContent`
li presenta senza avviare operazioni. La demo mantiene lo stesso servizio
avviato dall’interfaccia visibile, senza Bluetooth o primo piano
`connectedDevice`, anche quando si apre il selettore documenti; non introduce
un secondo proprietario né persistenza dopo la terminazione del processo.
L’interruzione della sequenza attende la richiesta corrente; intento, fase ed
esito conclusivo sono decisi sullo stesso stato atomico. `Scollega` usa il
comando di arresto esistente. Le letture aggregate dei codici conservano le
risposte positive insieme agli eventuali limiti delle altre risposte.
Il Registro esporta dati e scambi correnti, separati dalla fotografia della
diagnosi conclusa.

L’ammissione di una lettura pubblica insieme occupazione e nuovo tentativo,
rivalutando i prerequisiti correnti. Una diagnosi preparata per un collegamento
perso non viene trasferita al successivo; la perdita subito dopo l’ammissione
conclude il tentativo anche se la coroutine non è ancora partita. È una
correzione dell’implementazione di app/D-0001, senza nuove politiche di avvio.

Nella versione 0.3.2 è attuata la conservazione delle diagnosi concluse
prevista da [ADR-0004](../adr/0004-conservazione-risultati-nel-processo.md):
il livello Application conserva l’ultimo risultato reale e quello demo nella
memoria del processo. Il servizio resta proprietario della sessione attiva
e si arresta con Scollega; un servizio ricreato nello stesso processo ritrova
i dati della propria modalità, senza riavviare la diagnosi. La terminazione
del processo elimina il contenitore. L’estensione progettata alle osservazioni
concluse è distinta qui sotto.

Il nucleo resta indipendente da Android e Compose. Il confine è espresso dalle
dipendenze Gradle e dai contratti: non è presente un controllo ArchUnit e questa
documentazione non ne introduce uno. Non si importano core, moduli, server,
database o sistemi di estensione appartenenti a Naedo.

## Responsabilità attuali

| Modulo | Componente | Responsabilità |
| --- | --- | --- |
| obd-core | ConnectionController | Stato della connessione, budget dei tentativi, attese, blocco persistente, gestione degli eventi concorrenti. |
| obd-core | ConnectionStore / AdapterLink | Contratti per profilo, blocco manuale, collegamento e verifica della centralina. |
| obd-core | PromptConnection | Lettore passivo del flusso, risposte fino al prompt, fine del flusso, timeout e cancellazione. |
| obd-core | ElmSession | Inizializzazione adattatore separata dalla ricerca della centralina; richieste diagnostiche serializzate. |
| obd-core | ObdParser / DiagnosticReader / ReadinessDecoder / DtcCatalog / DemoTransport | Decodifica, lettori diagnostici in sola lettura, descrizioni locali verificate e simulatore. |
| app | DiagnosticService | Proprietario della sessione, servizio in primo piano, notifica, wake lock e ciclo di vita indipendente dalla schermata. |
| app | MultiplaApplication / DiagnosisResults | Ultimi risultati conclusi nella memoria del processo, reale e demo separati; nessuna proprietà delle operazioni. |
| app | DiagnosticsSession | Letture, rapporto, registri, isolamento della demo e stato per l'interfaccia. |
| app | AndroidConnectionStore | Persistenza del profilo e del blocco manuale. |
| app | RfcommTransport / ElmAdapterLink | Collegamento Bluetooth Serial Port Profile (SPP) tramite Radio Frequency Communication (RFCOMM) e adattamento al coordinatore. |
| app | MainActivity | Interfaccia Compose, collegamento al servizio, permessi, scelta del profilo e selettore documenti. |

## Proprietà della sessione e dati

ADR-0001 colloca la sessione nel servizio Android `connectedDevice`, avviato
dall'app visibile. L'interfaccia si ricollega alla sessione esistente; la sua
ricreazione non ne crea intenzionalmente una nuova. Il servizio gestisce
notifica, interruzione e mantenimento temporaneo della CPU tramite wake lock;
CPU indica Central Processing Unit. Il coordinatore possiede la politica dei
tentativi, separata dalle operazioni di trasporto e dalle attese.

L'inizializzazione dell'adattatore e la verifica della centralina hanno esiti
distinti. Le richieste diagnostiche sono serializzate. ADR-0002 precisa
l'interpretazione dell'avanzamento della prima ricerca e la durata del blocco
dell'autoconnessione; non introduce tentativi paralleli o un nuovo proprietario
della sessione.

Il profilo del dispositivo e il blocco sono persistiti dal livello Android.
Campione diagnostico e registro non costituiscono uno storico persistente
delle diagnosi. Il rapporto è esportato come testo attraverso il sistema;
la demo è identificata e non modifica il profilo reale o il suo blocco.

## Estensione progettata, non implementata

[app/D-0002](../design/app/D-0002-parametri-motore-in-tempo-reale/README.md)
è in design e consolidato parzialmente. Il periodo nominale numerico rimane
sospeso al punto 15, in attesa delle misure hardware; non è autorizzato il
coding.

L’osservazione continua riusa la sessione posseduta da DiagnosticService,
il coordinamento delle operazioni di DiagnosticsSession, le richieste
serializzate di ElmSession e i decoder esistenti. Non introduce collegamenti
o proprietari operativi paralleli.

La revisione di ADR-0004 estende il modello di conservazione nel livello
Application all’ultima osservazione conclusa reale e demo, separatamente
dalle ultime diagnosi concluse. Sono quattro risultati limitati nella memoria
del processo, senza sequenze di campioni, storico su disco o proprietà delle
operazioni attive.

La continuità reale e la pausa automatica della demo seguono la specifica del
package. La demo non introduce il primo piano connectedDevice o il wake lock
della sessione reale.

L’implementazione corrente rimane quella documentata sulla 0.3.2. La presenza
del package e delle feature non attesta implementazione, esecuzione degli
scenari o collaudo hardware.

## Perimetro diagnostico

La versione corrente esegue letture. Non invia cancellazioni errori, attuazioni
o codifiche. Un esito vuoto valido è distinto da una lettura non conclusiva;
la presenza di una risposta OBD non dimostra che siano state raggiunte tutte
le centraline o la gestione metano.

Il dettaglio dei comandi disponibili, dei limiti diagnostici, della continuità
e delle prove sul telefono è nel README e nei documenti storici di ADR-0001 e
ADR-0002. Le nuove capacità richiedono il design previsto dal metodo;
una nuova decisione durevole richiede anche il relativo ADR.

## Build e verifiche

La build usa Gradle con script Kotlin e wrapper fissato. I test del nucleo
usano JUnit 4 e coroutine con tempo simulato. Il modulo Android include test
locali Robolectric; compilazione e analisi statica Android sono disponibili.
Non è necessaria un'istanza server o un database.

ADR-0003 adotta Cucumber per gli scenari nuovi o modificati e Spotless/ktlint
per la formattazione. Gli strumenti sono integrati secondo il
[package progetto/D-0001](../design/progetto/D-0001-verifiche-automatiche/README.md)
confermato dal proprietario il 10 novembre 2026. Il runner Cucumber nel nucleo
esegue il pilota di connessione attraverso il coordinatore reale e le tre
feature diagnostiche del protocollo. Nel modulo Android sono eseguibili le
due feature della sessione e del rapporto. Trasporto e tempo sono simulati;
le verifiche Compose usano la schermata effettiva con Robolectric. Le suite
JUnit/Robolectric esistenti sono conservate.

Spotless applica ktlint con configurazione comune adatta a Compose ai sorgenti
Kotlin dei due moduli e agli script Gradle Kotlin. Il controllo aggregato
verifica test locali e formattazione senza correggere i sorgenti; l'applicazione
delle correzioni è esplicita. Versioni e comandi provati restano in
`CONTRIBUTING.md`, risultati e limiti nel
[rapporto degli strumenti](../verification/progetto-D-0001-verifica.md) e nel
[rapporto diagnostico](../verification/app-D-0001-verifica.md).

## Evidenze e limiti

I rapporti in `docs/verification/` descrivono verifiche già svolte sulle
versioni indicate. Le evidenze storiche non certificano nuove esecuzioni.
Prove simulate, comportamento Android reale,
adattatore e vettura hanno evidenze distinte.

La diagnosi generica confermata è attuata; accesso specifico Fiat/metano e
attribuzione delle risposte alle centraline restano fuori perimetro.
Nessuna nuova decisione architetturale è stata introdotta per queste letture.
