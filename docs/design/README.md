# Design package — Specifiche vive

I design package descrivono capacità, comportamenti e criteri di accettazione.
Per l'app descrivono il funzionale e, quando presente, il design dell'interfaccia
utente. L'area `progetto` ammette le capacità del processo di sviluppo, come
l'esecuzione delle verifiche. Non sono documentazione del codice o verbali:
l'architettura vive nell'overview, le motivazioni delle scelte negli
Architecture Decision Record (ADR), gli esiti nei rapporti di verifica.

## Struttura e identità

Percorso: `docs/design/<area>/D-NNNN-<slug>/README.md`. Aggiungere directory per
mockup o diagrammi solo quando esistono artefatti reali da conservare.

Le aree coincidono con gli ambiti di `CONTRIBUTING.md`: `app`, `obd-core`,
`progetto`. La numerazione `D-NNNN` è cronologica e immutabile per area;
non ricompattare buchi o riutilizzare numeri. Il riferimento canonico è
`<area>/D-NNNN`. Una capacità che evolve mantiene lo stesso package; uno nuovo
nasce per una capacità diversa.

## Stati e autorizzazioni

| Stato | Significato |
| --- | --- |
| `in design` | Specifica in preparazione; non abilita il coding. |
| `confermato` | Specifica resa autorevole dal proprietario per il coding. |
| `ritirato` | Capacità rimossa o sostituita; indicare il successore quando esiste. |

`APPROVO` autorizza la scrittura del contenuto concordato; non cambia da solo
lo stato. La conferma è un atto separato del proprietario e deve identificare
il package e il perimetro che è pronto. Lo stato non viene promosso di
iniziativa dalla sessione. Il proprietario può modificarlo personalmente o
ordinare esplicitamente la registrazione della propria conferma.

Una specifica `in design` può avere decisioni già accettate nei suoi ADR e
scenari già scritti, senza autorizzare l'implementazione. Il coding richiede
la specifica confermata e il relativo incarico. Non esiste lo stato
`implementato`: il package resta la specifica corrente; esecuzione e risultati
sono documentati dalle consegne e dai rapporti, senza duplicare il package.

Le modifiche di contenuto richiedono `APPROVO`. Un incremento funzionale non
ancora confermato va distinto dal perimetro confermato esistente, con revisione
e confini espliciti; se il documento non permette di distinguerli, riportarlo
a `in design` su decisione del proprietario. Il coding non usa parti in sospeso.

## Intestazione

```markdown
# D-NNNN — Titolo

**Stato:** in design **Area:** app **Data:** AAAA-MM-GG
**ADR correlati:** ADR-NNNN
**Scenari:** percorsi delle feature quando esistono
```

## Contenuto e collegamenti

- Scopo, perimetro e ciò che resta fuori dall'incremento.
- Comportamenti, regole, prerequisiti ed esiti attesi, inclusi errori e recupero.
- Criteri verificabili e collegamenti agli scenari pertinenti.
- Decisioni d'interfaccia quando la capacità comprende schermate.
- Eventuali domande numerate secondo `../../AGENTS.md`, senza rinumerazioni.

Il collegamento è design → ADR: un package cita le decisioni su cui poggia;
non si edita un vecchio ADR per aggiungere il rimando inverso.

Gli scenari di Behavior-Driven Development (BDD) sono `.feature` in Gherkin
nel source tree del modulo, `src/test/resources/features/`, mai in questa
directory. La sessione di design li scrive su `APPROVO`; il coding aggiunge
gli step Cucumber e li porta al verde. Le regole sono in
[code-style.md](../guides/code-style.md). La collocazione nel source tree non
li rende automaticamente eseguibili senza runner e step.

## Design dell'interfaccia

Prima del coding definire schermate, stati, azioni, testi necessari,
prerequisiti, errori, accessibilità e ritorno al compito. Identificare i
componenti Compose/Material già disponibili, quelli da riusare nel progetto
e le eventuali nuove responsabilità, senza duplicare meccanismi esistenti.

Se si usa un prototipo, fissarne una revisione riconoscibile e gli stati di
riferimento. Comportamenti e perimetro fanno fede nel package; le divergenze
vanno risolte prima del coding. La verifica del comportamento e quella della
resa sul dispositivo sono distinte. Un package del processo di sviluppo privo
di schermate dichiara che non introduce un'interfaccia nell'app.

## Indice per area

Un package fuori indice non è pubblicato. Aggiornare l'indice alla creazione
e quando cambia lo stato o il perimetro autorevole.

### Progetto

- [progetto/D-0001 — Verifiche automatiche e formattazione](progetto/D-0001-verifiche-automatiche/README.md)
  — `in design`, consolidato su `APPROVO` il 10 novembre 2026; integrazione di
  Cucumber con scenario pilota e controllo Spotless/ktlint. Conferma per il
  coding ancora da ricevere; nessun runner, step o formatter configurato.

### App

- [app/D-0001 — Diagnosi OBD motore in sola lettura](app/D-0001-diagnosi-obd-sola-lettura/README.md)
  — `in design`, consolidato su `APPROVO` il 10 novembre 2026: avvio manuale,
  codici con descrizioni verificate, readiness, freeze frame, campione attuale,
  esiti distinti ed esportazione TXT in sessione; scenari di design nel nucleo
  e nell'app. Conferma per il coding ancora da ricevere; nessuna implementazione
  o validazione hardware dichiarata.

### Nucleo diagnostico

Non sono ancora presenti package autonomi per l'area `obd-core`; gli scenari
di protocollo della diagnosi sono collegati da app/D-0001, proprietario della
capacità trasversale. Le specifiche storiche di ADR-0001 e ADR-0002 restano
nei documenti originali, senza migrazione retroattiva dei requisiti.
