# Verifica di app/D-0001 — Diagnosi OBD motore in sola lettura

Data: 10 novembre 2026. OBD significa On-Board Diagnostics, DTC significa
Diagnostic Trouble Code, PID significa Parameter Identifier, BDD significa
Behavior-Driven Development. TXT indica il formato di testo semplice;
JVM indica Java Virtual Machine, JDK Java Development Kit, SDK Software
Development Kit, API Application Programming Interface e BOM Bill of Materials.
CC0 indica Creative Commons Zero.

## Conferma, perimetro e consegna precedente

Il proprietario ha confermato esplicitamente il package per il coding e
incaricato di registrarne la conferma e attuarlo. Registrazione nel
[package](../design/app/D-0001-diagnosi-obd-sola-lettura/README.md) e
nell’[indice](../design/README.md). Nessuna domanda aperta; nessuna nuova
scelta funzionale o architetturale aggiunta. Gli Architecture Decision Record
(ADR) precedenti sono conservati.

La consegna di progetto/D-0001 è stata verificata leggendo specifica, rapporto,
configurazione e runner e rieseguendo `:check :app:assembleDebug :app:lintDebug
--rerun-tasks --console=plain` prima dell’implementazione. Risultato iniziale:
72 test superati (62 nel nucleo, 10 nell’app), formattazione, compilazione e
analisi statica positive. La nuova integrazione riusa Cucumber, Spotless,
ktlint e il controllo aggregato; non introduce una pipeline alternativa.

Versione applicativa risultante: **0.3.0**, `versionCode` **4**. Incremento
minor per capacità nuova compatibile, firma debug della 0.2.1 conservata.
Nessun aggiornamento generale dello stack, nessuna pubblicazione o prova sul
telefono del proprietario. Nessun comando Git, commit o push eseguito.
È stato richiesto il pannello nativo di revisione delle modifiche della chat.

## Attuazione e contratti

`DiagnosticReader` usa `ElmSession`, il parser e il trasporto esistenti. Le
richieste ammesse alla vettura restano nei servizi 01, 02/frame 00, 03, 07 e
0A. L’inizializzazione dell’adattatore conserva il percorso precedente. La
serializzazione non cambia; non esiste un comando arbitrario né un servizio
di cancellazione, attuazione o codifica.

Readiness e stato spia provengono da PID 01; i bit di supporto e completamento
restano distinti, anche fra accensione comandata e compressione. Freeze frame
00 legge codice, supporto e valori nel proprio contesto. Risposte multiple
ambigue non vengono unite in un frame coerente; l’identità della centralina,
l’associazione fra risposte e l’ora del guasto restano non verificate quando
ATH0 non permette di accertarle. Le risposte `NO DATA`, vuote e malformate non
provano assenza o mancato supporto. Il mancato supporto usa evidenze negative
interpretabili del servizio richiesto, senza deduzioni dal silenzio.

`DiagnosticsSession` conserva un solo tentativo in memoria, annotazione e
contesto fissati all’avvio, tempi, progressi e dati parziali. Anche i valori
ricevuti a metà di una sezione sono conservati in caso di perdita successiva.
Le risposte e gli errori tardivi di un’operazione precedente non modificano
una nuova acquisizione. L’interruzione della sequenza attende il comando in
corso entro il suo timeout; `Scollega` usa l’arresto esplicito esistente.
Il ripristino del collegamento non avvia una nuova diagnosi.

`DiagnosisContent` riusa card e Material/Compose; testi della nuova schermata
nelle risorse Android, selettore disposto su più righe quando necessario,
scorrimento e azioni con etichette testuali. Il rapporto usa la fotografia
conclusa senza interrogazioni ulteriori, con valori attuali separati dal frame.
La lettura manuale Parametri non riscrive il tentativo concluso.

Il [catalogo locale](../../obd-core/docs/dtc-catalog.md) contiene tre voci
verificate (P0133, P0171, P0300), con fonte, revisione immutabile, traduzione
italiana e dichiarazione CC0 1.0 inclusa nelle risorse distribuite. Non è un
catalogo completo: le altre voci restano visibili senza descrizione inventata.
Non sono importati cause, costi, suggerimenti o codice software dalla fonte.

