# D-0001 — Diagnosi OBD motore in sola lettura

**Stato:** in design **Area:** app **Data:** 2026-11-10

OBD significa On-Board Diagnostics; DTC significa Diagnostic Trouble Code,
codice diagnostico di guasto. Architecture Decision Record (ADR) indica il
registro delle motivazioni; Behavior-Driven Development (BDD) indica gli
scenari eseguibili di comportamento.

**ADR correlati:** [ADR-0001](../../../adr/0001-collegamento-android-elm327.md),
[ADR-0002](../../../adr/0002-primo-collegamento-e-riabilitazione-automatica.md),
[ADR-0003](../../../adr/0003-metodo-di-sviluppo-e-verifiche.md).

**Scenari di design:**

- [Risultati diagnostici e descrizioni](../../../../obd-core/src/test/resources/features/diagnostics/read-only-diagnostics.feature).
- [Readiness](../../../../obd-core/src/test/resources/features/diagnostics/readiness.feature).
- [Freeze frame](../../../../obd-core/src/test/resources/features/diagnostics/freeze-frame.feature).
- [Sessione di diagnosi](../../../../app/src/test/resources/features/diagnostics/diagnosis-session.feature).
- [Rapporto esportato](../../../../app/src/test/resources/features/diagnostics/diagnosis-report.feature).

Il proprietario ha autorizzato il consolidamento di questa specifica e degli
scenari con `approvo` il 10 novembre 2026. Il package resta `in design`:
la conferma separata per il coding non è stata ricevuta. Le feature sono
artefatti di design, non test già eseguibili o superati.

## Scopo e perimetro

Raccogliere in una diagnosi manuale le informazioni OBD generiche del motore
effettivamente leggibili: stato della spia, codici di guasto, completamento dei
monitor antinquinamento, freeze frame e un campione dei parametri attuali.
Il risultato deve rendere visibili disponibilità, qualità e limiti dei dati,
ed essere esportabile in un rapporto di testo.

«Completa» indica l'acquisizione delle sezioni definite da questo package.
Non significa scansione di ogni centralina della vettura, lettura di tutti i
parametri possibili o accertamento della causa esatta di un guasto.

Il taglio riusa collegamento, proprietario della sessione e serializzazione
delle richieste già stabiliti. Non introduce una nuova architettura, una nuova
connessione parallela o una modifica delle politiche di riconnessione. La
specifica funzionale e gli scenari attuano le decisioni esistenti; non nasce
un nuovo ADR per il solo fatto di aggiungere queste letture.

Restano fuori cancellazione dei codici, attuazioni, codifica, comandi arbitrari,
accesso specifico alle centraline Fiat/metano, freni e airbag, aggiornamento
continuo dei parametri, grafici, storico persistente, confronto fra diagnosi,
cause possibili e suggerimenti di riparazione. La normale configurazione
dell'adattatore continua a seguire il collegamento esistente.

## Decisioni del tema

I numeri delle domande già discusse restano stabili.

| Punto | Stato | Decisione o informazione acquisita |
| --- | --- | --- |
| 1 | Risolto, contesto di collaudo | La versione 0.2.1 non è ancora stata provata sul Motorola e sulla vettura. Il proprietario invierà il registro dopo la prova; il design può proseguire senza dichiarare validata la compatibilità fisica. |
| 2 | Risolto | La diagnosi parte soltanto dal pulsante «Avvia diagnosi», mai automaticamente al collegamento. |
| 3 | Risolto | Risultato in sessione ed esportazione; lo storico salvato nell'app è un incremento successivo. |
| 4 | Risolto | Codice, descrizione verificata e limiti d'interpretazione; approfondimenti su cause e controlli restano successivi. |

Nessun punto DA CHIEDERE aperto sul perimetro consolidato. La conferma per il
coding e le prove hardware restano passaggi distinti, non risposte da inventare.

## Contenuto della diagnosi

### Riepilogo e contesto

