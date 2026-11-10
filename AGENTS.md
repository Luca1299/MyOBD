# Multipla OBD — Istruzioni di sviluppo

App Android nativa in Kotlin e Jetpack Compose. I moduli attuali sono `app`
e `obd-core`. La fonte di verità sull'architettura è
[l'overview](docs/architecture/overview.md): consultarla prima degli interventi,
senza riprogettare il sistema di propria iniziativa.

Le decisioni sono registrate in Architecture Decision Record (ADR).
Queste regole attuano [ADR-0003](docs/adr/0003-metodo-di-sviluppo-e-verifiche.md).
Sono normative per collaboratori e agenti. Il metodo deriva dalle regole di
Naedo, adattate a questo progetto; architettura, funzionalità, nomi di dominio
e dipendenze di Naedo non vengono adottati.

## Sedi delle informazioni

- Architettura corrente: `docs/architecture/overview.md`.
- Motivazioni delle decisioni: ADR, `docs/adr/`.
- Comportamenti attesi: design package, `docs/design/`.
- Stile del codice: `docs/guides/code-style.md`, normativo quanto questo file.
- Commit, rami, comandi e consegne: `CONTRIBUTING.md`.
- Risultati e limiti delle verifiche: `docs/verification/`.

Ogni informazione ha una sede autorevole. Gli altri documenti la richiamano
senza mantenerne copie concorrenti. La documentazione specifica di un modulo
vive nel modulo; quella trasversale nella `docs/` di root. I design package
sono centralizzati perché precedono l'implementazione.

## Regole di ingaggio

- I dubbi e le decisioni aperte si istruiscono e si chiedono al proprietario;
  non si inventano informazioni mancanti.
- Ogni domanda compare in un elenco numerato nella forma
  `1. [DA CHIEDERE: domanda precisa]`, anche quando è l'unica. I numeri restano
  stabili nel tema: quelli risolti non fanno rinumerare gli altri e non si
  riutilizzano. La regola vale in conversazione e nei documenti.
- Si prosegue sulle parti indipendenti; le parti che dipendono da una risposta
  restano sospese. Un'ipotesi non diventa un requisito.
- Prima di chiedere o eseguire `APPROVO`, riepilogare le domande aperte con i
  loro numeri. Se non ce ne sono, dichiararlo prima di scrivere.
- Se restano domande, il proprietario decide se risolverle oppure autorizzare
  esplicitamente un consolidamento parziale, precisando cosa si scrive e cosa
  resta sospeso. L'approvazione non risolve implicitamente i dubbi.
- Segnalare criticità e compromessi prima dell'esecuzione; niente assenso di
  cortesia. Esempi, prototipi e riferimenti ad altri progetti non diventano
  automaticamente requisiti o attività.
- Esplicitare gli acronimi alla prima occorrenza in ogni documento. Lingue e
  convenzioni degli identificatori sono definite in `CONTRIBUTING.md`.

## Sessioni di design e approvazione

Una sessione affronta un tema con un perimetro e un criterio di chiusura.
Parte dalla discussione: nessuna scrittura sul filesystem, comprese bozze,
appunti e file temporanei, prima del comando esplicito `APPROVO` del
proprietario. Il comando è riconosciuto anche in minuscolo o con altra
capitalizzazione quando è inequivocabilmente riferito al perimetro discusso.
Un generico assenso non lo sostituisce.

Ricevuto `APPROVO`, eseguire le sole scritture concordate. La stessa sessione
consolida le decisioni nei documenti pertinenti: una decisione rimasta soltanto
in conversazione non costituisce la specifica per il coding. Un cambio di
decisione riapre la discussione delle parti interessate.

L'approvazione delle scritture e la conferma di una specifica sono atti
distinti. `APPROVO` non promuove un design package a `confermato`. Il passaggio
è del proprietario, mai un'iniziativa dell'agente; convenzioni e stati sono in
`docs/design/README.md`. Il coding parte soltanto da specifiche confermate e
da un incarico che ne autorizza l'implementazione.

Se il tema comprende un'interfaccia utente, decidere prima del coding stati,
azioni, errori, testi necessari, componenti da riusare e criteri di verifica.
Un prototipo può essere un riferimento visivo identificabile; non decide
autonomamente comportamenti, tecnologia o proprietà dei componenti.

Quando cambia una decisione architetturale, su `APPROVO` aggiornare nella
stessa sessione l'ADR e l'overview risultante. Prima della prima installazione
sul telefono si può rivedere l'ADR esistente secondo il suo ciclo di vita;
dopo serve un nuovo ADR che superi il precedente. Una precisazione che non
cambia decisioni richiede l'approvazione dell'aggiornamento, senza un nuovo ADR.

Chiudere il tema nei file prima di aprirne uno nuovo. Se il lavoro richiede
un'altra sessione, il ponte sono i documenti consolidati. L'istruzione di avvio
del coding indica i documenti; non trasferisce requisiti rimasti solo in chat.

## Sessioni di coding e verifiche

Prima di scrivere o modificare codice, leggere per intero
`docs/guides/code-style.md`, l'overview e i documenti pertinenti al compito.
Il codice attua il design confermato. Scelte esecutive già comprese nel
perimetro non richiedono una nuova approvazione; decisioni funzionali o
architetturali nuove tornano al design prima delle modifiche dipendenti.

Per funzionalità nuove o modificate, il design produce scenari di
Behavior-Driven Development (BDD) in Gherkin; il coding implementa gli step Cucumber.
Le `.feature` sono artefatti di design scritti su `APPROVO`, nel source tree
del modulo, mai nella documentazione. I test esistenti restano validi:
l'adozione non impone una loro conversione generale.

Scrivere o estendere i test pertinenti prima del codice applicativo. Per una
correzione, dimostrare la regressione prima e il successo dopo, distinguendo
un fallimento del comportamento da problemi di compilazione o ambiente.
Portare al verde i controlli pertinenti prima della consegna. La sola
documentazione si verifica senza eseguire inutilmente build applicative.

La scelta di Cucumber e di Spotless con ktlint è adottata; la loro integrazione
è ancora da implementare. Consultare lo stato effettivo in `CONTRIBUTING.md`:
non dichiarare attivo o superato un controllo che non è stato configurato ed
eseguito. L'adozione documentale non certifica la conformità del codice storico.

Le simulazioni non certificano Bluetooth, telefono o vettura reali. Registrare
separatamente controlli automatici, verifiche dell'interfaccia e prove fisiche,
con esiti e limiti. Usare gli strumenti nativi di revisione per rendere
ispezionabili le modifiche autorizzate.

Revisioni con più agenti non partono autonomamente. Si possono proporre
numero e obiettivi; occorre la richiesta esplicita del proprietario.

## Git e chiusura

- Nessun comando Git, neppure di sola lettura, senza richiesta esplicita del
  proprietario per quel comando. Non inizializzare un repository di iniziativa.
- Commit e push sono del proprietario; la sessione non li esegue di iniziativa.
- Chiudere ogni tema con l'elenco esatto dei file creati o modificati, i
  controlli svolti, gli eventuali limiti e il testo del commit proposto secondo
  `CONTRIBUTING.md`. La proposta non costituisce un commit eseguito.
- Non includere segreti o configurazioni locali nelle consegne. La particolare
  chiave debug già distribuita con i sorgenti è trattata in `CONTRIBUTING.md`.