## Test prima del codice e regressioni

1. Runner diagnostici aggiunti prima del codice applicativo: **24 scenari del
   nucleo e 27 dell’app scoperti ma indefiniti**; invocazione fallita come
   previsto. Il test del comando `020200` falliva per asserzione sul codice
   precedente. Nessun errore di compilazione in questa prova iniziale.
2. Step, test di servizio e cinque prove Compose scritti prima dei componenti
   applicativi che verificano. Tutte le cinque feature approvate sono rimaste
   identiche; scenari e traduzioni non sono stati adattati per ottenere il verde.
3. Prove tecniche successive hanno riprodotto tre difetti: dati persi a metà
   di frame/campione e vecchio errore di trasporto attribuito a una nuova
   acquisizione. Tre fallimenti comportamentali sul codice prima della
   correzione; tutti verdi dopo. Una prima attesa della fixture era troppo
   breve ed è stata corretta prima della ripetizione definitiva delle tre
   regressioni, senza confonderla con il difetto applicativo.
4. L’interruzione immediata, prima della prima richiesta schedulata, falliva
   perché avviava comunque PID 01; corretta e verificata verde. Timeout
   durante interruzione provato un millisecondo prima della scadenza e alla
   scadenza effettiva del comando: nessuna richiesta successiva e risultato
   interrotto, con chiusura e gestione del collegamento esistenti.
5. La prova effettiva del selettore documenti ha rilevato la perdita della
   demo quando l’interfaccia si scollegava da un servizio solo associato.
   Regressione Robolectric riprodotta prima della correzione. La demo avvia
   ora lo stesso servizio dall’interfaccia visibile senza richiedere Bluetooth
   o un primo piano connectedDevice. Il ritorno dal selettore conserva il
   tentativo; l’uscita dalla demo e lo scollegamento conservano la gestione
   dell’arresto. Non vengono introdotti un altro proprietario o uno storico.
6. Prova negativa del nuovo runner Android: filtro tag senza corrispondenze,
   uscita 1 e `IllegalStateException: Nessuno scenario Cucumber scoperto` nel
   rapporto JUnit. Nessun errore di compilazione; runner ripristinato byte
   per byte. La console sintetica mostrava solo classe e riga: la causa è
   stata verificata nel rapporto, non dedotta dal solo codice di uscita.

## Ambiente e comandi

macOS arm64, JDK Amazon Corretto 17.0.19, wrapper Gradle 8.13, Android Gradle
Plugin 8.13.2, Kotlin/Compose plugin 2.2.20. Dipendenze applicative e firma
rimaste fissate. Cucumber Java/JUnit 7.29.0, Spotless 8.3.0, ktlint 1.8.0,
Robolectric 4.17; Compose test 1.9.0 risolto dal BOM 2025.08.00 già adottato.
Le versioni dei test sono state riscontrate con
`:app:dependencies --configuration debugUnitTestRuntimeClasspath`.

Prima prova Gradle limitata dall’accesso alla cache locale, precedente a ogni
test: ripetuta con accesso all’ambiente installato. Non viene conteggiata come
regressione. Tutte le invocazioni effettive hanno usato JDK 17 e lo SDK Android
installato. Comandi pertinenti, dalla root:

```sh
./gradlew :obd-core:test :app:testDebugUnitTest --console=plain
./gradlew :app:testDebugUnitTest --tests it.multipla.obd.diagnostics.DiagnosisRobustnessTest --console=plain
./gradlew :obd-core:test --tests it.multipla.obd.core.DiagnosticProtocolTest --console=plain
./gradlew :app:testDebugUnitTest --tests it.multipla.obd.SessionServiceTest --console=plain
./gradlew :spotlessApply --console=plain
./gradlew :check :app:assembleDebug :app:lintDebug --rerun-tasks --console=plain
```

L’applicazione di Spotless è stata esplicita. Il primo passaggio ha rilevato
gli import con asterisco dei nuovi test: resi espliciti senza soppressioni.
L’ultimo completamento dei commenti dei contratti ha richiesto un ulteriore
riallineamento degli spazi; il controllo lo ha rilevato prima del verde finale.
Il controllo finale verifica senza correggere i sorgenti. La formattazione
storica già consegnata da progetto/D-0001 non è stata rifatta o mescolata con
una nuova riscrittura generale.