Ogni tentativo costituisce un'acquisizione distinta, con inizio, fine e durata,
stato complessivo, adattatore e protocollo quando disponibili. Il contesto
identifica sessione reale o demo e alimentazione annotata manualmente all'avvio:
`Benzina`, `Metano`, `Non annotata`. Quest'ultima è ammessa e non impedisce la
lettura. L'app non deduce l'alimentazione attuale dai parametri generici.

L'annotazione e i dati di un'acquisizione terminata non cambiano retroattivamente
quando la persona prepara una lettura successiva. Il cambio dell'annotazione
non è disponibile durante la diagnosi.

La spia è lo stato richiesto dalla centralina, non una verifica visiva della
lampada. Il numero di codici confermati dichiarato viene conservato come dato
ricevuto, senza ricostruirlo sommando categorie o risposte di origine incerta.

Le letture sono sequenziali, non simultanee: un campione è riferito al proprio
momento di acquisizione. Il rapporto non presenta l'insieme come una fotografia
atomica dell'intera vettura.

### Codici di guasto e descrizioni

Restano distinte le categorie memorizzati, in attesa e permanenti. Lo stesso
codice presente in categorie diverse conserva ogni appartenenza; la
deduplicazione all'interno di una categoria non lo elimina dalle altre.

La descrizione proviene da un catalogo locale con fonte e versione verificabili.
L'inclusione delle voci deve avere provenienza e condizioni d'uso accertate:
questo consolidamento non fornisce o certifica un catalogo di codici completo.
La scelta concreta della fonte è una verifica tecnica dell'implementazione,
nel vincolo già deciso di descrizioni attendibili disponibili localmente.

Se una voce verificata manca, il codice resta visibile con «Descrizione non
disponibile». Non dedurre il significato di un codice specifico del costruttore
da una voce generica simile o dalla sola dichiarazione del modello della vettura.
La descrizione spiega la condizione rilevata, senza identificare automaticamente
la causa esatta o un componente da sostituire.

L'origine della centralina è indicata solo se accertata. Quando non lo è,
mostrare il limite senza attribuire le risposte alla gestione benzina o metano.
Non trasformare elenchi aggregati in una scansione completa di tutte le
centraline o in un conteggio globale verificato della vettura.

### Readiness

Mostrare il completamento dei monitor antinquinamento dall'ultima cancellazione
dei codici, secondo le informazioni effettivamente restituite. Per ogni monitor
interpretato distinguere:

- `Completato`: dichiarato supportato e controllo completato.
- `Incompleto`: dichiarato supportato e controllo non ancora completato.
- `Non supportato`: l'indisponibilità è dichiarata dal dato valido interpretato.

Questi sono stati del monitor. Una risposta mancante o malformata è invece un
esito della lettura e non imposta automaticamente i monitor a «Non supportato».
La spia spenta non implica monitor completati. Il completamento non certifica
lo stato generale dell'auto né l'esito della revisione.

### Freeze frame

Il freeze frame è il gruppo di parametri memorizzato dalla centralina in
relazione a un guasto. Mostrare il codice associato quando restituito e i dati
effettivamente presenti nel frame disponibile. Non promettere un frame per
ogni codice né uno storico di eventi.

I dati di supporto e i valori del freeze frame sono verificati nel relativo
contesto, senza dedurli dalla sola disponibilità dei parametri attuali.
Un parametro mancante nel frame non viene sostituito con il valore attuale.
Una risposta valida che dichiara l'assenza del frame resta distinta da un
timeout, un errore, una funzione non supportata o una risposta non conclusiva.

I parametri parzialmente disponibili restano consultabili con il limite
segnalato. Non costruire associazioni fra codice, frame e centralina che le
risposte non permettono di accertare. L'ora di lettura del frame non diventa
l'ora del guasto: se la centralina non fornisce la data dell'evento, non
inventarla o ricavarla dall'orologio del telefono.

### Parametri attuali

