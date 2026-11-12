# D-0002 — Parametri motore in tempo reale

**Stato:** in design **Area:** app **Data:** 2026-11-12

**Consolidamento:** parziale, con il punto 15 sospeso in attesa delle misure
hardware. Nessuna conferma per il coding.

Consolidamento parziale autorizzato dal proprietario con `APPROVO` il
12 novembre 2026, lasciando esplicitamente sospeso il punto 15 e il package
in design.

OBD significa On-Board Diagnostics; PID Parameter Identifier; ADR Architecture
Decision Record; BDD Behavior-Driven Development. TXT indica testo semplice.

**ADR correlati:** [ADR-0001](../../../adr/0001-collegamento-android-elm327.md),
[ADR-0002](../../../adr/0002-primo-collegamento-e-riabilitazione-automatica.md),
[ADR-0003](../../../adr/0003-metodo-di-sviluppo-e-verifiche.md),
[ADR-0004](../../../adr/0004-conservazione-risultati-nel-processo.md).

**Scenari di design:**

- [Sessione](../../../../app/src/test/resources/features/parameters/live-parameters-session.feature).
- [Valori e letture](../../../../app/src/test/resources/features/parameters/live-parameters-values.feature).
- [Esportazione](../../../../app/src/test/resources/features/parameters/live-parameters-report.feature).

Le feature sono artefatti di design. Questo consolidamento non aggiunge runner
o step Cucumber e non le dichiara eseguite.

## Scopo e perimetro

Osservare gli ultimi valori dei parametri motore durante un’acquisizione
avviata manualmente, con selezione dei parametri, aggiornamento continuo e
comandi Avvia, Pausa e Termina.

L’osservazione sostituisce la lettura singola nella scheda Parametri. Il
campione associato alla diagnosi manuale rimane distinto e continua a seguire
[app/D-0001](../D-0001-diagnosi-obd-sola-lettura/README.md).

Si riusano il servizio proprietario della sessione, il coordinamento delle
operazioni, la serializzazione delle richieste, i lettori, i decoder, il
collegamento e la riconnessione esistenti. Non si introducono connessioni
parallele o nuovi proprietari delle operazioni.

Restano fuori grafici, cruscotto con strumenti, registrazione continua su
disco, storico, statistiche, nuovi sensori, diagnostica specifica metano,
cancellazione errori, attuazioni e codifiche.

## Decisioni del tema

I numeri restano quelli della discussione.

| Punto | Stato | Decisione o informazione |
| --- | --- | --- |
| 1 | Risolto | Nessuna nuova prova hardware disponibile. |
| 2 | Risolto | L’osservazione continua sostituisce la lettura singola nella scheda Parametri. |
| 3 | Risolto | Cadenza nominale unica, senza selettore di velocità. |
| 4 | Risolto | L’acquisizione reale continua durante navigazione, passaggio ad altra app e schermo spento; dopo una perdita serve un nuovo avvio manuale. |
| 5 | Risolto | La pausa mantiene riservata l’operazione; per avviare una diagnosi occorre Termina. |
| 6 | Risolto | Selezione e alimentazione restano fisse fino alla conclusione, anche in pausa. |
| 7 | Risolto | Ultimi risultati conservati nello stesso processo anche dopo Scollega, con reale e demo separati. |
| 8 | Risolto | Alla prima preparazione sono selezionati tutti i parametri dichiarati disponibili. |
| 9 | Risolto | Un errore di contenuto non elimina il parametro: viene ritentato nel giro successivo. |
| 10 | Risolto | La demo va automaticamente in pausa quando l’app non è visibile; ripresa manuale. |
| 11 | Risolto | Il periodo nominale riguarda il giro completo; niente giri arretrati da recuperare. |
| 12 | Risolto | Età effettiva sempre visibile; nessuna soglia arbitraria di freschezza. |
| 13 | Risolto | L’esportazione del Registro comprende la fotografia dell’ultima osservazione conclusa. |
| 14 | Risolto | Il proprietario non ha installato la 0.3.1 né la 0.3.2 sul Motorola. |
| 15 | Sospeso | Periodo nominale numerico da stabilire mediante misure hardware. |
| 16 | Risolto | La notifica mantiene un unico comando Scollega e mostra anche lo stato dell’osservazione. |
| 17 | Risolto | La demo mantiene i valori costanti esistenti. |

