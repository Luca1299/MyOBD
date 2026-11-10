# ADR-0004 — Conservazione dei risultati nel processo

**Tipo:** architettura **Stato:** accettato **Data:** 2026-11-10

Architecture Decision Record (ADR) approvato dal proprietario il 10 novembre
2026 con «APPROVO la soluzione consigliata, i documenti e l’implementazione»,
in risposta al punto 5 della correzione di app/D-0001. Nessuna domanda aperta.

## Contesto

[ADR-0001](0001-collegamento-android-elm327.md) assegna la sessione al servizio
Android e ne richiede l’arresto con il comando esplicito. Il package
[app/D-0001](../design/app/D-0001-diagnosi-obd-sola-lettura/README.md) conserva
in memoria la diagnosi dopo Scollega, senza precisarne il proprietario quando
Android distrugge il servizio dopo il distacco dell’interfaccia.

Tenere il risultato solo dentro il servizio lo fa perdere passando a un’altra
app dopo lo scollegamento, anche se il processo esiste ancora. Tenere vivo il
servizio solo per conservare dati conclusi contrasterebbe con il suo arresto.

## Decisione

La sessione attiva, il trasporto e le operazioni restano posseduti dal servizio.
Un contenitore nella memoria del processo conserva solo l’ultimo risultato
concluso reale e l’ultimo concluso demo, separatamente. Non conserva coroutine,
collegamenti, stato di riconnessione o proprietari della sessione attiva.

Scollega continua a chiudere il collegamento, fermare servizio e notifica e
concludere l’eventuale diagnosi come interrotta, conservando i dati ricevuti.
Il risultato concluso resta consultabile anche dopo cambio app, ricreazione
dell’interfaccia o distruzione e ricreazione del servizio nello stesso processo.
La nuova sessione ripresenta il risultato della propria modalità; la proprietà
delle acquisizioni attive non viene trasferita all’interfaccia o al contenitore.

Una nuova acquisizione parte con sezioni vuote. Alla conclusione sostituisce
il risultato della stessa modalità. Identificativi e pubblicazioni impediscono
che un risultato vecchio sostituisca un tentativo successivo; il risultato demo
non sovrascrive quello reale. Il ripristino dei soli dati non avvia diagnosi
né collegamenti: i comandi di collegamento mantengono la politica precedente.

La terminazione del processo elimina entrambi i risultati. Non si scrive uno
storico su disco; per conservare oltre tale limite si esporta il rapporto di
testo semplice (TXT). Memoria e registro rimangono limitati ai due risultati
e ai limiti già previsti per ciascun registro.

Questa decisione precisa la durata dei risultati, mantenendo l’arresto del
servizio e le altre decisioni di ADR-0001. Il documento storico non è riscritto;
non si dichiara una prima installazione sul telefono non verificata.

## Alternative e compromessi

| Alternativa | Esito |
| --- | --- |
| Risultati solo nel servizio | Non soddisfa la durata ora richiesta dopo Scollega e cambio app. |
| Servizio mantenuto in vita per i risultati | Scartata: trattiene il proprietario operativo dopo il comando di arresto. |
| Ultimi risultati nella memoria del processo | Accettata: conserva la consultazione senza connessioni o persistenza aggiuntive. |
| Storico persistente su disco | Fuori perimetro: richiede un design distinto. |

La memoria del processo non è una garanzia di conservazione permanente.
Servono prove separate per i cicli di vita Android e per la vettura reale.
La decisione va riesaminata se vengono richiesti storico, recupero dopo
terminazione del processo o confronto fra più diagnosi.
