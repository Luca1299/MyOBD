# Verifica della revisione visiva R1 — app/D-0001

**Data:** 12 novembre 2026. **Esito:** controlli pertinenti superati.

Attuata la revisione R1 di
[app/D-0001](../design/app/D-0001-diagnosi-obd-sola-lettura/README.md),
confermata dal proprietario con autorizzazioni distinte al consolidamento,
alla conferma della specifica e all'implementazione delle quattro schede.
OBD significa On-Board Diagnostics; BDD significa Behavior-Driven Development;
JDK significa Java Development Kit; JVM significa Java Virtual Machine.

## Risultato

Collegamento distingue adattatore e centralina e conserva i comandi esistenti.
Parametri presenta il campione singolo in griglia con unità e precisione
originali, correzioni su righe intere e stato della spia separato.
Diagnosi mantiene contesto, categorie, esiti, tempi, limiti e interruzione;
la provenienza delle descrizioni è consultabile con un'espansione esplicita.
Registro distingue ora, tipo e contenuto, spiega i prefissi e conserva
multilinea, prefissi sconosciuti e righe senza orario riconoscibile.
Mostra le ultime 120 voci e chiarisce il limite distinto di 400 nel rapporto.
I due comandi di esportazione distinguono sessione e diagnosi conclusa.

MainActivity adatta gli stessi comandi Android e della sessione ai componenti
di presentazione. Non sono stati modificati il nucleo, i lettori, la politica
di collegamento, il servizio, la conservazione dei risultati o i rapporti.
Non è stato implementato il package `app/D-0002`, ancora in design.
Nessuna dipendenza, versione applicativa o chiave di firma è stata cambiata;
nessun pacchetto è stato distribuito e nessuna operazione Git è stata eseguita.

## Metodo ed esecuzioni

Ambiente: JDK 17.0.21, Gradle 8.13, Robolectric 4.17 e Application Programming
Interface (API) Android 35 per le verifiche dell'interfaccia. I test di
Collegamento comprendono anche API 31. I contratti di Compose
Foundation 1.9.0 e Material 3 1.3.2 sono stati verificati nei sorgenti delle
dipendenze effettive. Gli strumenti sono quelli descritti in
[CONTRIBUTING.md](../../CONTRIBUTING.md).

Prima del codice grafico sono stati scritti gli scenari e i test pertinenti.
Il primo avvio mirato di `DiagnosisUiTest` ha compilato correttamente ed
eseguito sei test, con due fallimenti comportamentali attesi: mancava la
spiegazione specifica dell'assenza di codici e il nome distinto
dell'esportazione della diagnosi. Gli stessi sei test sono poi passati.
Questo rosso è distinto dall'assenza iniziale delle nuove API dei componenti,
per le quali i test nuovi sono stati scritti prima della produzione.

Comandi eseguiti con JDK 17, dalla root:

```sh
./gradlew :app:testDebugUnitTest --tests it.multipla.obd.DiagnosisUiTest
./gradlew :app:testDebugUnitTest --tests it.multipla.obd.DiagnosisUiTest --tests it.multipla.obd.ConnectionContentTest --tests it.multipla.obd.ParametersContentTest --tests it.multipla.obd.SessionRegisterContentTest --tests it.multipla.obd.SessionLogEntryTest --tests it.multipla.obd.presentation.PresentationScenariosTest
./gradlew :app:testDebugUnitTest --tests it.multipla.obd.PresentationRenderingTest
./gradlew :spotlessApply :check :app:assembleDebug :app:lintDebug
./gradlew :obd-core:test --rerun-tasks
./gradlew :spotlessCheck :check :app:assembleDebug :app:lintDebug
```

La formattazione ha interessato soltanto sorgenti del presente intervento;
il confronto delle impronte prima e dopo la prima applicazione non ha
rilevato formattazioni del nucleo o degli script di build. Dopo le ultime
correzioni a orari e risorse è stata ripetuta la verifica completa con esito
positivo. Il nucleo, inizialmente aggiornato nel controllo incrementale, è
stato anche rieseguito esplicitamente: gli esiti non sono ricavati soltanto
da una vecchia esecuzione.