## Parametri e disponibilità

Il catalogo resta quello esistente.

| Parametro | Unità |
| --- | --- |
| Giri motore | giri/min |
| Temperatura refrigerante | °C |
| Velocità | km/h |
| Correzione carburazione breve, banco 1 | % |
| Correzione carburazione lunga, banco 1 | % |
| Pressione assoluta nel collettore | kPa |
| Posizione farfalla | % |

Si interrogano soltanto i parametri selezionati e dichiarati disponibili dalla
mappa del collegamento corrente, nell’ordine stabile del catalogo.

La mappa non viene riletta ad ogni giro. La preparazione e la verifica del
collegamento continuano a seguire il percorso esistente.

Un parametro non dichiarato disponibile rimane riconoscibile nell’elenco,
con selezione disabilitata e motivo esplicito. Una mappa ancora sconosciuta
non equivale a una mappa valida senza parametri disponibili.

Durante il ciclo non vengono aggiunte letture periodiche della spia, dei
codici o di altre sezioni diagnostiche.

## Avvio e contesto

L’avvio è soltanto manuale e richiede:

- centralina verificata sul collegamento corrente;
- almeno un parametro selezionato e dichiarato disponibile;
- nessun’altra operazione diagnostica attiva.

All’ammissione vengono fissati identificativo dell’osservazione, modalità
reale o demo, alimentazione annotata, selezione, adattatore, protocollo e
momento di inizio.

L’alimentazione ammette Benzina, Metano e Non annotata. Non viene dedotta dai
parametri generici e non identifica la centralina che ha risposto.

Selezione, alimentazione e modalità non cambiano durante l’osservazione,
compresa la pausa.

Pressioni ripetute non accodano osservazioni. I prerequisiti e l’identità del
collegamento vengono rivalutati all’ammissione. Un avvio rifiutato non
sostituisce il risultato precedente e non viene trasferito a un collegamento
successivo.

Una nuova osservazione ammessa presenta valori inizialmente vuoti; non usa
dati precedenti per riempire parametri non ancora letti.

## Giri, cadenza e tempi effettivi

Un giro comprende una richiesta per ogni parametro selezionato e disponibile.
Le richieste sono sequenziali. Ogni risposta valida aggiorna subito il
relativo parametro.

Il primo giro parte immediatamente dopo l’ammissione.

Il periodo nominale decorre dall’inizio del giro corrente:

- se il giro termina prima della scadenza, si attende il tempo restante;
- se termina alla scadenza o dopo, il successivo può iniziare alla conclusione;
- non si accumulano giri arretrati e non si inviano richieste aggiuntive per
  recuperare il ritardo.

Il valore numerico del periodo resta sospeso al punto 15. Gli scenari
temporali usano un periodo controllato di prova, che non costituisce una
frequenza scelta per il prodotto.

Restano distinti periodo nominale, durata effettiva dell’ultimo giro ed età
dell’ultima risposta valida di ciascun parametro. Non si calcolano medie o
altre statistiche.

I tempi di lettura indicano la ricezione nell’app, non un istante di misura
fisica certificato dalla centralina. I valori del giro non costituiscono una
fotografia simultanea della vettura.

Il limite attuale della singola richiesta ordinaria è cinque secondi. È un
timeout, non una frequenza sostenibile dimostrata. Questo incremento non lo
modifica.

## Pausa, ripresa e conclusione

Pausa impedisce nuove richieste dopo quella già in corso. Se non esiste una
richiesta pendente, ha effetto subito. Altrimenti mostra Pausa in corso… e
attende la conclusione entro il timeout esistente.

Una risposta valida della richiesta pendente può essere conservata. La pausa
mantiene riservata l’operazione e non abilita diagnosi o altre letture manuali.

Avvia, dallo stato In pausa, riprende la stessa osservazione iniziando un nuovo
giro. Contesto e valori già acquisiti rimangono gli stessi finché vengono
riletti.

