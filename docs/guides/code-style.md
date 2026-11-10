# Stile del codice — Multipla OBD

Normativo per ogni intervento Kotlin e per i relativi test. Leggere per intero
prima di scrivere o modificare codice. Il metodo delle sessioni è in
[AGENTS.md](../../AGENTS.md), le convenzioni linguistiche e operative in
[CONTRIBUTING.md](../../CONTRIBUTING.md), i confini del sistema
[nell'overview](../architecture/overview.md).

Questa guida adatta le regole generali di Naedo allo stack Android del progetto.
I template di persistenza e del server non sono importati. L'adozione non
dichiara conforme il codice storico né autorizza una sua riscrittura generale:
applicare le regole al perimetro dell'intervento, segnalando incoerenze che
richiedono una decisione più ampia.

## Nomi, struttura e valori

- Identificatori in inglese, autodocumentanti. Preferire un nome esplicito a
  un'abbreviazione ambigua; evitare prefissi che ripetono il contesto.
- Classi e funzioni hanno una responsabilità comprensibile; estrarre operazioni
  nominate quando un metodo richiede sezioni per essere spiegato. Evitare
  separatori decorativi, salvo configurazioni lineari che non conviene spezzare.
- Numeri con significato di protocollo, limite o durata hanno costanti nominate,
  con unità esplicita quando pertinente, per esempio `_MS` o `_BYTES`. Collocarle
  nel proprietario della regola o in un riferimento comune realmente condiviso.
- Chiavi persistite, comandi e valori di dominio non si ripetono come stringhe
  sparse. Usare costanti, enumerazioni o tipi dedicati. I byte e le stringhe del
  protocollo conservano il formato richiesto dal contratto.
- `val` per valori che non devono cambiare; mutabilità limitata al componente
  responsabile dello stato. Le viste esposte non devono permettere a un
  chiamante di modificare indirettamente lo stato interno.
- Insiemi chiusi di valori: `enum class` oppure gerarchie `sealed` con `when`
  esaustivo. Le interfacce che altri componenti devono implementare non sono
  `sealed` se questo impedisce l'estensione prevista.
- Valori e fotografie dello stato: `data class`, preferibilmente immutabili.
  Un oggetto con identità e ciclo di vita proprio non diventa una `data class`
  per ottenere automaticamente `copy()` o confronti inappropriati.
- Usare tipi distinti nelle firme pubbliche quando lo scambio di identificativi,
  unità o valori primitivi validi produrrebbe un errore silenzioso. Il tipo deve
  rappresentare un contratto utile, senza introdurre astrazioni decorative.

## Precondizioni ed errori

`require` valida l'input del chiamante; `check` lo stato necessario per
l'operazione. Le condizioni complesse si scompongono in predicati con nomi
espressivi. Conservare la distinzione fra dati validi, risultato vuoto,
prerequisito mancante, errore di lettura e operazione non conclusiva prevista
dal design; non trasformare un errore in un successo apparente.

Niente `printStackTrace()`. Gli errori hanno contesto sufficiente a individuare
operazione e fase, usando i meccanismi di registro già presenti. Non loggare
segreti o dati personali non necessari. La presentazione all'utente mantiene
un messaggio comprensibile e le informazioni necessarie per agire.

I `catch` non nascondono errori e non sostituiscono precondizioni note.
Le coroutine propagano la cancellazione; eventuali intercettazioni sono
motivate dal rilascio delle risorse o dal contratto dell'operazione, senza
trasformare la cancellazione in un risultato diagnostico valido.

## Commenti e documentazione

Nomi e struttura sono il primo strumento di spiegazione. Commenti e KDoc
chiariscono prima scopo e responsabilità, poi vincoli e ragioni delle scelte
non ovvie. Non parafrasano istruzioni o elencano soltanto i meccanismi usati.

KDoc sulle classi pubbliche, sui contratti e sui metodi non banali: spiegare
precondizioni, risultati significativi, gestione delle risorse e concorrenza
quando fanno parte del contratto. Evitare commenti che dipendono dalla memoria
della conversazione o che dichiarano risultati di test non riproducibili.

## Riuso e contratti delle librerie

1. Ricostruire il percorso prima di modificarlo: chiamanti, componenti, modello,
   configurazione e test. Individuare chi possiede la regola e le invarianti.
2. Verificare la versione effettivamente risolta delle librerie e il contratto
   rilevante nella documentazione ufficiale. Leggere il sorgente o riprodurre
   il caso quando necessario; il ricordo di un'altra versione non basta.
3. Riusare il percorso o il punto di estensione esistente. Conservare un solo
   proprietario della regola; copiare un'implementazione non costituisce riuso.
4. Una soluzione alternativa richiede un limite concreto e verificabile del
   meccanismo esistente e la spiegazione delle garanzie da conservare. Se cambia
   una decisione architetturale, tornare al ciclo di design prima di scrivere.
5. Verificare l'integrazione sul percorso effettivo e le garanzie interessate,
   non soltanto un risultato ottenuto da una riproduzione parallela nel test.

## Confini, stato e lavoro asincrono

Rispettare la dipendenza `app` → `obd-core`. Il nucleo non dipende da Android o
Compose; il livello Android adatta trasporto, persistenza e ciclo di vita ai
contratti del nucleo. L'interfaccia osserva e comanda la sessione secondo il
proprietario già stabilito, senza crearne una parallela per comodità.

Le operazioni concorrenti preservano il proprietario dello stato. Timeout,
annullamento, risposte obsolete e rilascio delle risorse sono parte del
contratto quando pertinenti. Evitare richieste, listener o tentativi duplicati
al rientro nella schermata. Le operazioni bloccanti usano il contesto adatto;
le operazioni sospendibili conservano la cancellazione strutturata.

Non introdurre architetture, framework o registri alternativi per risolvere un
caso locale senza ricostruire i meccanismi già presenti.

## Dati scambiati e protocolli

Il contratto comprende rappresentazione effettiva e significato: nomi, tipi,
unità, precisione, formato delle date, valori assenti e vuoti, limiti e gestione
degli errori, per i campi interessati. Valori tipizzati nel codice non bastano
a dimostrare che il destinatario legga il contenuto prodotto.

- Usare modelli tipizzati ai confini e parsing esplicito del protocollo previsto.
  Non importare un formato JSON (JavaScript Object Notation) o una libreria di
  serializzazione se il contratto non li richiede.
- I test attraversano parser, configurazioni e percorso realmente usati.
  Collegare output del produttore e decoder del consumatore; due fixture
  inventate indipendentemente non dimostrano compatibilità.
- Provare valori significativi, collezioni popolate e campi opzionali anche
  quando valorizzati, oltre ad assenze ed errori previsti dal contratto.
- Per una regressione usare un campione riproducibile e anonimizzato quando
  necessario. Verificare il fallimento precedente e il successo della correzione.
- Non rendere un decoder permissivo soltanto per nascondere dati errati.
  Normalizzazioni e tolleranze devono essere limitate al contesto approvato,
  preservando i dati e rifiutando le anomalie che restano fuori dal contratto.
- Per rapporti e file esportati verificare il contenuto prodotto, le unità e
  la distinzione tra valori reali, demo ed esiti non conclusivi pertinenti.

## Letture, costo e coerenza

Un'ottimizzazione conserva il contratto e riduce il lavoro complessivo.
Individuare richieste duplicate, elaborazioni ripetute, memoria trattenuta e
lavoro sul thread dell'interfaccia. Non introdurre cache o limiti silenziosi
che alterano dati o disponibilità delle funzioni.

Confrontare prima e dopo con gli stessi dati e criteri. Misurare separatamente
trasporto, elaborazione e resa quando pertinenti; registrare ambiente, build,
carico e numero di prove. Una singola misura favorevole non dimostra un
miglioramento. I test verificano anche le garanzie concrete dell'intervento,
come l'assenza di richieste duplicate, quando sono il suo obiettivo.

Non si importano paginazione server, query, repository o regole di persistenza
di Naedo in un progetto che non adotta quei meccanismi.

## Interfaccia Compose

Riusare componenti e meccanismi Compose/Material già adottati. Lo stato e gli
effetti hanno un proprietario riconoscibile; la ricomposizione non avvia lavoro
duplicato. Risorse ed effetti rispettano il ciclo di vita pertinente.

Applicare le convenzioni Compose: funzioni che rappresentano componenti e
restituiscono `Unit` con nome in PascalCase, funzioni che producono valori con
nome coerente con il loro ruolo. Il formatter considera l'annotazione
`Composable`, senza imporre nomi inappropriati o disabilitare tutti i controlli.

Usare risorse per i testi dell'interfaccia secondo `CONTRIBUTING.md`.
Preservare leggibilità con le impostazioni del dispositivo, nomi accessibili
per le azioni, stati di attesa/errore e accessibilità dei controlli. I testi
di aiuto aggiungono informazioni utili per agire, senza ripetere il titolo o
lo stato già visibile. I vincoli di schermata e i criteri visivi specifici
appartengono al package confermato; nessun layout web di Naedo viene copiato.

## Scenari di comportamento e test

Behavior-Driven Development (BDD) con Gherkin/Cucumber per funzionalità nuove
o modificate. Le `.feature` sono artefatti di design scritti su `APPROVO`;
il coding implementa gli step dopo la conferma della specifica. Vivono in
`src/test/resources/features/` del modulo, mai in `docs/`.

- Scenari dichiarativi nel linguaggio del dominio, con lo stesso vocabolario
  del codice. Descrivere condizioni, azioni ed esiti; evitare sequenze del tipo
  «clicco questo pulsante» per specificare una regola di connessione.
- Step in inglese. Sotto il titolo, prima degli step, inserire la resa italiana
  come descrizione libera, senza `#`, nello stesso ordine Dato–Quando–Allora.
  Fanno fede gli step inglesi; aggiornarne insieme la resa italiana.
- Gli step attraversano il componente responsabile effettivo. Quelli della
  politica di connessione usano il coordinatore, non una sua imitazione.
- Step nel modulo verificato, senza librerie condivise tra moduli. Usare gli
  strumenti di test esistenti, estraendo supporto comune solo quando necessario
  nel modulo, senza copiare politiche applicative nelle fixture.
- Tempo simulato per attese e timeout quando possibile. Le prove automatiche
  locali non richiedono collegamenti reali alla vettura.
- JUnit e Robolectric restano validi per unità, integrazione Android e casi
  tecnici. Non convertire in blocco le suite già presenti. Le verifiche della
  resa dell'interfaccia e quelle fisiche restano distinte dagli scenari di dominio.
- Runner e report devono rendere visibili scoperta ed esecuzione degli scenari;
  step indefiniti, pendenti o ambigui non sono successi.

Scrivere test pertinenti prima del codice applicativo. Verificare esiti e
garanzie reali, includendo errori e concorrenza se coinvolti. Non aggiungere test
che ripetono l'implementazione senza proteggere un comportamento o un contratto.
Per modifiche di sola documentazione usare verifiche documentali appropriate.

## Formattazione e revisione

La scelta è ktlint via Spotless, stile `intellij_idea`, con configurazione
comune adatta a Compose. Lo stato dell'integrazione e i comandi effettivamente
disponibili sono in `CONTRIBUTING.md`: la scelta normativa non significa che
il formatter sia già configurato.

Il controllo non modifica i sorgenti; l'applicazione delle correzioni è
esplicita. Non escludere file di produzione o di test per nascondere errori.
Soppressioni circoscritte richiedono una ragione documentabile. La formattazione
generale iniziale è separata dai cambiamenti di comportamento.

Prima della consegna verificare:

- responsabilità e contratti rispettati, con un solo proprietario delle regole;
- nessuna nuova decisione funzionale rimasta soltanto in conversazione;
- test pertinenti e regressioni riproducibili, con esiti effettivi;
- formattazione, compilazione e analisi statica quando pertinenti e disponibili;
- esiti fisici distinti da simulazioni e limiti dichiarati;
- file esatti, documenti attuati e messaggio di commit proposto.
