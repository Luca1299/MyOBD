# ADR-0003 — Metodo di sviluppo e verifiche

**Tipo:** metodo **Stato:** accettato **Data:** 2026-11-10

Approvato dal proprietario con il comando esplicito `approvo` il 10 novembre
2026, dopo la risoluzione dei tre punti di scelta sul metodo.

## Contesto

Multipla OBD dispone di due Architecture Decision Record (ADR), test JUnit 4,
verifiche Android con Robolectric e rapporti delle versioni 0.2.0 e 0.2.1.
Il metodo precedente è riassunto nel README, ma mancano istruzioni comuni agli
agenti, convenzioni indicizzate per le decisioni e una guida normativa di stile.
I primi ADR contengono anche requisiti e risultati delle verifiche.

Il proprietario richiede di adottare le regole di sviluppo di Naedo preservando
l'autonomia di questo progetto. Lo stack qui è Android/Kotlin/Compose: regole
di dominio, architettura server, database e integrazioni di Naedo non sono
applicabili. La richiesta comprende discussione, gestione dei dubbi,
approvazioni, stile e verifiche.

## Decisione

### Metodo e responsabilità dei documenti

Le istruzioni comuni vivono in `AGENTS.md`; `CLAUDE.md` le richiama senza
duplicarle. Le convenzioni operative del repository sono in
`CONTRIBUTING.md`, lo stile in `docs/guides/code-style.md`.

L'overview descrive l'architettura corrente. Gli ADR ne motivano le decisioni
durevoli. I design package descrivono capacità e comportamenti attesi; includono
anche le capacità trasversali del processo di sviluppo nell'area `progetto`.
I rapporti di verifica documentano risultati ed evidenze. Gli indici rendono
pubblici ADR e package; la stessa informazione non ha più sedi concorrenti.

Le sessioni di design discutono un tema delimitato. Le domande hanno numeri
stabili e la forma `[DA CHIEDERE: domanda precisa]`. Le risposte mancanti non
sono inventate e l'approvazione non le risolve implicitamente.

### Due atti distinti

Il comando locale è `APPROVO`, già usato nel progetto; è riconosciuto senza
distinzione fra maiuscole e minuscole quando espresso come comando esplicito.
Autorizza le scritture concordate dopo il riepilogo dei dubbi aperti.

Il proprietario conferma separatamente quando una specifica è pronta per il
coding, portando il package da `in design` a `confermato`. La sessione non lo
promuove autonomamente. L'approvazione documentale non autorizza da sola
l'implementazione di un package non confermato.

### Ciclo delle decisioni

La numerazione degli ADR è cronologica e immutabile. Prima della prima
installazione sul telefono di una versione che attua la decisione, è possibile
rivedere direttamente il documento approvando e dichiarando la revisione.
Da quella installazione una variazione richiede un nuovo ADR che superi il
precedente. Il superamento si registra nel nuovo documento e nell'indice,
senza modificare l'ADR diventato immutabile.

I due ADR preesistenti e i rapporti associati sono conservati senza
riclassificazioni distruttive o migrazioni dei loro contenuti.

### Stile, riuso e verifiche

Si adottano naming espressivo, responsabilità chiare, tipi appropriati,
commenti orientati allo scopo, riuso dei percorsi esistenti e verifica dei
contratti delle librerie effettivamente impiegate. Le prescrizioni di
persistenza e del frontend server di Naedo non sono importate.

Per funzionalità nuove o modificate si adottano scenari di Behavior-Driven
Development (BDD) in Gherkin con Cucumber. Gli step sono in inglese, con resa
italiana sotto il titolo dello scenario; le `.feature` nascono nel design e
vivono nel source tree del modulo. JUnit e Robolectric restano validi e non
si convertono in blocco. L'integrazione iniziale usa il percorso JUnit 4
esistente, senza dipendenze Spring.