| Controllo conclusivo | Risultato |
| --- | --- |
| Test del modulo app | 90 test JUnit, zero fallimenti, errori o saltati. |
| Test del nucleo | 94 test JUnit, zero fallimenti, errori o saltati; riesecuzione esplicita. |
| BDD di presentazione | 7 scenari, 26 passi tutti superati; guard sui sette identificatori, avvio e conclusione. |
| BDD diagnostico applicativo | 29 scenari, 142 passi tutti superati. |
| Spotless/ktlint | Superato dopo formattazione. |
| Compilazione debug | Superata. |
| Android lint | Zero errori; 11 avvisi su file preesistenti non modificati: nove segnalazioni di versioni disponibili e due suggerimenti SharedPreferences/Kotlin Extensions (KTX). |

JUnit indica il framework dei test; i conteggi dei suoi test e degli scenari
non vanno sommati. I sette scenari di presentazione sono aggregati in un
test JUnit Robolectric, mentre i report Cucumber espongono ciascuno scenario.
Rimangono le deprecazioni del runner JUnit 4 Cucumber già descritte in
CONTRIBUTING; non è stata introdotta una migrazione.

Copertura nuova o estesa: sei test Diagnosi, otto Collegamento, sette
Parametri, tre Registro, cinque del presentatore del registro, sette scenari
di presentazione e due verifiche del renderer. Sono verificati anche
callback, prerequisiti e disponibilità specifiche dei comandi: la notifica
e la ripetizione della verifica della centralina mantengono le condizioni
originali; cambiare scheda non richiede letture o esportazioni.

Report generati:

- [JUnit app](../../app/build/reports/tests/testDebugUnitTest/index.html).
- [JUnit nucleo](../../obd-core/build/reports/tests/test/index.html).
- [Cucumber presentazione](../../app/build/reports/cucumber/presentation.html).
- [Cucumber diagnosi](../../app/build/reports/cucumber/diagnosis.html).
- [Android lint](../../app/build/reports/lint-results-debug.html).

## Resa grafica e limiti

`PresentationRenderingTest` monta i componenti applicativi con il tema
effettivo e dati ottenuti dalla sessione demo, dai lettori e dal coordinatore
reali. Sono stati verificati 412 × 915 density-independent pixel (dp) con
caratteri standard e 320 × 640 dp con scala caratteri 2.0.
La navigazione e lo scorrimento rendono raggiungibili i contenuti finali delle
quattro sezioni anche nella seconda configurazione. Le griglie e le coppie
etichetta/valore si ricompongono; il registro conserva l'orario completo.

