# Verifica di progetto/D-0001 — Verifiche automatiche e formattazione

Data: 10 novembre 2026. Specifica confermata dal proprietario per il coding
nella stessa data, con incarico di registrarne la conferma e implementarla.
La registrazione è nel [package](../design/progetto/D-0001-verifiche-automatiche/README.md)
e nel relativo [indice](../design/README.md). Nessuna domanda aperta.

## Esito e perimetro

**72 test superati, nessun fallimento, errore o test saltato.** Controllo di
formattazione, compilazione del pacchetto Android e analisi statica completati.
Le 14 prove negative rilevano la causa prevista, con uscita diversa da zero,
senza errori di compilazione e senza modifiche automatiche al file provato.

Il pilota di Behavior-Driven Development (BDD) usa `ConnectionController`
reale, `MemoryConnectionStore` e un connettore simulato che fallisce dopo
500 millisecondi virtuali. I tentativi iniziano a 0, 3.500 e 7.000 millisecondi;
le pause sono 3.000 millisecondi dal fallimento precedente. Sono verificati
blocco persistito, stato manuale e assenza di un quarto tentativo anche dopo
ulteriori 100.000 millisecondi virtuali.

Il runner seleziona esplicitamente solo la feature di connessione. Tutte le
`.feature`, comprese quelle della futura diagnosi, sono conservate byte per
byte: non sono aggiunti runner o step diagnostici. Nessuna modifica funzionale
al codice applicativo; i sorgenti esistenti cambiano soltanto per formattazione
e import espliciti. Il file `app/build.gradle.kts` è identico all'inizio della
sessione: versione, firma e dipendenze applicative restano quelle esistenti.

## Ambiente, versioni e contratti

Ambiente macOS su architettura arm64; Java Development Kit (JDK) Amazon Corretto
17.0.19. Le versioni applicative rimangono quelle del README.

| Componente | Versione verificata |
| --- | --- |
| Gradle, wrapper invariato | 8.13 |
| Android Gradle Plugin (AGP) | 8.13.2 |
| Kotlin e plugin Compose | 2.2.20 |
| JUnit | 4.13.2 |
| Coroutine del nucleo e dei test | 1.10.2 |
| Cucumber Java, JUnit e core | 7.29.0 |
| Spotless Gradle | 8.3.0 |
| ktlint, motore fissato nei due formati Spotless | 1.8.0 |

Le release sono stabili e fissate nella configurazione del progetto.
`buildEnvironment` e `:obd-core:dependencies --configuration testRuntimeClasspath`
confermano Spotless, Cucumber, JUnit e coroutine risolti. Non sono introdotti
Spring o il motore JUnit Platform. La documentazione ufficiale delle versioni
è stata consultata prima dell'integrazione:

- [Spotless 8.3.0, pubblicazione](https://plugins.gradle.org/plugin/com.diffplug.spotless/8.3.0).
- [Spotless 8.3.0, requisiti e configurazione](https://github.com/diffplug/spotless/blob/gradle/8.3.0/plugin-gradle/README.md): JRE 17 e Gradle 7.3 come minimi; JRE significa Java Runtime Environment.
- [Runner Cucumber 7.29.0](https://github.com/cucumber/cucumber-jvm/blob/v7.29.0/cucumber-junit/src/main/java/io/cucumber/junit/Cucumber.java): delega a JUnit 4 e deprecazione a monte.
- [ktlint 1.8.0, release](https://github.com/ktlint/ktlint/releases/tag/1.8.0) e [regole della versione](https://github.com/ktlint/ktlint/blob/1.8.0/documentation/snapshot/docs/rules/standard.md), compreso il naming `Composable`.

## Comandi eseguiti

Tutti i comandi Gradle usano JDK 17. Suite iniziali rieseguite, prima di
integrare gli strumenti:

```sh
./gradlew :obd-core:test :app:testDebugUnitTest --rerun-tasks --console=plain
```

Integrazione e formattazione, con applicazione esplicita distinta dal controllo:

```sh
./gradlew :obd-core:test --console=plain
./gradlew :spotlessCheck --console=plain
./gradlew :spotlessApply --console=plain
```

Il primo `spotlessCheck` ha rilevato le violazioni storiche senza correggerle.
Il primo `spotlessApply` ha segnalato import con `*` non correggibili: sono stati
resi espliciti, senza soppressioni. La compilazione ha poi segnalato due import
mancanti nei test, `assertThrows` e `cancelAndJoin`, completati prima del verde.
Questi sono adeguamenti del passaggio di formattazione, non fallimenti del
comportamento o prove negative di Cucumber. Anche il runner aggiunto dopo la
prova di mancata scoperta è stato riallineato al formatter prima delle prove
definitive. Le prove negative riportate sotto sono state tutte ripetute sul
runner definitivo, partendo da sorgenti formattati.

Verifica finale dopo il ripristino di tutte le modifiche temporanee:

```sh
./gradlew :check :app:assembleDebug :app:lintDebug --rerun-tasks --console=plain
```

Esito: `BUILD SUCCESSFUL`, 65 task eseguiti. La root `:check` dipende da
`spotlessCheck`, `:obd-core:test` e `:app:testDebugUnitTest`. Non richiama un
formatter in modalità di correzione. Verifiche delle dipendenze:

```sh
./gradlew :obd-core:dependencies --configuration testRuntimeClasspath :buildEnvironment --console=plain
```

## Scoperta, suite e report

| Modulo | Prima | Finale | Differenza | Fallimenti/errori/saltati finali |
| --- | ---: | ---: | --- | --- |
| obd-core | 61 | 62 | Un solo scenario pilota Cucumber | 0 / 0 / 0 |
| app | 10 | 10 | Nessuna | 0 / 0 / 0 |
| Totale | 71 | 72 | +1 | 0 / 0 / 0 |

I 71 test precedenti sono stati confrontati per suite e nome, tutti conservati.
Il report Cucumber contiene una sola feature e un solo scenario, con sette
step di scenario superati; il riepilogo console conta otto step includendo
l'hook finale di rilascio delle risorse. Nessuno step indefinito o pendente.
Nessuna feature diagnostica compare nei risultati eseguiti.

I risultati normalizzati, i nomi dei test, gli esiti delle prove negative e le
impronte della formattazione sono in
[progetto-D-0001-risultati.json](progetto-D-0001-risultati.json);
JSON significa JavaScript Object Notation. I report rigenerabili dalla build:

- `obd-core/build/reports/tests/test/index.html` e `obd-core/build/test-results/test/`.
- `obd-core/build/reports/cucumber/automatic-retry.json` e `.html`.
- `app/build/reports/tests/testDebugUnitTest/index.html` e `app/build/test-results/testDebugUnitTest/`.
- `app/build/reports/lint-results-debug.html` e `.xml`.

## Prove negative definitive

Ogni prova modifica un solo file, lo ripristina in ogni caso e verifica che
il controllo lasci intatti i byte della variante intenzionalmente errata.
Il codice di uscita è 1 in tutti i 14 casi; nessuno fallisce per compilazione.

Per asserzione errata, zero scenari e prima violazione di stile è stato usato
`./gradlew :check --continue`: il fallimento si propaga all'invocazione
aggregata. `--continue` permette di osservare anche i controlli indipendenti.
Per gli altri errori Cucumber:

```sh
./gradlew :obd-core:test --tests it.multipla.obd.core.connection.AutomaticRetryTest --console=plain
```

| Prova | Alterazione temporanea | Causa osservata |
| --- | --- | --- |
| Asserzione | Attesi 4 tentativi invece di 3 | `AssertionError: expected:<4> but was:<3>`; formattazione positiva |
| Step indefinito | Cambiata l'espressione dello step `Then` | `UndefinedStepException` |
| Step pendente | Sollevata `PendingException` nello step | Scenario pendente e invocazione fallita |
| Step ambiguo | Aggiunta una seconda espressione sovrapposta | `AmbiguousStepDefinitionsException` |
| Zero scenari | Filtro tag senza corrispondenze sul pilota | `Nessuno scenario Cucumber scoperto`; suite storiche ancora eseguite |
| Feature assente | Percorso classpath inesistente | Errore di risorsa durante la creazione del runner |

La prova di zero scenari ha dimostrato che Gradle può scartare una suite vuota
prima che i plugin di fine esecuzione siano chiamati. `RequiredScenariosRunner`
controlla quindi la descrizione prodotta dal runner Cucumber prima di delegare
esecuzione e filtri JUnit; `ScenarioExecutionGuard` conta avvii e conclusioni.
La prova negativa sul controllo ordinario dimostra il caso senza affidarsi
al fallimento per un filtro Gradle privo di test.

Le altre otto prove inseriscono spazi finali, validi per la compilazione ma
vietati dalla formattazione. Il controllo segnala espressamente il file
alterato e non lo corregge. Oltre alla prima prova aggregata, il comando è
`./gradlew :spotlessCheck --console=plain`:

| Percorso provato | Copertura |
| --- | --- |
| `app/src/main/java/it/multipla/obd/MainActivity.kt` | Produzione Android, directory java e Compose |
| `app/src/test/java/it/multipla/obd/SessionServiceTest.kt` | Test Android nella directory java |
| `obd-core/src/main/kotlin/it/multipla/obd/core/ConnectionController.kt` | Produzione del nucleo |
| `obd-core/src/test/kotlin/it/multipla/obd/core/ConnectionControllerTest.kt` | Test del nucleo |
| `build.gradle.kts` | Script root |
| `settings.gradle.kts` | Impostazioni root |
| `app/build.gradle.kts` | Script modulo Android |
| `obd-core/build.gradle.kts` | Script modulo nucleo |

## Formattazione e revisione separata

Il passaggio di sola formattazione interessa i 22 file elencati sotto.
Comprende spaziatura, impaginazione, parentesi graffe e virgole finali applicate
da ktlint, import ordinati, rimozione di import inutilizzati ed espansione
esplicita degli import con `*`. Nessun aggiornamento di logica o contratto.

È stata prodotta e consegnata fuori dal source tree una patch separata della
sola formattazione. L'inversione della patch su una copia temporanea dei 22 file
ricostruisce esattamente i byte precedenti. Le impronte prima/dopo sono nei
risultati normalizzati. Il confronto lessicale sui 21 sorgenti Kotlin storici
conserva tutti gli altri token, escludendo import, spazi, commenti, parentesi
graffe, punti e virgola e virgole finali; i test verificano i comportamenti.
I quattro file Kotlin nuovi sono già formattati nella modifica di integrazione.
Configurazione, step e documentazione possono quindi essere rivisti separatamente
senza mescolare cambiamenti funzionali ai sorgenti storici.

## Copertura dei criteri di accettazione

| Criterio | Esito |
| --- | --- |
| V01 | Un pilota scoperto ed eseguito; sette step superati, hook concluso |
| V02 | Pilota verde, asserzione intenzionalmente errata rilevata, ripristino verde |
| V03 | Tutti i 71 test iniziali conservati; totale finale 72 |
| V04 | Otto percorsi provati con violazioni, byte invariati dal controllo, verde dopo ripristino |
| V05 | 22 file e patch di sola formattazione separati; verifiche finali positive |
| V06 | Aggregatore positivo nello stato finale e negativo per asserzione, zero scenari e stile |
| V07 | Pacchetto Android compilato e lint senza errori; configurazione app invariata |
| V08 | CONTRIBUTING, overview, stato del package, indice ed evidenze aggiornati |

## Limiti e prove distinte

- Lint Android: zero errori e nove avvisi, sette su versioni aggiornabili e due
  `UseKtx`. Il numero è quello dell'esecuzione corrente, non i tre avvisi del
  rapporto storico. Nessun aggiornamento generale delle dipendenze è stato eseguito.
- Il compilatore segnala la deprecazione del runner/annotazioni Cucumber JUnit 4
  e dell'uso Robolectric di `setIsInteractive` nel test esistente. La migrazione
  a JUnit 5 richiede un perimetro distinto; qui è conservato JUnit 4 come previsto.
- I test Android sono locali con Robolectric; non è stata verificata la resa
  delle schermate su dispositivo o emulatore durante questa sessione.
- Nessuna installazione o distribuzione del pacchetto, nessuna prova Bluetooth,
  su telefono o vettura. Le simulazioni non le certificano.
- Gli scenari della futura diagnosi restano in design ed esclusi dall'esecuzione.
- Nessun comando Git, commit o push eseguito. Le liste descrivono le scritture
  della sessione, non uno stato dell'indice Git.

## Elenco esatto dei file

### Creati — 7

- `.editorconfig`
- `obd-core/src/test/kotlin/it/multipla/obd/core/connection/AutomaticRetryTest.kt`
- `obd-core/src/test/kotlin/it/multipla/obd/core/connection/AutomaticRetrySteps.kt`
- `obd-core/src/test/kotlin/it/multipla/obd/core/connection/RequiredScenariosRunner.kt`
- `obd-core/src/test/kotlin/it/multipla/obd/core/connection/ScenarioExecutionGuard.kt`
- `docs/verification/progetto-D-0001-verifica.md`
- `docs/verification/progetto-D-0001-risultati.json`

### Modificati per integrazione e documentazione — 9

- `AGENTS.md`
- `CONTRIBUTING.md`
- `README.md`
- `build.gradle.kts`
- `obd-core/build.gradle.kts`
- `docs/architecture/overview.md`
- `docs/adr/README.md`
- `docs/design/README.md`
- `docs/design/progetto/D-0001-verifiche-automatiche/README.md`

### Modificati per sola formattazione — 22

- `app/src/main/java/it/multipla/obd/AndroidConnectionStore.kt`
- `app/src/main/java/it/multipla/obd/DiagnosticService.kt`
- `app/src/main/java/it/multipla/obd/DiagnosticsSession.kt`
- `app/src/main/java/it/multipla/obd/ElmAdapterLink.kt`
- `app/src/main/java/it/multipla/obd/MainActivity.kt`
- `app/src/main/java/it/multipla/obd/bluetooth/RfcommTransport.kt`
- `app/src/test/java/it/multipla/obd/ConnectionDiagnosticsTest.kt`
- `app/src/test/java/it/multipla/obd/SessionServiceTest.kt`
- `obd-core/src/main/kotlin/it/multipla/obd/core/ConnectionController.kt`
- `obd-core/src/main/kotlin/it/multipla/obd/core/DemoTransport.kt`
- `obd-core/src/main/kotlin/it/multipla/obd/core/ElmSession.kt`
- `obd-core/src/main/kotlin/it/multipla/obd/core/ObdParser.kt`
- `obd-core/src/main/kotlin/it/multipla/obd/core/PromptConnection.kt`
- `obd-core/src/main/kotlin/it/multipla/obd/core/Transport.kt`
- `obd-core/src/test/kotlin/it/multipla/obd/core/AdapterInitializationTest.kt`
- `obd-core/src/test/kotlin/it/multipla/obd/core/ConnectionControllerTest.kt`
- `obd-core/src/test/kotlin/it/multipla/obd/core/ConnectionRecoveryTest.kt`
- `obd-core/src/test/kotlin/it/multipla/obd/core/ElmSessionTest.kt`
- `obd-core/src/test/kotlin/it/multipla/obd/core/FirstConnectionTest.kt`
- `obd-core/src/test/kotlin/it/multipla/obd/core/ObdParserTest.kt`
- `obd-core/src/test/kotlin/it/multipla/obd/core/PromptConnectionTest.kt`
- `settings.gradle.kts`

## Commit proposti

Separazione proposta secondo CONTRIBUTING; nessun commit eseguito.
Il primo contiene soltanto i 22 file di formattazione, il secondo gli altri 16.

```text
refactor(progetto): uniformata la formattazione di sorgenti e script

Resi espliciti gli import e applicato lo stile intellij_idea,
senza modifiche ai comportamenti applicativi.

Refs: progetto/D-0001
```

```text
build(progetto): integrati Cucumber e controlli di formattazione

Reso eseguibile il pilota di connessione con controlli su scoperta e
fallimenti. Aggregati test e formattazione, registrate conferma ed evidenze.

Refs: progetto/D-0001
```