Acquisire un campione del catalogo già gestito, interrogando i parametri
dichiarati disponibili e rispettando le distinzioni tra dati mancanti ed
errori. Il primo taglio non estende il catalogo a sensori ulteriori.

| Parametro | Unità |
| --- | --- |
| Giri motore | giri/min |
| Velocità | km/h |
| Temperatura refrigerante | °C |
| Pressione assoluta nel collettore | kPa |
| Posizione farfalla | % |
| Correzione carburazione breve, banco 1 | % |
| Correzione carburazione lunga, banco 1 | % |

Un valore numerico zero valido resta un dato; non indica automaticamente
indisponibilità. Presentare questo campione separatamente dal freeze frame.

## Stati delle letture e del risultato

Ogni sezione riporta il proprio esito e conserva i dati acquisiti quando
disponibili. Gli esiti non si appiattiscono su un solo messaggio di successo.

| Esito della sezione | Significato |
| --- | --- |
| Dati acquisiti | Risposta valida, con il contenuto previsto. |
| Risposta valida senza codici o senza frame | Assenza dichiarata da una risposta interpretabile, limitata alla sezione. |
| Funzione non supportata | Indisponibilità accertata; non dedotta dal solo silenzio o da `NO DATA`. |
| Nessuna risposta o lettura fallita | Lettura non conclusiva, con motivo disponibile. |
| Non eseguita | Sezione non ancora interrogata o non raggiunta. |
| Interrotta | Acquisizione fermata prima di concludere la sezione. |

Un contenuto parziale ha i dati leggibili e l'indicazione di ciò che manca.
Il risultato complessivo usa:

- `Completata`: tutte le sezioni previste hanno un esito conclusivo, compresa
  un'assenza valida o un'indisponibilità accertata con il limite esposto.
- `Parziale`: il tentativo è terminato con almeno una lettura non conclusiva,
  un contenuto incompleto o una sezione non acquisita per un errore.
- `Interrotta`: il tentativo è stato interrotto dalla persona o dalla perdita
  del collegamento prima della conclusione.

Un tentativo che termina senza acquisire dati, con errori di lettura ma
trasporto ancora utilizzabile, resta esplicito come parziale senza dati
acquisiti, mai come scansione senza guasti. La perdita del trasporto prima
della conclusione determina invece l'interruzione. I tentativi non avviati
per prerequisiti mancanti restano «Diagnosi non avviata».

Lo stato complessivo descrive l'acquisizione. Non è un giudizio sulla salute
della vettura e non nasconde i limiti delle singole sezioni.

## Avvio, concorrenza e continuità

Il comando richiede centralina raggiunta e nessuna lettura già in corso. Con
prerequisiti mancanti resta indisponibile e la schermata indica l'azione da
compiere nel collegamento. Una pressione ripetuta non accoda altre diagnosi.

All'avvio fissare il contesto della nuova acquisizione e mostrare la fase
corrente. Interrogare le sezioni in sequenza, mantenendo i risultati delle
richieste riuscite. Un errore di contenuto locale consente di proseguire sulle
sezioni ancora leggibili; un errore che interrompe il trasporto ferma la
scansione e segue la gestione del collegamento esistente.

Una sola operazione usa la sessione diagnostica. Durante la diagnosi anche
gli altri comandi manuali di lettura restano indisponibili. Il cambio di
scheda, la ricreazione dell'interfaccia e lo schermo spento non creano una
seconda acquisizione e non ne spostano la proprietà fuori dal servizio.

Il ritorno del collegamento non riavvia una scansione interrotta. La persona
la riavvia con il comando manuale. Le risposte tardive non aggiornano una
scansione diversa. Una nuova acquisizione non eredita valori dalle sezioni
riuscite di quella precedente: l'interfaccia e il rapporto identificano
chiaramente il tentativo a cui appartengono i risultati.

## Interruzione della diagnosi e disconnessione

