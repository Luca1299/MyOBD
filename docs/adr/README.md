# Architecture Decision Record — Convenzioni e indice

Gli Architecture Decision Record (ADR) registrano decisioni durevoli:
contesto, scelta, alternative scartate, conseguenze e compromessi. I requisiti
dettagliati appartengono alle specifiche funzionali, gli esiti delle prove ai
rapporti di verifica. Una correzione ordinaria non richiede un ADR se non
introduce una decisione durevole da motivare.

## Identità e intestazione

Numerazione cronologica e immutabile `NNNN`, senza riordini tematici o riuso
dei numeri. Il file si chiama `NNNN-<slug-con-trattini>.md`; il riferimento è
`ADR-NNNN`. L'indice è la vista tematica: aggiornarlo alla nascita di ogni ADR.
Un ADR fuori indice non è pubblicato come riferimento del progetto.

Intestazione canonica per i nuovi documenti:

```markdown
# ADR-NNNN — Titolo

**Tipo:** metodo **Stato:** bozza **Data:** AAAA-MM-GG
```

Vocabolario dei tipi: `architettura`, `tecnologia`, `prodotto`, `metodo`.
Stati: `bozza`, `accettato`, `superato`. Quando necessario, aggiungere la data
della revisione e il riferimento esplicito alla decisione superata o successiva.
L'approvazione e la sua data devono risultare verificabili.

## Ciclo di vita

Prima della prima installazione sul telefono di una versione che attua la
decisione, l'ADR può essere rivisto direttamente su `APPROVO`. Dichiarare la
revisione nell'intestazione e nell'indice; la storia delle versioni, quando
presente, è conservata da Git. Un nuovo ADR che ne supera un altro prima
dell'installazione è l'eccezione: serve quando la nuova scelta ha alternative
e compromessi propri da conservare separatamente.

Dalla prima installazione l'ADR è immutabile. Un cambiamento produce un nuovo
ADR che cita quello superato. Stato effettivo di superamento e successore si
registrano nell'indice, senza modificare nemmeno l'intestazione storica del
vecchio documento.
La prima installazione va registrata con versione e data quando nota;
consegna del pacchetto e test automatici non la dimostrano. Non dedurre o
retrodatare installazioni mancanti. Nel dubbio conservare il testo esistente
e discutere il cambiamento prima di riscriverlo.

La specifica cita gli ADR su cui poggia; non si modifica un ADR per aggiungere
collegamenti a package nati dopo. Il numero di un ADR non qualificato si
riferisce a questo progetto. I riferimenti ad altri repository portano sempre
il nome del progetto, per esempio `Naedo ADR-0009`.

## Contenuto minimo

- Contesto, vincoli e problema da risolvere.
- Decisione esplicita e perimetro.
- Alternative considerate e motivi della scelta.
- Conseguenze, costi e compromessi accettati.
- Condizioni che renderebbero necessario riesaminare la decisione, quando utili.
- Eventuali dubbi nella forma numerata stabilita in `../../AGENTS.md`.

La stessa sessione che approva una variazione architetturale aggiorna
[l'overview](../architecture/overview.md) per rappresentarne lo stato risultante.

## Indice per perimetro

### Collegamento e diagnostica

- [ADR-0001 — Collegamento Android all'adattatore ELM327](0001-collegamento-android-elm327.md)
  — accettato il 9 novembre 2026; documento storico della versione 0.2.0.
  ADR-0004 precisa la durata dei risultati conclusi senza cambiare l’arresto del servizio.
- [ADR-0002 — Primo collegamento alla centralina e riabilitazione dell'autoconnessione](0002-primo-collegamento-e-riabilitazione-automatica.md)
  — accettato il 10 novembre 2026; documento storico della versione 0.2.1.

- [ADR-0004 — Conservazione dei risultati nel processo](0004-conservazione-risultati-nel-processo.md)
  — `architettura`, accettato il 10 novembre 2026; revisione precedente
  alla prima installazione, approvata il 12 novembre 2026 per estendere
  la conservazione all’ultima osservazione conclusa reale e demo,
  separatamente dalle diagnosi concluse. Estensione progettata,
  non ancora implementata.

I primi due documenti precedono queste convenzioni e mantengono testo,
intestazioni e collegamenti originali. I loro requisiti e rapporti restano
consultabili; la nuova organizzazione non riscrive le decisioni storiche,
non certifica ulteriori prove fisiche e non ne cambia gli identificativi.
Le evoluzioni future seguono il metodo di ADR-0003.

### Metodo e qualità

- [ADR-0003 — Metodo di sviluppo e verifiche](0003-metodo-di-sviluppo-e-verifiche.md)
  — `metodo`, accettato il 10 novembre 2026: adozione delle regole, doppio
  passaggio approvazione/conferma, ciclo degli ADR, Cucumber e formattazione;
  lo stato effettivo degli strumenti è in
  [CONTRIBUTING.md](../../CONTRIBUTING.md#controlli-attualmente-disponibili).
