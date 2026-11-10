# Correzioni della revisione di app/D-0001

Data: 10 novembre 2026. Versione 0.3.1, codice versione 5.
OBD significa On-Board Diagnostics, DTC Diagnostic Trouble Code, ADR
Architecture Decision Record e JVM Java Virtual Machine. TXT indica testo
semplice; JDK indica Java Development Kit e API Application Programming
Interface. Nessun comando Git eseguito.

## Perimetro autorizzato ed esito

Il proprietario ha richiesto di correggere i quattro rilievi della revisione.
Tre sono correzioni del comportamento già confermato da
[app/D-0001](../design/app/D-0001-diagnosi-obd-sola-lettura/README.md): risposte
miste dei codici, concorrenza fra interruzione e finalizzazione, esportazione
del Registro dopo nuove letture. Gli scenari originari sono conservati; dopo APPROVO sono stati aggiunti
i due esempi DIA-S14 per la ricreazione del servizio, reale e demo.
La versione applicativa corregge tutti e quattro i rilievi, compresa la
conservazione ora precisata da ADR-0004.

**153 test superati**, zero fallimenti, errori o test saltati. Preservati per
classe e nome tutti i 140 test della consegna precedente. Controllo aggregato,
formattazione, compilazione Android e analisi statica positivi. Nessuna prova
con telefono, adattatore o vettura reali.

La durata dei risultati dopo Scollega è stata poi decisa esplicitamente dal
proprietario con «APPROVO la soluzione consigliata, i documenti e
l’implementazione». Nessuna domanda aperta.

## Punto 5 risolto: conservazione dopo Scollega

La revisione aveva presentato il rilievo come un difetto di sola
implementazione. La ricostruzione ha evidenziato una precisazione necessaria
fra arresto del servizio e durata del risultato. Prima delle modifiche
dipendenti, il proprietario ha approvato la memoria del processo per gli ultimi
risultati conclusi, reale e demo separati. Decisione consolidata in
[ADR-0004](../adr/0004-conservazione-risultati-nel-processo.md), nel package,
nei relativi indici e nell’overview. Gli ADR storici non sono stati riscritti.

Il contenitore Application conserva solo dati conclusi, senza trasporto,
coroutine o sessioni operative. Scollega ferma servizio, notifica e collegamento;
un servizio ricreato nello stesso processo ritrova il risultato della modalità
consultata. Una nuova acquisizione parte da sezioni vuote; alla conclusione
sostituisce il risultato della stessa modalità. Pubblicazioni obsolete non
rimpiazzano risultati successivi. Un contenitore nuovo, come in un processo
nuovo, non ha dati; non è introdotto uno storico persistente.