`Interrompi diagnosi` ferma la sequenza al termine della richiesta già in
corso, senza inviarne altre. Durante l'attesa mostra «Interruzione in corso…».
La richiesta conserva il proprio limite di attesa: non si introduce un'attesa
senza scadenza. Una risposta valida già in corso può essere conservata;
le sezioni successive restano non eseguite e il risultato è interrotto.

La sola interruzione non chiude il collegamento se la richiesta si conclude
regolarmente. Se la richiesta scade o il trasporto viene perso, valgono gli
esiti e la gestione del collegamento già previsti. Non si promette che il
collegamento sopravviva a un errore del trasporto.

`Scollega` chiude invece la sessione tramite il comando esplicito esistente,
ferma la diagnosi e conserva in memoria i risultati già acquisiti. La distinzione
evita di equiparare l'interruzione della sequenza alla cancellazione immediata
di una richiesta pendente, che nel trasporto attuale comporta la chiusura.

## Conservazione ed esportazione

Il risultato termina in memoria e resta consultabile dopo la disconnessione
finché la sessione applicativa è disponibile. Non si introduce recupero dopo
terminazione del processo, archivio su disco o storico navigabile. Per
conservare una diagnosi oltre la sessione occorre esportare il rapporto.

Una nuova diagnosi produce risultati distinti e può sostituire il tentativo
precedente nella sessione; la schermata ricorda che l'esportazione lo conserva.
Questa capacità non introduce un confronto fra acquisizioni.

Il rapporto TXT è una copia dei risultati raccolti, ottenuta senza nuove
interrogazioni alla vettura. È disponibile anche per tentativi parziali o
interrotti una volta conclusi; durante l'acquisizione e l'interruzione in corso
l'esportazione del tentativo corrente non è disponibile.

Riportare contesto e tempi, alimentazione annotata, spia e conteggio dichiarati,
codici con categoria e descrizione disponibile, esiti delle sezioni, dati del
freeze frame, parametri attuali con unità e problemi incontrati. Esporre i
limiti sulla fonte delle risposte e sull'interpretazione. Le evidenze originali
disponibili nel registro restano incluse secondo i limiti dichiarati del
registro stesso; il rapporto non promette un tracciato illimitato.

La demo resta identificata nella schermata e nel rapporto. Non modifica
profilo, blocco o risultati reali usando i dati sintetici.

## Design dell'interfaccia

La scheda attuale `Errori` diventa `Diagnosi`; restano gli ingressi
`Collegamento`, `Parametri` e `Registro`. La nuova scheda raccoglie la diagnosi
con un solo comando principale; non duplica un comando di scansione in ogni
sezione. La lettura manuale della scheda Parametri resta distinta dal campione
associato alla diagnosi e non ne riscrive retroattivamente il risultato.

Ordine dei contenuti: titolo e contesto, selettore facoltativo di alimentazione,
comando principale e stato dell'operazione, riepilogo, codici, readiness,
freeze frame, parametri attuali, esportazione del rapporto concluso. Il comando
di esportazione riusa il percorso TXT esistente; il Registro mantiene la
consultazione degli scambi e il suo ingresso all'esportazione.

| Stato della schermata | Presentazione e azioni |
| --- | --- |
| Prima del collegamento | «Diagnosi non avviata», motivo del prerequisito mancante e indicazione della scheda Collegamento; nessun risultato inventato. |
| Pronta | Alimentazione selezionabile, `Avvia diagnosi` disponibile. |
| Acquisizione | Fase corrente, risultati già disponibili, `Interrompi diagnosi`; nuovi avvii e letture concorrenti indisponibili. |
| Interruzione richiesta | «Interruzione in corso…», nessun nuovo comando diagnostico; `Scollega` resta l'azione di chiusura esplicita. |
| Conclusa | Esito complessivo e sezioni con i propri limiti; esportazione disponibile anche per parziale o interrotta. |
| Collegamento perso | Risultato conservato con motivo dell'interruzione; riavvio solo manuale dopo il ripristino dei prerequisiti. |

