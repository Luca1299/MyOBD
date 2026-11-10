# ADR-0002 — Primo collegamento alla centralina e riabilitazione dell’autoconnessione

- Stato: **ACCETTATO — approvato da Luca il 10 novembre 2026 con “Approvo”**
- Data: 10 novembre 2026
- Riferimento: ADR-0001, applicazione 0.2.0
- Ambito: lettura della prima risposta della centralina e durata del blocco dell’autoconnessione.
- Esecuzione autorizzata: fase test prima del codice applicativo; implementazione e verifiche nel perimetro approvato.

## Contesto verificato

Nei due registri condivisi, la connessione Bluetooth all’adattatore e la sua inizializzazione riescono già durante il primo collegamento manuale. Alla prima richiesta `0100` la risposta contiene:

```text
SEARCHING...\r..4100BE3EB81100\r>
```

Il parser elimina `SEARCHING...`, ma non i puntini di avanzamento prima dei dati sulla riga successiva. La risposta viene quindi rifiutata e l’interfaccia presenta la centralina come non raggiungibile. Una successiva verifica riesce sullo stesso collegamento, senza una nuova inizializzazione dell’adattatore. Il difetto osservato riguarda quindi l’interpretazione della prima risposta.

Nel codice 0.2.0 un collegamento manuale riuscito rimuove già il blocco persistente dell’autoconnessione; esiste anche un test che verifica questa rimozione. Tuttavia, il ramo che gestisce i fallimenti può impostare il blocco anche dopo un solo tentativo manuale fallito, quando prima era disattivato.

I registri iniziano con il blocco già attivo e non contengono i fallimenti che l’hanno originato. Non dimostrano da soli che la riabilitazione dopo un successo manuale sia mancata.

## Decisione approvata

### 1. Prima risposta della centralina

Riconoscere i messaggi di avanzamento della ricerca del protocollo nella risposta al singolo comando. Accettare il caso reale riportato sopra, sia quando avanzamento e dati arrivano sulla stessa riga, sia quando sono separati da ritorni a capo.

La normalizzazione deve essere limitata al contesto di ricerca riconosciuto: rimuovere soltanto i puntini iniziali di avanzamento, conservando integralmente i byte della risposta. I dati esadecimali devono poi superare le normali verifiche di formato e contenuto. Non eliminare caratteri arbitrari per far apparire valida una risposta danneggiata.

Una risposta valida al primo comando deve consentire di completare la verifica della centralina senza una seconda azione dell’utente.

Una risposta non interpretabile deve produrre un messaggio di lettura fallita, distinto da una reale assenza di risposta della centralina. Se l’adattatore rimane collegato, mantenere il collegamento e consentire la verifica della centralina già prevista dall’ADR-0001. Questo errore non consuma tentativi Bluetooth.

### 2. Blocco temporaneo dell’autoconnessione

Il blocco indica esclusivamente che un ciclo automatico ha esaurito i suoi tre tentativi. Non è una preferenza permanente dell’utente.

| Evento | Comportamento approvato |
| --- | --- |
| Avvio con dispositivo salvato, prerequisiti soddisfatti e automatismo abilitato | Avviare il ciclo automatico. |
| Primo o secondo fallimento automatico | Attendere 3 secondi dal fallimento e riprovare. |
| Terzo fallimento automatico | Salvare il blocco, terminare il ciclo e richiedere l’azione manuale. Nessun quarto tentativo. |
| Riapertura con blocco attivo | Conservare il blocco e attendere il comando manuale. |
| Pressione del comando manuale | Eseguire un solo tentativo. |
| Tentativo manuale fallito con blocco già attivo | Conservare il blocco. |
| Tentativo manuale fallito con automatismo abilitato | Conservare l’abilitazione: il singolo fallimento manuale non crea un blocco persistente. Restituire il controllo all’utente senza avviare un ciclo automatico immediato. |
| Collegamento manuale riuscito | Rimuovere subito e in modo persistente il blocco; azzerare il conteggio dei fallimenti del ciclo concluso. |
| Successiva apertura dell’app dopo il successo manuale | Consentire l’autoconnessione, se non esiste già una sessione o un tentativo in corso. |
| Successiva perdita del collegamento | Consentire un nuovo ciclo automatico con tre tentativi disponibili. Solo tre nuovi fallimenti automatici possono bloccarlo nuovamente. |

