# Multipla OBD — Architettura corrente

Stato documentato il 10 novembre 2026 sulla versione applicativa 0.2.1.
OBD significa On-Board Diagnostics. Questa overview descrive il sistema
presente e distingue le scelte di strumenti ancora da attuare; non aggiunge
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
| obd-core | ObdParser / DemoTransport | Decodifica OBD e simulatore. |
| app | DiagnosticService | Proprietario della sessione, servizio in primo piano, notifica, wake lock e ciclo di vita indipendente dalla schermata. |
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
per la formattazione. Alla data di questa overview gli strumenti non sono
ancora integrati: è stato consolidato soltanto il design, compreso un pilota
Gherkin senza runner o step. Il relativo
[package progetto/D-0001](../design/progetto/D-0001-verifiche-automatiche/README.md)
è `in design`, in attesa della conferma separata per il coding. Questo stato
non implica un cambiamento delle dipendenze applicative o dei test esistenti.

## Evidenze e limiti

I rapporti in `docs/verification/` descrivono verifiche già svolte sulle
versioni indicate. Non sono esiti della presente adozione documentale e non
certificano nuove esecuzioni. Prove simulate, comportamento Android reale,
adattatore e vettura hanno evidenze distinte.

Non vengono risolti in questa sessione i temi diagnostici rinviati nei
documenti precedenti. Nessun nuovo punto architetturale resta aperto nel
perimetro del consolidamento del metodo.