## Risultati automatici finali

**140 test superati, zero fallimenti, errori o test saltati**. Controllo
aggregato, formattazione, compilazione del pacchetto Android e lint positivi.
I nomi dei 72 test iniziali sono stati confrontati e tutti conservati.

| Modulo | Iniziali | Finali | Aggiunte |
| --- | ---: | ---: | --- |
| obd-core | 62 | 90 | 24 scenari diagnostici, un test sui comandi, tre test di protocollo |
| app | 10 | 50 | 27 scenari, cinque test Compose, due test servizio, sei prove di robustezza |
| Totale | 72 | 140 | 68 |

Cucumber esegue il pilota precedente e tutti i 51 scenari diagnostici,
compresi gli esempi degli outline. Report diagnostici: 24 scenari/115 step
nel nucleo; 27 scenari/134 step nell’app; tutti superati, nessuno indefinito, ambiguo,
pendente o saltato. Non basta il conteggio dei test JUnit: sono stati letti
anche i risultati Cucumber e confrontati i tag con le feature di design.

Lint: zero errori e **11 avvisi**, nove su versioni aggiornabili (inclusi i
due nuovi riferimenti test Cucumber) e due UseKtx già presenti. Deprecazioni
Cucumber JUnit 4 e API Robolectric per lo schermo: percorso conservato come
richiesto, senza migrare a JUnit 5 o aggiornare lo stack per nascondere gli avvisi.

Risultati normalizzati e impronte:
[app-D-0001-risultati.json](app-D-0001-risultati.json). Report rigenerabili:

- `obd-core/build/reports/tests/test/index.html` e `build/reports/cucumber/diagnostics.json`/`.html`.
- `app/build/reports/tests/testDebugUnitTest/index.html` e `build/reports/cucumber/diagnosis.json`/`.html`.
- `app/build/reports/lint-results-debug.html`/`.xml`.
- `app/build/outputs/apk/debug/app-debug.apk`.

## Interfaccia e integrazione Android simulate

Emulatore Android API 36.1, arm64, display 1080×2400 a 420 punti per pollice; avvio in copia
sola lettura dell’ambiente esistente. Le prove usano la demo, nessun adattatore
reale. Verificati sulla schermata effettiva:

- Prerequisito mancante e avvio disabilitato; avvio disponibile dopo la demo,
  solo manuale, annotazione catturata nel tentativo.
- Riepilogo parziale perché la demo restituisce NO DATA per i permanenti;
  codice P0171 e descrizione/fonte, categorie distinte, stati readiness.
- Freeze frame 1024 giri/min e 20 km/h distinto dal campione attuale
  820 giri/min e 0 km/h; limiti di origine e data del guasto esposti.
- Scorrimento sino all’esportazione e testo al 200%, selettore alimentazione
  su più righe e azioni raggiungibili. Barra di stato resa leggibile sul tema scuro.
- Salvataggio effettivo tramite selettore documenti, contenuto del file
  verificato e copia privata temporanea rimossa; demo e tentativo conservati
  al ritorno. Contesto e tempi corrispondono al risultato mostrato.

Acquisizione, interruzione in attesa e stato concluso parziale sono verificati
anche dalle cinque prove Compose con Robolectric, con controlli su etichette,
azioni e abilitazione. La continuità di una diagnosi attiva e del wake lock
reale è simulata nei test del servizio; questo non certifica il telefono.
Evidenze della build finale, tutte con dati sintetici:

- [Prerequisito mancante](app-D-0001-ui/prerequisito-mancante.png).
- [Riepilogo demo](app-D-0001-ui/demo-riepilogo.png).
- [Parametri ed esportazione](app-D-0001-ui/parametri-esportazione.png).
- [Caratteri al 200%](app-D-0001-ui/caratteri-200.png) e [tentativo conservato dopo ricreazione](app-D-0001-ui/caratteri-200-risultato.png).
- [TXT salvato tramite il selettore Android](app-D-0001-demo.txt).