La prova Robolectric Scollega → arresto → distruzione del servizio → nuova
istanza nello stesso processo falliva prima della correzione. È stata poi
riallineata la fixture perché ogni servizio abbia il proprio scope figlio:
distruggere un’istanza non cancella lo scope virtuale di tutte le successive.
Questa era una limitazione della fixture emersa nel secondo caso demo,
distinta dalla regressione iniziale sulla perdita dei dati reali.
Il rilascio del servizio avviato e collegato all’interfaccia è coerente con il
[ciclo di vita documentato da Android](https://developer.android.com/develop/background-work/services/bound-services).

## Riproduzione prima della correzione

I test sono stati scritti prima delle correzioni. Un solo adattamento
preparatorio ha reso iniettabile l’orologio già usato dalla sessione, mantenendo
la logica precedente, per fermare la finalizzazione nel punto esatto.

Comando iniziale, con JDK 17 e ambiente Android installato:

```sh
./gradlew :obd-core:test --tests it.multipla.obd.core.MixedDtcResponsesTest :app:testDebugUnitTest --tests it.multipla.obd.diagnostics.DiagnosisRobustnessTest --continue --console=plain
```

Esito atteso: compilazione riuscita, tre regressioni comportamentali fallite.
Nel nucleo la lettura mista non produceva la lista del codice valido; nell’app
l’esportazione non conteneva il nuovo campione e l’interruzione accettata
veniva superata dalla finalizzazione. Le sei prove di robustezza precedenti
rimanevano positive. Non si tratta di fallimenti di compilazione o ambiente.

La prova concorrente usa due thread e due barriere con scadenza: il thread
della lettura arriva alla costruzione dello stato finale, quello dell’interfaccia
richiede l’interruzione e ne verifica l’accettazione, poi viene rilasciata la
finalizzazione. Il primo confronto atomico fallisce e viene ripetuto. Il codice
precedente riusava il parametro booleano letto prima dell’interruzione;
quello corretto rivaluta fase ed esito sullo stato corrente. Non si usano
ritardi casuali o una riproduzione parallela della politica applicativa.

Un’ulteriore regressione ha verificato che anche la lettura manuale dei codici
esporti sia il codice valido sia il problema: il vecchio riepilogo privilegiava
il problema e ometteva il codice. Fallimento riprodotto, poi corretto nel
riepilogo comune usato da registro ed esportazione.

## Correzioni

### Risposte miste

Il parser restituisce codici validi e problemi separatamente. Il lettore di
produzione conserva i codici ma classifica la sezione non conclusiva se altre
risposte sono negative o contengono un payload non valido. Una risposta valida
vuota insieme a un rifiuto resta parziale, non prova assenza globale di codici.
Rifiuti di natura diversa non dimostrano il mancato supporto dell’intera funzione.

La decodifica del singolo payload rimane unica; l’API storica della sola lista
continua a rifiutare letture parziali, mentre la diagnosi e la lettura manuale
usano il risultato che espone anche i problemi. Non sono stati resi permissivi
i controlli di framing o di formato per nascondere dati danneggiati.

### Finalizzazione e interruzione

Fase e intento di interruzione hanno un solo proprietario nello stato della
diagnosi; è stato rimosso il flag separato. La transizione finale rivaluta
l’interruzione sullo stato corrente ad ogni tentativo atomico, conservando il
motivo. Anche l’interruzione per trasporto o scollegamento aggiorna sezione,
esito e motivo nella stessa transizione. Le operazioni precedenti identificano
il proprio tentativo e non finalizzano quello successivo.

Il contratto è stato verificato sulla versione 1.10.2 risolta di coroutine,
anche nel sorgente locale della libreria e nel
[sorgente ufficiale StateFlow](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/flow/StateFlow.kt):
`update` può rivalutare la funzione quando un aggiornamento concorrente fa
fallire il confronto. Non sono introdotti framework o dipendenze ulteriori.

### Esportazione

Il Registro esporta campione manuale e scambi correnti, aggiungendo
l’eventuale diagnosi conclusa in una sezione esplicitamente distinta. La
scheda Diagnosi esporta invece la propria fotografia conclusa tramite
`exportDiagnosis`. I due ingressi riusano lo stesso selettore documenti e la
stessa preparazione della copia temporanea. Nessuno dei due interroga la vettura.

Il test confronta il rapporto diagnostico prima e dopo una lettura manuale con
annotazione Metano e 2048 giri/min: resta identico, mentre l’esportazione del
Registro include nuovi valori, annotazione e risposta originale. Un test sul
percorso completo verifica inoltre che una risposta mista produca una diagnosi
parziale con P0171 presente anche nel testo esportato.

## Controlli finali

```sh
./gradlew :spotlessApply --console=plain
./gradlew :check :app:assembleDebug :app:lintDebug --rerun-tasks --console=plain
```

Ambiente: macOS arm64, Amazon Corretto JDK 17.0.19, wrapper Gradle 8.13.
Stack, firma e dipendenze conservati. Applicazione del formatter esplicita,
distinta dal controllo. Il controllo finale non corregge i sorgenti.

| Modulo | Precedenti preservati | Nuovi | Totale |
| --- | ---: | ---: | ---: |
| obd-core | 90 | 4 | 94 |
| app | 50 | 9 | 59 |
| Totale | 140 | 13 | 153 |

Eseguiti tutti i 53 scenari diagnostici e il pilota di connessione.
Lint: zero errori e 11 avvisi non bloccanti, sulle versioni aggiornabili e i
due UseKtx già noti. Restano le deprecazioni Cucumber JUnit 4 e Robolectric già
documentate nella consegna precedente. Nessun aggiornamento generale delle
dipendenze è stato introdotto per rimuovere tali avvisi.

Report rigenerabili negli stessi percorsi descritti in CONTRIBUTING;
risultati della presente esecuzione nel
[riepilogo normalizzato](app-D-0001-correzioni-risultati.json).
La verifica nativa delle modifiche è stata richiesta nel pannello della chat.

## Interfaccia simulata e limiti fisici

Le prove automatiche attraversano parser, lettori e sessione reali con
trasporto controllato. Le prove sull’emulatore verificano separatamente i due
ingressi di esportazione e il contenuto realmente salvato dal selettore Android.
Sulla build finale 0.3.1, emulatore Medium Phone API 36.1,
senza adattatore fisico, sono stati verificati:

- diagnosi demo con annotazione Benzina, conclusa ed esportata tramite il
  selettore documenti: [TXT della diagnosi](app-D-0001-correzioni-ui/diagnosi.txt);
- lettura manuale successiva con annotazione Metano, poi esportazione dal
  Registro: il [TXT del Registro](app-D-0001-correzioni-ui/registro.txt) contiene
  il nuovo campione, i suoi scambi e l’intero rapporto Benzina invariato,
  separato dal contesto corrente;
- Scollega, passaggio alla schermata Home e ritorno nell’app: nessun servizio
  residuo nel controllo Android dei servizi e processo rimasto identico;
  rientrando in demo è consultabile la stessa diagnosi #1 Benzina, senza
  un nuovo avvio diagnostico. La [cattura al ritorno](app-D-0001-correzioni-ui/risultato-ripristinato.png)
  mostra il risultato e l’annotazione corrente ripartita da Non annotata.

La demo riprende il proprio collegamento simulato secondo il comportamento
già esistente. L’assenza del servizio è stata verificata prima di rientrare;
la consultazione del risultato non costituisce una nuova diagnosi. Il
ripristino reale e demo dopo ricreazione del servizio è coperto separatamente
da Robolectric e dagli esempi DIA-S14. I TXT e la cattura contengono solo
dati sintetici. Impronte e risultati sono nel riepilogo normalizzato.

La conservazione dopo Scollega è verificata nello stesso processo; non
sopravvive alla terminazione del processo. Restano
necessarie le prove con Motorola, NK Mini e vettura: comunicazione, risposte
multiple reali, continuità, timeout, interruzione ed esportazione sul telefono.
Le simulazioni non certificano questi comportamenti fisici.

## File della sessione

Modificati (19):

- `CONTRIBUTING.md`
- `README.md`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/it/multipla/obd/DiagnosticService.kt`
- `app/src/main/java/it/multipla/obd/DiagnosticsSession.kt`
- `app/src/main/java/it/multipla/obd/MainActivity.kt`
- `app/src/test/java/it/multipla/obd/SessionServiceTest.kt`
- `app/src/test/java/it/multipla/obd/diagnostics/DiagnosisFixture.kt`
- `app/src/test/java/it/multipla/obd/diagnostics/DiagnosisRobustnessTest.kt`
- `app/src/test/java/it/multipla/obd/diagnostics/DiagnosisSteps.kt`
- `app/src/test/resources/features/diagnostics/diagnosis-session.feature`
- `docs/adr/README.md`
- `docs/architecture/overview.md`
- `docs/design/README.md`
- `docs/design/app/D-0001-diagnosi-obd-sola-lettura/README.md`
- `obd-core/src/main/kotlin/it/multipla/obd/core/DiagnosticReader.kt`
- `obd-core/src/main/kotlin/it/multipla/obd/core/ElmSession.kt`
- `obd-core/src/main/kotlin/it/multipla/obd/core/ObdParser.kt`

Creati (10):

- `app/src/main/java/it/multipla/obd/DiagnosisResults.kt`
- `app/src/main/java/it/multipla/obd/MultiplaApplication.kt`
- `app/src/test/java/it/multipla/obd/DiagnosisResultsTest.kt`
- `docs/adr/0004-conservazione-risultati-nel-processo.md`
- `docs/verification/app-D-0001-correzioni-risultati.json`
- `docs/verification/app-D-0001-correzioni-ui/diagnosi.txt`
- `docs/verification/app-D-0001-correzioni-ui/registro.txt`
- `docs/verification/app-D-0001-correzioni-ui/risultato-ripristinato.png`
- `docs/verification/app-D-0001-correzioni-verifica.md`
- `obd-core/src/test/kotlin/it/multipla/obd/core/MixedDtcResponsesTest.kt`

Gli output di build e i report HTML rigenerabili non sono sorgenti della consegna.

## Commit proposto

```text
fix(app): corretti diagnosi, conservazione ed esportazione

Conservati codici validi nelle risposte miste e resa atomica
l’interruzione della diagnosi. Distinti il rapporto concluso e
l’esportazione dei campioni e del registro correnti. Conservati
separatamente gli ultimi risultati reali e demo nella memoria
del processo, anche dopo l’arresto del servizio.

Refs: app/D-0001, ADR-0004
```