Sono state prodotte e ispezionate immagini del renderer Android, distinte
dal mockup in HyperText Markup Language (HTML). La cattura tramite
`captureToImage` attendeva un ridisegno della finestra non ricevuto in questo
ambiente: era un limite della cattura, non un fallimento delle asserzioni sui
componenti. Le immagini finali sono ottenute dopo il layout mediante
[`View.draw(Canvas)`](https://developer.android.com/reference/android/view/View#draw(android.graphics.Canvas))
con grafica nativa Robolectric. La cattura software della gerarchia non
certifica la composizione della finestra o gli effetti grafici di un telefono.
L'orologio dei tentativi è controllato nella fixture: le durate mostrate
nelle immagini non sono misure di prestazione.

Le immagini sono in `app/build/reports/interface-r1/`, nel formato Portable
Network Graphics (PNG):

- [Collegamento](../../app/build/reports/interface-r1/collegamento.png),
  [Parametri](../../app/build/reports/interface-r1/parametri.png),
  [Diagnosi](../../app/build/reports/interface-r1/diagnosi.png),
  [Registro](../../app/build/reports/interface-r1/registro.png).
- [Collegamento con caratteri grandi](../../app/build/reports/interface-r1/collegamento-320-font2.png),
  [Parametri con caratteri grandi](../../app/build/reports/interface-r1/parametri-320-font2.png),
  [Diagnosi con caratteri grandi](../../app/build/reports/interface-r1/diagnosi-320-font2.png),
  [Registro con caratteri grandi](../../app/build/reports/interface-r1/registro-320-font2.png).

Le immagini comprendono la porzione visibile della schermata, non l'intero
contenuto scorrevole. I controlli non attestano telefono, permessi e selettore
documenti reali, Bluetooth, adattatore o vettura. Nessuna installazione o prova
fisica è stata svolta in questa sessione; resta la verifica della resa sul
telefono. La simulazione mantiene la distinzione fra dati acquisiti, assenza
valida e lettura non conclusiva.

## File di progetto interessati

Elenco completo dei 26 file sorgente e documentali dell'intervento. I report,
le immagini, il pacchetto debug e le cache di build sono output generati,
separati dalla consegna dei sorgenti.

| Azione | File |
| --- | --- |
| Modificato | `CONTRIBUTING.md` |
| Modificato | `docs/design/README.md` |
| Modificato | `docs/design/app/D-0001-diagnosi-obd-sola-lettura/README.md` |
| Creato | `docs/design/app/D-0001-diagnosi-obd-sola-lettura/mockup/interfaccia-r1.html` |
| Creato | `docs/verification/app-D-0001-interfaccia-r1-verifica.md` |
| Modificato | `app/src/main/java/it/multipla/obd/MainActivity.kt` |
| Modificato | `app/src/main/java/it/multipla/obd/DiagnosisContent.kt` |
| Creato | `app/src/main/java/it/multipla/obd/PresentationComponents.kt` |
| Creato | `app/src/main/java/it/multipla/obd/ConnectionContent.kt` |
| Creato | `app/src/main/java/it/multipla/obd/ParametersContent.kt` |
| Creato | `app/src/main/java/it/multipla/obd/SessionRegisterContent.kt` |
| Creato | `app/src/main/java/it/multipla/obd/SessionLogEntry.kt` |
| Modificato | `app/src/main/res/values/strings.xml` |
| Creato | `app/src/main/res/values/strings_presentation.xml` |
| Creato | `app/src/main/res/values/strings_connection.xml` |
| Creato | `app/src/main/res/values/strings_parameters_register.xml` |
| Modificato | `app/src/test/java/it/multipla/obd/DiagnosisUiTest.kt` |
| Creato | `app/src/test/java/it/multipla/obd/ConnectionContentTest.kt` |
| Creato | `app/src/test/java/it/multipla/obd/ParametersContentTest.kt` |
| Creato | `app/src/test/java/it/multipla/obd/SessionRegisterContentTest.kt` |
| Creato | `app/src/test/java/it/multipla/obd/SessionLogEntryTest.kt` |
| Creato | `app/src/test/java/it/multipla/obd/PresentationRenderingTest.kt` |
| Creato | `app/src/test/java/it/multipla/obd/presentation/PresentationScenariosTest.kt` |
| Creato | `app/src/test/java/it/multipla/obd/presentation/PresentationWorld.kt` |
| Creato | `app/src/test/java/it/multipla/obd/presentation/PresentationSteps.kt` |
| Creato | `app/src/test/resources/features/presentation/diagnostic-interface.feature` |

Il mockup congelato conserva l'impronta registrata nella specifica R1.
Non sono stati inclusi segreti o configurazioni locali.

## Commit proposto

```text
refactor(app): adottata la grafica confermata per le quattro schede

Resi espliciti stato del collegamento, parametri ed esiti diagnostici.
Presentato il registro in colonne, preservando dati e comandi esistenti.

Refs: app/D-0001
```

La proposta non costituisce un commit eseguito.