Termina segue lo stesso criterio di attesa della richiesta pendente, senza
inviarne altre. Da una pausa completata conclude immediatamente. Prevale su
una precedente richiesta di pausa e non può terminare tornando allo stato
In pausa.

La conclusione volontaria mantiene il collegamento se questo resta
utilizzabile.

Scollega chiude invece la sessione tramite il percorso esistente, interrompe
l’osservazione e arresta servizio e notifica. Rimane disponibile anche durante
le attese di pausa o terminazione.

Timeout, cancellazione del trasporto e perdita del collegamento conservano
gli effetti del percorso esistente: non viene promesso il mantenimento del
collegamento dopo tali eventi.

## Valori ed errori

Per ciascun parametro si conservano separatamente:

- ultimo valore valido, con unità e momento di ricezione;
- esito e momento dell’ultimo tentativo;
- problema disponibile quando il tentativo non produce un valore valido.

| Situazione | Presentazione |
| --- | --- |
| Nessun tentativo concluso | In attesa della prima lettura, senza numero. |
| Risposta valida | Valore, unità, ora ed età dell’ultima lettura valida. |
| Tentativo fallito con valore precedente | Valore precedente conservato, età e motivo del mancato aggiornamento. |
| Tentativo fallito senza valore precedente | Nessun dato ricevuto oppure Risposta non valida, con motivo disponibile. |
| Pausa o conclusione | Stato esplicito; tempi e valori non vengono aggiornati artificialmente. |

Zero resta un valore valido. Anche una risposta numericamente identica alla
precedente costituisce una nuova lettura e aggiorna il proprio tempo.

Un errore di contenuto non cancella l’ultimo valore valido, non aggiorna il
suo momento di ricezione e non rende il parametro non supportato. Le altre
richieste del giro proseguono; il parametro viene ritentato nel giro
successivo, senza tentativi aggiuntivi nello stesso giro.

Se tutti i tentativi falliscono ma il trasporto rimane utilizzabile,
l’osservazione continua mostrando Nessuna lettura valida nell’ultimo giro.

Timeout o perdita del trasporto interrompono l’osservazione e seguono la
gestione del collegamento esistente.

La validità segue i decoder esistenti: non si introducono nuovi filtri di
plausibilità, sostituzioni o interpolazioni.

## Concorrenza, collegamento e ciclo di vita

Una sola operazione possiede l’accesso diagnostico. La riserva copre
acquisizione, attese tra giri, pausa richiesta, pausa completata e terminazione
richiesta.

Durante l’osservazione diagnosi manuale, lettura singola e verifica manuale
della centralina non vengono avviate né accodate. La verifica del collegamento
durante una riconnessione resta responsabilità del coordinatore esistente.

Consultare risultati già conclusi non avvia letture.

L’acquisizione reale continua cambiando scheda, ricreando l’interfaccia,
passando a un’altra app e spegnendo lo schermo, senza duplicare collegamenti
o cicli.

La perdita del collegamento interrompe anche un’osservazione in pausa.
I risultati ricevuti restano disponibili con il motivo dell’interruzione.

La riconnessione non riprende l’osservazione. Dopo il ripristino serve Avvia
per una nuova acquisizione, con nuova verifica dei prerequisiti e della
disponibilità.

Risposte e pubblicazioni tardive non aggiornano osservazioni successive o
risultati di un’altra modalità.

La demo:

- usa lo stesso proprietario della sessione senza accesso al dispositivo reale;
- conserva i valori costanti del simulatore esistente;
- aggiorna i tempi ad ogni nuova risposta;
- richiede automaticamente la pausa quando l’app non è visibile, compreso
  lo schermo spento;
- non va in pausa per il solo cambio di scheda o per la ricreazione
  dell’interfaccia;
- non riprende automaticamente al ritorno;
- non introduce servizio in primo piano o wake lock della sessione reale.

Il ripristino di un risultato conservato non ricrea un’operazione attiva.
La demo non acquisisce le garanzie di continuità della sessione reale per
effetto della sola conservazione dei dati.

## Conservazione ed esportazione

Secondo ADR-0004 si conservano separatamente, nella memoria del processo:

