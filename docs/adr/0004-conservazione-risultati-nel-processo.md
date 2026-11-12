# ADR-0004 — Conservazione dei risultati nel processo

**Tipo:** architettura **Stato:** accettato **Data:** 2026-11-10

**Revisione:** 2026-11-12 — estensione della conservazione alle osservazioni
concluse dei parametri motore, autorizzata mediante `APPROVO` del proprietario
prima della prima installazione delle versioni che attuano la decisione.

Architecture Decision Record (ADR) approvato originariamente il 10 novembre
2026 con «APPROVO la soluzione consigliata, i documenti e l’implementazione»,
per la conservazione delle diagnosi concluse.

Il proprietario ha successivamente dichiarato di non avere installato sul
Motorola né la versione 0.3.1 né la 0.3.2. La revisione segue quindi il ciclo
precedente alla prima installazione. Non dichiara implementata l’estensione
né autorizza il coding di specifiche non confermate.

## Contesto

[ADR-0001](0001-collegamento-android-elm327.md) assegna la sessione al servizio
Android e ne richiede l’arresto con il comando esplicito.

La conservazione iniziale riguarda la diagnosi conclusa dopo Scollega. Tenere
il risultato solo nel servizio lo fa perdere quando il servizio viene
distrutto, anche se il processo esiste ancora. Tenere vivo il servizio soltanto
per i dati contrasta con il suo arresto.

L’osservazione continua dei parametri richiede lo stesso comportamento per i
propri ultimi valori conclusi. Diagnosi e osservazione sono risultati diversi
e non devono sostituirsi reciprocamente.

## Decisione

La sessione attiva, il trasporto e le operazioni restano posseduti dal servizio.

Il livello Application conserva nella memoria del processo soltanto:

- ultima diagnosi conclusa reale;
- ultima diagnosi conclusa demo;
- ultima osservazione conclusa reale;
- ultima osservazione conclusa demo.

La conservazione non comprende coroutine, collegamenti, stato di riconnessione
o proprietà delle operazioni attive.

Dell’osservazione si conservano contesto e fotografia finale degli ultimi
valori, tempi ed esiti. Non si conserva la sequenza dei campioni.

Scollega continua a chiudere collegamento, servizio e notifica e a interrompere
l’operazione eventualmente attiva, conservando i dati ricevuti secondo la
specifica della capacità.

I risultati conclusi restano consultabili dopo cambio app, ricreazione
dell’interfaccia e distruzione e ricreazione del servizio nello stesso processo.

Una nuova acquisizione ammessa presenta dati inizialmente vuoti e, alla
conclusione, sostituisce soltanto il risultato della stessa capacità e
modalità. Un avvio rifiutato non sostituisce il risultato precedente.

Identificativi e pubblicazioni impediscono che un risultato tardivo sostituisca
un tentativo successivo. Dati demo e reali restano separati.

Il ripristino dei risultati non avvia acquisizioni. I comandi di collegamento
mantengono le politiche esistenti.

La terminazione del processo elimina i quattro risultati. Non si introduce
uno storico su disco. La conservazione oltre il processo richiede
un’esportazione esplicita in testo semplice (TXT).

La memoria rimane limitata agli ultimi risultati indicati e ai limiti già
previsti per i registri. Il servizio non viene mantenuto attivo soltanto per
conservare risultati conclusi.

## Alternative e compromessi

| Alternativa | Esito |
| --- | --- |
| Risultati solo nel servizio | Non soddisfa la consultazione dopo distruzione del servizio nello stesso processo. |
| Servizio mantenuto in vita per i risultati | Scartata: trattiene il proprietario operativo dopo Scollega. |
| Un solo risultato condiviso tra diagnosi e osservazione | Scartata: una capacità cancellerebbe il risultato dell’altra. |
| Ultimo risultato per capacità e modalità nella memoria del processo | Accettata: conserva consultazione e separazione senza trasferire operazioni. |
| Sequenza dei campioni o storico persistente | Fuori perimetro: richiede un design distinto. |

La memoria del processo non garantisce conservazione permanente. Servono
verifiche separate dei cicli di vita Android e dell’hardware reale.

La decisione va riesaminata se vengono richiesti storico, recupero dopo
terminazione del processo, sequenze di campioni o confronto fra acquisizioni.
