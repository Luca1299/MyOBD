# Contribuire a Multipla OBD

Convenzioni operative del repository. Il metodo delle sessioni è in
[AGENTS.md](AGENTS.md), lo stile in
[code-style.md](docs/guides/code-style.md), l'architettura
[nell'overview](docs/architecture/overview.md).

## Lingua

Documentazione, commenti e prosa dei commit in italiano. Identificatori di
codice, package e nomi dei test in inglese. Gli scenari Gherkin sono in inglese
con resa italiana sotto il titolo, secondo la guida di stile. I prefissi dei
commit restano quelli inglesi dello standard.

I testi destinati all'utente hanno l'italiano come riferimento. Le nuove
schermate usano risorse Android per i testi dell'interfaccia; protocolli,
identificatori persistiti e nomi dei comandi non sono testi da tradurre.
Questa regola non introduce traduzioni o una migrazione generale dei testi
esistenti nel presente consolidamento.

## Messaggi di commit

Si adotta Conventional Commits:

```text
<tipo>(<ambito>): <sommario>

<corpo facoltativo: motivazione ed effetto>

<footer facoltativo>
```

Sommario in italiano, al participio passato concordato con l'oggetto, iniziale
minuscola, senza punto finale, entro 72 caratteri. Il corpo spiega motivazione
ed effetto quando non sono ovvi; è impersonale, senza cronache della sessione
o di chi ha preso la decisione.

| Tipo | Uso |
| --- | --- |
| `feat` | Nuova funzionalità. |
| `fix` | Correzione di un difetto. |
| `docs` | Sola documentazione e specifiche. |
| `refactor` | Ristrutturazione senza nuovi comportamenti o correzioni. |
| `perf` | Miglioramento prestazionale verificato. |
| `test` | Aggiunta o correzione di test eseguibili e scenari. |
| `build` | Build, dipendenze e strumenti di sviluppo. |
| `ci` | Automazione dei controlli in integrazione continua. |
| `chore` | Manutenzione senza modifiche a sorgenti o test. |
| `revert` | Annullamento di una modifica. |

L'ambito identifica il modulo, `app` o `obd-core`. `progetto` identifica metodo,
strumenti e specifiche del processo di sviluppo comuni ai moduli. Le stesse
aree si usano per i design package. Si omette l'ambito quando un intervento
trasversale non ha un proprietario unico; il corpo chiarisce perché è
indivisibile. Non inventare moduli o aree funzionali prendendoli da altri repo.

Footer: `Refs: ADR-0003` oppure `Refs: progetto/D-0001`, quando pertinenti.
Una rottura di compatibilità usa `!` e un footer `BREAKING CHANGE:` che spiega
l'effetto su dati, contratti o aggiornamenti. Esempi:

```text
docs(progetto): adottate le regole di sviluppo

Refs: ADR-0003
```

```text
fix(obd-core): riconosciuto l'avanzamento della ricerca del protocollo

Refs: ADR-0002
```

Una consegna di specifiche può comprendere `.feature` come artefatti del design;
usare `docs` quando lo scopo è consolidare il design. Una modifica ai soli
test eseguibili usa `test`. La formattazione generale iniziale si propone in
un commit `refactor` distinto dalle modifiche di comportamento e dalla
configurazione degli strumenti, per rendere leggibile la revisione.

## Git, revisione e rami

La sessione propone i file e i messaggi; il proprietario esegue i comandi Git
secondo `AGENTS.md`. Questo documento non autorizza comandi, commit, push,
creazione di rami o inizializzazione del repository.

Il proprietario rivede le modifiche prima di consolidarle. Non si introduce un
obbligo di pull request per il lavoro individuale. Quando il repository è
gestito con Git, `main` resta rilasciabile; lavorazioni lunghe o che non devono
restare a metà possono usare rami brevi `<tipo>/<descrizione-con-trattini>`.
Non si riscrive la storia condivisa. Non si importa la gestione dei rami di
rilascio di un parco server, che qui non esiste.

## Versioni, aggiornamenti e firma

La versione dell'app segue `MAJOR.MINOR.PATCH`: nuova funzionalità compatibile
incrementa la minor, una correzione incrementa la patch; incompatibilità
deliberate si dichiarano nel design e nelle note di rilascio. Ogni nuovo
pacchetto distribuito incrementa anche `versionCode`. Un intervento ai soli
documenti non produce di per sé una nuova versione dell'app.

Adottare release stabili e supportate dall'intero stack Android interessato.
Verificare la compatibilità tra Java, Gradle, Android Gradle Plugin, Kotlin,
Compose e librerie prima di aggiornare. Fissare le versioni, conservare il
wrapper con il suo checksum e verificare build e test. Nessun aggiornamento
generale delle dipendenze è implicito nell'importazione del metodo. Versione
Android del telefono, `minSdk`, `compileSdk` e `targetSdk` sono scelte distinte.

Conservare la continuità della firma negli aggiornamenti. La chiave
`signing/debug.keystore`, già inclusa nei sorgenti 0.2.1, è una chiave debug
con credenziali standard e un'eccezione esplicita alla regola generale sui
segreti: non rimuoverla o sostituirla durante l'adozione delle regole.
Credenziali reali e future chiavi di rilascio non entrano nel repository.
Non aggirare le esclusioni delle configurazioni locali.

## Controlli attualmente disponibili

Usare Java Development Kit (JDK) 17 e l'ambiente Android descritto nel README.
I comandi si eseguono dalla root con il wrapper; su Windows usare
`gradlew.bat`.

```sh
./gradlew :obd-core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

I test del nucleo usano Java Virtual Machine (JVM), JUnit 4 e tempo simulato;
quelli Android includono Robolectric. Questi controlli non richiedono un
telefono per le suite locali e non ne certificano il comportamento reale.
La prima risoluzione delle dipendenze può richiedere la rete.

## Controlli adottati, ancora da integrare

Alla data del 10 novembre 2026, Cucumber e Spotless con ktlint sono scelte
accettate in ADR-0003, ma non sono configurati nella build. Lo scenario pilota
Gherkin è un artefatto di design, senza runner o step eseguibili. Il perimetro
dell'integrazione è [progetto/D-0001](docs/design/progetto/D-0001-verifiche-automatiche/README.md),
ancora `in design` e in attesa della conferma separata del proprietario.

Dopo l'integrazione, il controllo di formattazione dovrà far parte delle
verifiche ordinarie. `spotlessCheck` verificherà Kotlin e script Gradle;
`spotlessApply` sarà la correzione esplicita. I controlli non dovranno
correggere automaticamente i sorgenti. Nessun hook Git è previsto.
L'implementazione aggiornerà questa sezione con i comandi realmente provati.

## Consegna

Indicare file esatti, documenti attuati, controlli eseguiti, risultati ed
eventuali verifiche residue. Non riciclare gli esiti di una vecchia build
come risultati della consegna corrente. I rapporti stanno in
`docs/verification/`; documentare prove fisiche separatamente.

Proporre il testo del commit e, se necessario, la separazione in commit
coerenti. Commit e pubblicazione di un pacchetto restano azioni distinte.