- ultima diagnosi conclusa reale;
- ultima diagnosi conclusa demo;
- ultima osservazione conclusa reale;
- ultima osservazione conclusa demo.

Dell’osservazione si conservano contesto, ultimi valori, relativi tempi ed
esiti e motivo della conclusione. Non si conserva la sequenza dei campioni.

Una nuova osservazione sostituisce il risultato della stessa capacità e
modalità alla conclusione, anche se interrotta. Un avvio non ammesso lascia
invariato il risultato precedente.

Dopo Termina o interruzione, il risultato rimane consultabile anche dopo
Scollega e ricreazione del servizio nello stesso processo. La terminazione
del processo elimina i risultati; non viene introdotto uno storico su disco.

L’esportazione esistente del Registro comprende la fotografia dell’ultima
osservazione conclusa, distinta dal contesto preparato per letture successive
e dalla diagnosi conclusa.

Riporta modalità, alimentazione annotata, selezione, adattatore e protocollo
disponibili, inizio e fine, motivo della conclusione, durata disponibile
dell’ultimo giro, ultimi valori con unità e tempi ed esiti degli ultimi
tentativi. I parametri non acquisiti restano riconoscibili senza numeri
inventati.

L’esportazione non esegue letture. Non presenta l’osservazione corrente come
conclusa durante acquisizione, pausa o attese di arresto.

Il testo preparato per l’esportazione rimane una fotografia stabile durante
il selettore documenti.

Il Registro conserva i limiti esistenti di 400 voci, di cui 120 mostrate.
Non viene garantita la presenza di tutti gli scambi di una lunga osservazione.
Il testo demo rimane esplicitamente simulato.

## Interfaccia

La scheda mantiene il nome Parametri. Ordine dei contenuti:

1. Titolo Parametri motore e modalità.
2. Preparazione: alimentazione e selezione.
3. Comandi e stato.
4. Contesto dell’osservazione visualizzata.
5. Durata dell’ultimo giro e valori.

Alla prima preparazione sono selezionati tutti i parametri dichiarati
disponibili. Durante un’osservazione i controlli di preparazione sono
disabilitati. Dopo la conclusione possono preparare un nuovo avvio senza
modificare il contesto dei risultati conservati.

| Stato | Azioni e riscontro |
| --- | --- |
| Non pronta | Avvia disabilitato e motivo del prerequisito mancante. |
| Pronta | Preparazione modificabile; Avvia disponibile con selezione valida. |
| In acquisizione | Pausa e Termina; parametro interrogato riconoscibile. |
| Pausa richiesta | Pausa in corso…; nessuna nuova lettura. |
| In pausa | Avvia riprende; Termina conclude; nessun indicatore di acquisizione attiva. |
| Terminazione richiesta | Terminazione in corso…; nessuna nuova lettura. |
| Terminata | Risultati consultabili e preparazione di un nuovo avvio. |
| Interrotta | Motivo e risultati disponibili; nuovo avvio manuale dopo ripristino dei prerequisiti. |

Testi necessari, oltre alle etichette dei parametri e ai motivi già disponibili:

- Parametri motore
- DEMO · DATI SIMULATI
- Seleziona almeno un parametro per avviare.
- Non dichiarato disponibile
- Nessuno dei parametri gestiti è dichiarato disponibile.
- Diagnosi in corso. Attendi la conclusione o interrompila dalla scheda Diagnosi.
- In acquisizione
- Lettura: {parametro}
- Pausa in corso…
- In pausa. Avvia riprende la stessa osservazione.
- Demo in pausa: app non visibile.
- Terminazione in corso…
- Osservazione terminata. Collegamento mantenuto.
- Osservazione interrotta: {motivo}
- Collegamento ripristinato. Avvia una nuova osservazione.
- In attesa della prima lettura
- Ultima lettura valida: {ora} · {età} s fa
- Valore precedente
- Nessun dato ricevuto
- Risposta non valida
- Nessuna lettura valida nell’ultimo giro
- Ultimo giro: {durata}
- Termina l’osservazione dalla scheda Parametri per avviare una diagnosi.
- I valori sono letti in sequenza e possono riferirsi a momenti diversi.
- Risultati conservati fino alla terminazione del processo. Esporta dal Registro
  per conservarli.