Il file finale salvato è di 4.452 byte: contenuto italiano, stesso tentativo
#1 e contesto Benzina, valori e tempi corrispondenti alla schermata. La cache
privata è risultata vuota dopo il salvataggio. Il tentativo è rimasto lo stesso
anche dopo la ricreazione dovuta al testo al 200%. Firma e integrità del
pacchetto sono state verificate con `apksigner`; la dichiarazione CC0 è
presente nel pacchetto come `licenses/OBDex-CC0-1.0.txt`.

## Hardware ancora da verificare

Nessuna installazione sul Motorola o prova con NK Mini e vettura. Restano:

- Aggiornamento fisico dalla 0.2.1, permessi e associazione dell’adattatore.
- Primo collegamento, letture 01/02/03/07/0A effettivamente supportate dalle
  centraline raggiunte, framing, timeout e risposte multiple reali.
- Confronto di spia richiesta, codici, readiness e freeze frame con uno
  strumento attendibile e con i limiti di origine esposti dall’app.
- Interruzione durante una richiesta, scollegamento, perdita e ripristino del
  Bluetooth, avvio manuale successivo e conservazione dei risultati disponibili.
- Navigazione, ricreazione dell’interfaccia, passaggio ad altra app e schermo
  spento con diagnosi attiva sul Motorola; notifica, wake lock e batteria.
- Esportazione e leggibilità/accessibilità sulle impostazioni reali del telefono.

Le simulazioni non dimostrano compatibilità Bluetooth, accesso alla gestione
metano, completezza delle centraline o correttezza delle risposte hardware.
La terminazione del processo non conserva la diagnosi: serve l’esportazione.

## File della consegna

Elenco delle sole scritture della sessione, non stato dell’indice Git.

### Modificati — 13

- [CONTRIBUTING.md](../../CONTRIBUTING.md)
- [README.md](../../README.md)
- [app/build.gradle.kts](../../app/build.gradle.kts)
- [app/src/main/java/it/multipla/obd/DiagnosticService.kt](../../app/src/main/java/it/multipla/obd/DiagnosticService.kt)
- [app/src/main/java/it/multipla/obd/DiagnosticsSession.kt](../../app/src/main/java/it/multipla/obd/DiagnosticsSession.kt)
- [app/src/main/java/it/multipla/obd/MainActivity.kt](../../app/src/main/java/it/multipla/obd/MainActivity.kt)
- [app/src/test/java/it/multipla/obd/SessionServiceTest.kt](../../app/src/test/java/it/multipla/obd/SessionServiceTest.kt)
- [docs/architecture/overview.md](../architecture/overview.md)
- [docs/design/README.md](../design/README.md)
- [docs/design/app/D-0001-diagnosi-obd-sola-lettura/README.md](../design/app/D-0001-diagnosi-obd-sola-lettura/README.md)
- [obd-core/src/main/kotlin/it/multipla/obd/core/DemoTransport.kt](../../obd-core/src/main/kotlin/it/multipla/obd/core/DemoTransport.kt)
- [obd-core/src/main/kotlin/it/multipla/obd/core/ElmSession.kt](../../obd-core/src/main/kotlin/it/multipla/obd/core/ElmSession.kt)
- [obd-core/src/main/kotlin/it/multipla/obd/core/ObdParser.kt](../../obd-core/src/main/kotlin/it/multipla/obd/core/ObdParser.kt)

### Creati — 27