Riusare tema, controlli Material e card già adottati in Compose, senza nuove
tecnologie. La presentazione delle sezioni diagnostiche è un componente
comune dell'app: titolo, esito testuale, contenuto e motivo dell'indisponibilità
sono coerenti per codici, readiness, freeze frame e parametri. I decoder dei
contenuti restano distinti; non si trasferiscono regole diagnostiche nei
componenti visivi.

Usare testi italiani nelle risorse secondo la guida di stile. Colori e icone
possono accompagnare gli stati, ma non sostituiscono etichette comprensibili.
Mantenere leggibilità con caratteri ingranditi, scorrimento e azioni raggiungibili,
nomi accessibili e stato riconoscibile dei controlli disabilitati. Non esiste
un prototipo visivo vincolante per questo taglio.

## Criteri di accettazione e copertura

Gli scenari usano risposte sintetiche controllate; non attestano compatibilità
del Motorola, dell'adattatore o delle centraline reali.

| Criterio | Scenari di riferimento |
| --- | --- |
| Categorie, assenze valide, errori e limiti non vengono confusi. | `DIA-C01`–`DIA-C05`, `DIA-S04`. |
| Descrizioni verificate o assenza esplicita, senza inferenze sulla causa o sulla centralina. | `DIA-C06`–`DIA-C08`, `DIA-T03`. |
| Readiness distingue supporto e completamento; una lettura fallita non produce monitor non supportati. | `DIA-R01`–`DIA-R04`. |
| Freeze frame distinto dai dati attuali, con assenze, parzialità e associazioni limitate alle evidenze. | `DIA-F01`–`DIA-F06`. |
| Solo avvio manuale, prerequisiti rispettati e una sola operazione attiva. | `DIA-S01`–`DIA-S03`. |
| Interruzione, disconnessione, riconnessione e risposte tardive preservano separazione e dati validi. | `DIA-S05`–`DIA-S08`. |
| Navigazione e ciclo di vita non duplicano la diagnosi; contesto e dati di acquisizioni diverse non si mescolano. | `DIA-S09`–`DIA-S11`. |
| Risultati in memoria ed esportazione senza introdurre uno storico. | `DIA-S12`, `DIA-T01`, `DIA-T02`, `DIA-T04`. |
| Demo e sola lettura rispettano il perimetro. | `DIA-S13`, `DIA-T05`, `DIA-C09`. |
| I valori zero validi restano dati e non modificano il conteggio dichiarato dei codici. | `DIA-C10`, `DIA-C05`. |

Il coding implementerà gli step nel modulo che verificano, sul percorso reale,
con tempo e trasporto controllati. Le verifiche Android devono coprire servizio,
ciclo di vita ed esportazione; la resa Compose va verificata anche nelle
schermate effettive. Servono confronto testo/interfaccia e casi di errore,
non soltanto compilazione o parsing delle feature.

L'integrazione degli strumenti è specificata in
[progetto/D-0001](../../progetto/D-0001-verifiche-automatiche/README.md), ancora
`in design`. Il presente consolidamento non la implementa né la conferma.
Prima di dichiarare eseguibili i nuovi scenari vanno resi operativi runner e
step nei moduli pertinenti; la sola presenza dei file non soddisfa i criteri.

## Riferimenti tecnici

La documentazione esterna informa la lettura dei servizi; non certifica
automaticamente il loro supporto sulla vettura. I comportamenti del prodotto
fanno fede in questo package.

- [Snap-on: servizi OBD-II](https://www.snapon.com/DiagnosticsManuals/PDL/7100/EG/Content/Common/SMX_Scanner/OBDII%20Service%20Modes.htm), consultato il 10 novembre 2026: dati attuali, freeze frame, categorie di codici e limiti della lettura generica.
- [Elm Electronics: manuale ELM327](https://www.elmelectronics.com/wp-content/uploads/2017/01/ELM327DS.pdf), consultato il 10 novembre 2026: comunicazione e conseguenze dei servizi, senza estensione implicita ai comandi di modifica.