La notifica reale mostra collegamento e stato dell’osservazione. Mantiene un
unico comando Scollega, che chiude l’intera sessione; Avvia, Pausa e Termina
restano nella scheda Parametri.

Riusare tema, InfoCard, pulsanti e controlli Material e selettore
dell’alimentazione già adottati. La selezione dei parametri usa controlli
Material accessibili. Non si introducono tecnologie alternative.

I nuovi testi usano risorse Android. Colori e icone non sostituiscono le
etichette. Verificare caratteri al 200%, scorrimento, azioni raggiungibili,
nomi accessibili e comprensibilità dei controlli disabilitati.

Non esiste un prototipo visivo vincolante.

## Criteri di accettazione

| Criterio | Scenari |
| --- | --- |
| Avvio manuale, disponibilità e selezione rispettati. | LIVE-S01–LIVE-S03, LIVE-V01 |
| Un solo proprietario e contesto immutabile. | LIVE-S09, LIVE-S16 |
| Giri, pausa, ripresa e terminazione rispettano richieste e attese. | LIVE-S04–LIVE-S08 |
| Valori, tempi, assenze ed errori rimangono distinti. | LIVE-V02–LIVE-V08 |
| Ciclo di vita, perdita e risposte tardive non duplicano o mescolano acquisizioni. | LIVE-S10–LIVE-S15, LIVE-S17 |
| Conservazione limitata e separata per capacità e modalità. | LIVE-S18–LIVE-S20 |
| Demo identificata e isolata. | LIVE-S11–LIVE-S12, LIVE-V09 |
| Esportazione coerente e senza nuove letture. | LIVE-T01–LIVE-T05 |

Gli step dovranno attraversare DiagnosticsSession e il percorso effettivo dei
lettori, del parser e del coordinatore, con tempo e trasporto controllati.
I test tecnici di concorrenza e integrazione Android affiancano gli scenari
senza reimplementare le politiche nelle fixture.

Le verifiche Compose useranno la schermata effettiva. Resa visiva e selettore
documenti richiedono verifiche separate dagli scenari di dominio.

## Evidenze e prove hardware

La base documentata è la versione 0.3.2. Il relativo rapporto riporta 157 test
superati e nessuna prova fisica eseguita. Questo design non ripresenta quel
conteggio come risultato corrente.

Sono disponibili evidenze storiche di risposte reali, ma non misure sufficienti
a scegliere il periodo nominale. Il proprietario non ha ancora installato la
0.3.1 né la 0.3.2 sul Motorola.

Le prove devono registrare versione dell’app, telefono e sistema effettivi,
adattatore e protocollo, parametri disponibili e selezionati, condizioni di
quadro e motore e alimentazione annotata.

Misurare separatamente:

- durata delle singole richieste;
- durata dei giri;
- intervallo effettivo tra risposte valide dello stesso parametro;
- risposte lente, errori e timeout.

Ripetere le osservazioni con uno, tre e sette parametri quando effettivamente
disponibili. Non sostituire parametri mancanti per dichiarare provata una
configurazione non eseguibile.

Le letture manuali già disponibili possono fornire prime misure per richiesta;
non certificano da sole il futuro ciclo continuo. La cadenza scelta dovrà
essere successivamente validata sull’incremento che la implementa.

Verificare inoltre sul Motorola con NK Mini e vettura: continuità, schermo
spento, notifica, comandi, perdita e recupero, mancata ripresa automatica,
leggibilità ed esportazione. La demo non certifica Bluetooth o centraline reali.

I risultati futuri saranno registrati in docs/verification/, distinguendo
controlli automatici, verifiche dell’interfaccia e prove fisiche.

## Punto aperto e chiusura

15. [DA CHIEDERE: quale periodo nominale adottare sulla base delle misure hardware?]

Il punto resta sospeso per decisione del proprietario. Il consolidamento
parziale comprende le altre decisioni e gli scenari del contratto temporale,
senza assegnare un periodo numerico.

Prima del coding occorrono la risoluzione del punto 15, il consolidamento della
scelta, la conferma separata della specifica e l’incarico di implementazione.