Per “collegamento riuscito” si intende collegamento Bluetooth stabilito e adattatore ELM inizializzato. La disponibilità della centralina è uno stato separato: una centralina senza risposta non deve impedire la riabilitazione dell’autoconnessione dopo il successo del collegamento all’adattatore.

La riabilitazione rende disponibili i futuri collegamenti automatici; non apre una seconda connessione mentre quella manuale è già attiva. Rimane valido il comando esplicito di interruzione della sessione previsto dall’ADR-0001.

### 3. Registro comprensibile

Registrare chiaramente:
- numero e origine del tentativo, automatico o manuale;
- attivazione del blocco dopo il terzo fallimento automatico;
- riabilitazione dell’autoconnessione dopo il successo manuale;
- esito della verifica della centralina, separato dall’esito del collegamento all’adattatore.

Questo permette di distinguere un blocco non rimosso da un nuovo blocco causato da tre fallimenti successivi.

## Verifiche richieste

Riutilizzare le coperture già presenti ed estenderle sui casi mancanti.

| ID | Risultato da verificare |
| --- | --- |
| T01 | La risposta reale con `SEARCHING...`, ritorno a capo e `..4100BE3EB81100` produce gli stessi dati della risposta senza avanzamento. |
| T02 | Le varianti di avanzamento previste sono accettate; caratteri estranei, dati troncati e puntini all’interno dei byte restano errori. |
| T03 | La prima verifica completa con la risposta reale arriva allo stato di centralina disponibile senza una seconda azione manuale e senza riaprire il Bluetooth. |
| T04 | Tre fallimenti automatici producono esattamente tre tentativi, con le attese previste; il blocco sopravvive alla ricreazione del gestore. |
| T05 | Un fallimento manuale conserva un blocco preesistente; senza blocco preesistente non ne crea uno. In entrambi i casi esegue un solo tentativo. |
| T06 | Dopo il blocco, il successo manuale rimuove il blocco persistente: ricreando il gestore, l’apertura può collegarsi automaticamente. |
| T07 | Dopo il successo manuale, una perdita del collegamento concede un nuovo ciclo completo; solo il terzo nuovo fallimento riattiva il blocco. |
| T08 | Il successo manuale dell’adattatore riabilita l’automatismo anche se la centralina non risponde; una sessione già attiva non genera tentativi duplicati. |
| T09 | Messaggi e registro distinguono blocco, riabilitazione, errore di lettura e centralina senza risposta. |

Sul dispositivo reale verificare poi il primo collegamento, il ciclo di tre fallimenti e il ripristino dopo collegamento manuale, sia alla successiva apertura sia a una successiva perdita del collegamento. I test automatici non sostituiscono questa verifica.

La fase di creazione/estensione dei test precede le modifiche al codice applicativo. La consegna richiede tutti i controlli pertinenti con esito positivo. L’approvazione autorizza entrambe le attività nel perimetro descritto.

## DA CHIEDERE — rinviato

Gli altri dubbi già emersi restano da discutere successivamente, come richiesto da Luca. Questo ADR non decide interpretazione o attribuzione dei DTC, differenze fra benzina e metano o ulteriori funzioni diagnostiche.

## Conseguenze

Il primo collegamento non richiede un secondo comando a causa del formato di avanzamento osservato. Il blocco dell’autoconnessione conserva la sua utilità dopo tre fallimenti, ma un successo manuale lo rimuove e ripristina il comportamento automatico futuro.

L’ADR-0001 rimane il riferimento per gli altri comportamenti. L’implementazione attuale non viene dichiarata corretta sul dispositivo prima delle verifiche previste.


## Attuazione 0.2.1

Implementazione completata dopo la fase dei test: prima 71 test sul codice applicativo 0.2.0, con 10 regressioni attese; poi correzioni e 71 test superati senza errori o test saltati. Compilazione APK e lint completati (0 errori, 3 avvisi non bloccanti).

Il [rapporto di verifica](../verification/adr-0002-verifica.md) e i risultati per suite documentano copertura, controlli sul pacchetto e prove fisiche residue. Il primo collegamento è stato verificato riproducendo la risposta reale; la nuova APK richiede ancora prova sul Motorola e sulla vettura.

La vecchia chiave di firma non era disponibile nei sorgenti recuperati: la APK 0.2.1 richiede una reinstallazione dopo aver esportato eventuali rapporti da conservare. La chiave debug nuova è inclusa e configurata nei sorgenti per gli aggiornamenti successivi.
