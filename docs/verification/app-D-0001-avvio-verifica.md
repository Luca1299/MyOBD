# Correzione dell’avvio concorrente di app/D-0001

Data: 10 novembre 2026. Versione 0.3.2, codice versione 6.
OBD significa On-Board Diagnostics; JDK Java Development Kit; JVM Java
Virtual Machine; ADR Architecture Decision Record. TXT indica testo semplice.

## Perimetro ed esito

Corretto il difetto residuo individuato nella seconda revisione: perdita del
collegamento fra preparazione del contesto e avvio effettivo della diagnosi.
Il proprietario ha richiesto la correzione con «correggiamolo». Il comportamento
è già definito da [app/D-0001](../design/app/D-0001-diagnosi-obd-sola-lettura/README.md):
prerequisiti correnti, nessuna diagnosi accodata alla riconnessione, un solo
proprietario operativo e conservazione dei risultati conclusi. Nessuna domanda
aperta o nuova decisione funzionale o architetturale. ADR-0004 resta valido.

**157 test superati**, nessun fallimento, errore o test saltato. Sono preservati
per classe e nome tutti i 153 test di 0.3.1, comprese le quattro correzioni
precedenti. Formattazione, compilazione e analisi statica completate; firma
conservata. Nessuna prova fisica eseguita.

## Regressione prima del codice applicativo

È stato aggiunto `DiagnosisStartTest`, senza cambiare il codice applicativo
né le feature di design. Il primo controllo ha riprodotto tre fallimenti;
un quarto caso ha poi protetto anche l’intervallo subito dopo l’ammissione.
L’esecuzione negativa completa è stata:

```sh
./gradlew :app:testDebugUnitTest --tests it.multipla.obd.diagnostics.DiagnosisStartTest --console=plain
```

Compilazione riuscita, quattro test eseguiti e quattro `AssertionError`:

| Caso | Comportamento precedente |
| --- | --- |
| Perdita durante la preparazione, prima diagnosi | Tentativo rimasto in acquisizione senza operazione attiva, anche dopo riconnessione. |
| Collegamento sostituito e già recuperato prima dell’ammissione | La richiesta preparata per il collegamento perso partiva su quello successivo. |
| Risultato precedente già concluso | Sostituzione del risultato consultabile con un tentativo mai partito. |
| Perdita mentre viene pubblicato l’avvio | Poteva essere inviata la prima richiesta dopo che la perdita era già stata elaborata, prima che la coroutine fosse registrata. |

I primi tre casi usano due thread e due barriere con scadenza, con l’orologio
iniettabile già presente. La perdita attraversa il flusso del trasporto e il
coordinatore effettivo; richieste, lettori e sessione sono quelli applicativi.
Il quarto usa un osservatore immediato dello stato per elaborare la perdita
nel punto di pubblicazione, prima che prosegua l’avvio della coroutine.
Non si tratta di errori di compilazione o ambiente e non si usano attese casuali.

## Correzione e garanzie

La preparazione mantiene un riferimento al collegamento originale. Il percorso
comune delle letture rivaluta i prerequisiti correnti e rifiuta un collegamento
sostitutivo. Occupazione e nuovo tentativo sono pubblicati nello stesso
aggiornamento atomico, solo se l’operazione è ammessa. Se non è ammessa,
non compare una nuova diagnosi e l’eventuale risultato concluso resta invariato.

Il controllo di ammissione rivaluta stato, proprietario, collegamento e
generazione dell’operazione ad ogni confronto atomico. Il contratto di
`MutableStateFlow.compareAndSet` è stato verificato nel sorgente distribuito
di coroutine 1.10.2, già risolto dal progetto; non sono state aggiunte dipendenze.

Il gestore della perdita riconosce anche un tentativo già ammesso la cui
coroutine non sia ancora partita: lo conclude come interrotto e invalida
l’operazione. La pubblicazione degli eventi di collegamento conserva lo stato
occupato finché esiste una diagnosi attiva. Una coroutine ormai invalidata
non viene avviata.

I test verificano inoltre assenza di una nuova richiesta diagnostica,
esportazione precedente invariata, contenitore dei risultati invariato e
possibilità di un successivo avvio manuale concluso. Rimangono eseguiti i test
di finalizzazione concorrente, risposte miste, Registro, servizio, conservazione
reale/demo e risposte tardive della consegna precedente.