- [app/src/main/java/it/multipla/obd/Diagnosis.kt](../../app/src/main/java/it/multipla/obd/Diagnosis.kt)
- [app/src/main/java/it/multipla/obd/DiagnosisContent.kt](../../app/src/main/java/it/multipla/obd/DiagnosisContent.kt)
- [app/src/main/res/values/strings.xml](../../app/src/main/res/values/strings.xml)
- [app/src/test/java/it/multipla/obd/DiagnosisUiTest.kt](../../app/src/test/java/it/multipla/obd/DiagnosisUiTest.kt)
- [app/src/test/java/it/multipla/obd/diagnostics/DiagnosisFixture.kt](../../app/src/test/java/it/multipla/obd/diagnostics/DiagnosisFixture.kt)
- [app/src/test/java/it/multipla/obd/diagnostics/DiagnosisRobustnessTest.kt](../../app/src/test/java/it/multipla/obd/diagnostics/DiagnosisRobustnessTest.kt)
- [app/src/test/java/it/multipla/obd/diagnostics/DiagnosisSteps.kt](../../app/src/test/java/it/multipla/obd/diagnostics/DiagnosisSteps.kt)
- [app/src/test/java/it/multipla/obd/diagnostics/DiagnosisTest.kt](../../app/src/test/java/it/multipla/obd/diagnostics/DiagnosisTest.kt)
- [app/src/test/java/it/multipla/obd/diagnostics/RequiredScenariosRunner.kt](../../app/src/test/java/it/multipla/obd/diagnostics/RequiredScenariosRunner.kt)
- [app/src/test/java/it/multipla/obd/diagnostics/ScenarioExecutionGuard.kt](../../app/src/test/java/it/multipla/obd/diagnostics/ScenarioExecutionGuard.kt)
- [docs/verification/app-D-0001-demo.txt](app-D-0001-demo.txt)
- [docs/verification/app-D-0001-risultati.json](app-D-0001-risultati.json)
- [docs/verification/app-D-0001-ui/caratteri-200-risultato.png](app-D-0001-ui/caratteri-200-risultato.png)
- [docs/verification/app-D-0001-ui/caratteri-200.png](app-D-0001-ui/caratteri-200.png)
- [docs/verification/app-D-0001-ui/demo-riepilogo.png](app-D-0001-ui/demo-riepilogo.png)
- [docs/verification/app-D-0001-ui/parametri-esportazione.png](app-D-0001-ui/parametri-esportazione.png)
- [docs/verification/app-D-0001-ui/prerequisito-mancante.png](app-D-0001-ui/prerequisito-mancante.png)
- [docs/verification/app-D-0001-verifica.md](app-D-0001-verifica.md)
- [obd-core/docs/dtc-catalog.md](../../obd-core/docs/dtc-catalog.md)
- [obd-core/src/main/kotlin/it/multipla/obd/core/DiagnosticReader.kt](../../obd-core/src/main/kotlin/it/multipla/obd/core/DiagnosticReader.kt)
- [obd-core/src/main/kotlin/it/multipla/obd/core/DtcCatalog.kt](../../obd-core/src/main/kotlin/it/multipla/obd/core/DtcCatalog.kt)
- [obd-core/src/main/kotlin/it/multipla/obd/core/ReadinessDecoder.kt](../../obd-core/src/main/kotlin/it/multipla/obd/core/ReadinessDecoder.kt)
- [obd-core/src/main/resources/licenses/OBDex-CC0-1.0.txt](../../obd-core/src/main/resources/licenses/OBDex-CC0-1.0.txt)
- [obd-core/src/test/kotlin/it/multipla/obd/core/DiagnosticProtocolTest.kt](../../obd-core/src/test/kotlin/it/multipla/obd/core/DiagnosticProtocolTest.kt)
- [obd-core/src/test/kotlin/it/multipla/obd/core/FreezeFrameCommandTest.kt](../../obd-core/src/test/kotlin/it/multipla/obd/core/FreezeFrameCommandTest.kt)
- [obd-core/src/test/kotlin/it/multipla/obd/core/diagnostics/DiagnosticSteps.kt](../../obd-core/src/test/kotlin/it/multipla/obd/core/diagnostics/DiagnosticSteps.kt)
- [obd-core/src/test/kotlin/it/multipla/obd/core/diagnostics/DiagnosticsTest.kt](../../obd-core/src/test/kotlin/it/multipla/obd/core/diagnostics/DiagnosticsTest.kt)

## Commit proposto

```text
feat(app): implementata la diagnosi OBD in sola lettura

Raccolti codici, readiness, freeze frame e parametri in un tentativo
manuale con contesto fissato, esiti distinti e rapporto TXT esportabile.
Preservati risultati parziali, isolamento demo e proprietà del servizio.

Refs: app/D-0001
```