La formattazione è verificata con Spotless e ktlint, stile `intellij_idea`,
configurato per i percorsi Kotlin di entrambi i moduli e per Compose. I
controlli verificano senza correggere implicitamente i file; la correzione è
un'azione esplicita. La formattazione generale iniziale resta distinta dalle
modifiche di comportamento. Le versioni degli strumenti saranno fissate dopo
la verifica di compatibilità con la build esistente, senza aggiornare in blocco
lo stack applicativo.

La scelta degli strumenti è accettata, ma questo consolidamento è documentale:
non li configura e non dichiara eseguibile il nuovo scenario pilota. Il coding
dell'integrazione attende la conferma separata della sua specifica.

I test pertinenti precedono le modifiche al codice applicativo. Le regressioni
si dimostrano prima e dopo; prove automatiche, resa dell'interfaccia e hardware
reale hanno evidenze distinte. La consegna esplicita verifiche e limiti.

### Gestione del repository

I comandi Git richiedono una richiesta esplicita del proprietario. Nessuna
inizializzazione, commit o push avviene di iniziativa. La chiusura include i
file esatti e un messaggio Conventional Commits proposto. Le revisioni con
più agenti richiedono un incarico esplicito.

## Alternative considerate

| Alternativa | Valutazione |
| --- | --- |
| Copiare integralmente le regole e la build di Naedo | Scartata: importerebbe vincoli server, dominio e dipendenze estranei. |
| Mantenere le regole soltanto nel README | Scartata: confonde guida d'uso, metodo e documenti autorevoli. |
| Usare un solo assenso per scrittura e avvio del coding | Scartata dal proprietario: approvazione e conferma della specifica restano distinte. |
| Rendere un ADR immutabile alla sola approvazione o consegna del pacchetto | Scartata dal proprietario: il confine è la prima installazione sul telefono. |
| Mantenere solo i test attuali o rinviare entrambi gli strumenti | Scartata dal proprietario: Cucumber e formattazione sono inclusi nell'adozione. |
| Convertire subito tutti i test e riscrivere gli ADR storici | Scartata: amplia il perimetro senza necessità per l'adozione. |

## Conseguenze e compromessi

La preparazione delle specifiche richiede un passaggio di conferma esplicito.
Gherkin e step aggiungono uno strato da mantenere, utile perché il proprietario
può rivedere gli scenari prima dell'implementazione. Le vecchie suite restano
parte delle verifiche e il primo scenario pilota riusa un comportamento già
deciso, senza introdurre funzionalità nell'app.

Il codice storico non diventa conforme per effetto di questo ADR. La
formattazione avrà un passaggio esplicito; gli adeguamenti semantici di stile
si affrontano negli interventi pertinenti senza riscritture indiscriminate.
Gli strumenti non sono dichiarati operativi prima della loro verifica.

Un eventuale ripensamento di Cucumber è una decisione di metodo: se il
linguaggio naturale smettesse di essere usato per rivedere il comportamento
prima del coding, riesaminare il beneficio rispetto al costo di feature e step.
Anche il momento di immutabilità degli ADR si riesamina se cambia la modalità
di distribuzione, senza riscrivere decisioni già consolidate.

## Punti da chiedere

Nessun punto aperto. Le risposte concordate sono registrate nelle decisioni:
conferma separata, immutabilità dalla prima installazione e adozione di entrambi
gli strumenti. L'approvazione ricevuta autorizza il consolidamento documentale.

## Riferimenti

- Regole di sviluppo del repository Naedo: `AGENTS.md`, `CONTRIBUTING.md`,
  `docs/guides/code-style.md`, convenzioni ADR e design, lette il 10 novembre
  2026; riferimento di origine del metodo, senza dipendenza operativa da quel repo.
- [Cucumber-JVM: installazione e integrazione JUnit](https://cucumber.io/docs/installation/java/).
- [Spotless per Gradle: requisiti, Kotlin e sorgenti Android](https://github.com/diffplug/spotless/blob/main/plugin-gradle/README.md).