Gli scenari confermati, compresi DIA-S01–DIA-S03, DIA-S06–DIA-S10 e DIA-S14,
restano integri e superati. I nuovi test JVM proteggono gli intervalli tecnici
del comportamento già specificato; nessuna `.feature` è stata modificata.

## Controlli finali

```sh
./gradlew :spotlessApply --console=plain
./gradlew :check :app:assembleDebug :app:lintDebug --rerun-tasks --console=plain
```

Ambiente: macOS arm64, Amazon Corretto JDK 17.0.19, Gradle 8.13 e ambiente
Android del progetto. Applicazione del formatter esplicita e distinta dalla
verifica. Tutti i 66 task della verifica completa sono stati eseguiti.

| Modulo | Test 0.3.1 preservati | Nuovi | Totale |
| --- | ---: | ---: | ---: |
| obd-core | 94 | 0 | 94 |
| app | 59 | 4 | 63 |
| Totale | 153 | 4 | 157 |

Eseguiti 53 scenari diagnostici e il pilota di connessione, tutti positivi.
Analisi statica: zero errori e 11 avvisi già presenti, sulle versioni
aggiornabili e due UseKtx. Restano le deprecazioni note di Cucumber JUnit 4 e
Robolectric; nessun aggiornamento generale dello stack.

Pacchetto generato: `app/build/outputs/apk/debug/app-debug.apk`.
Firma verificata con `apksigner`: certificato SHA-256 (Secure Hash Algorithm,
256 bit)
`ea6442a00c98a7f12698a9bddc77dd01b5ad862b382209177a18ca48e32e07fc`.
Il pacchetto conserva la chiave debug
del progetto; installazione e aggiornamento sul telefono restano da provare.

Conteggi, nomi dei test, comandi e impronta del pacchetto sono nel
[riepilogo normalizzato](app-D-0001-avvio-risultati.json). La revisione nativa
delle modifiche è stata richiesta nel pannello della chat. Nessun comando
Git eseguito; nessun commit, push o pubblicazione eseguiti.

## Interfaccia simulata e prove fisiche residue

Le cinque verifiche Compose sulla schermata effettiva passano con Robolectric,
ambiente Android simulato livello 35: prerequisito mancante, stato pronto,
acquisizione, interruzione in corso e risultato concluso esportabile.
Non sono cambiati layout, componenti o comandi dell’interfaccia, salvo il numero
di versione. Non è stata ripetuta una verifica visiva sull’emulatore: le catture
di 0.3.1 rimangono evidenze storiche e non sono presentate come prove di 0.3.2.

Il caso concorrente usa trasporto controllato e due thread, non Bluetooth
fisico. Restano necessarie le prove con Motorola, NK Mini e vettura, incluse
perdita e recupero durante l’avvio, comportamento a schermo spento,
notifica ed esportazione sul telefono. Le simulazioni non certificano tali
comportamenti né la compatibilità dell’hardware.

## File della sessione

Modificati:

- `CONTRIBUTING.md`
- `README.md`
- `app/build.gradle.kts`
- `app/src/main/java/it/multipla/obd/DiagnosticsSession.kt`
- `app/src/main/java/it/multipla/obd/MainActivity.kt`
- `docs/architecture/overview.md`

Creati:

- `app/src/test/java/it/multipla/obd/diagnostics/DiagnosisStartTest.kt`
- `docs/verification/app-D-0001-avvio-risultati.json`
- `docs/verification/app-D-0001-avvio-verifica.md`

Gli output di build e i report rigenerabili restano fuori dall’elenco dei sorgenti.
I rapporti precedenti sono conservati senza riscriverne gli esiti.

## Commit proposto

```text
fix(app): impedito il blocco della diagnosi durante l’avvio

Coordinati l’ammissione della lettura e la pubblicazione del tentativo.
Rifiutate le richieste preparate per un collegamento perso e conclusi
i tentativi interrotti prima dell’avvio della coroutine, conservando
i risultati precedenti quando l’operazione non viene ammessa.

Refs: app/D-0001
```
