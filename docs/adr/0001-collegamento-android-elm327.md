# ADR-0001 — Collegamento Android all'adattatore ELM327

| Campo | Valore |
| --- | --- |
| Progetto | Multipla OBD |
| Data | 9 novembre 2026 |
| Stato | **Accettato — implementazione autorizzata** |
| Approvazione dell'ADR | Comando esplicito `APPROVO` ricevuto dall'utente il 9 novembre 2026 |
| Implementazione | Versione 0.2.0; verifiche automatiche completate, prova su hardware reale ancora da eseguire |
| Ambito | Connessione, riconnessione e continuità della sessione Bluetooth |

## Contesto

L'app è nativa Android, in Kotlin. Il dispositivo destinatario dichiarato
dall'utente è un Motorola Edge 50 Ultra con Android 17. Il veicolo è una Fiat
Multipla del 2005, benzina/metano, con impianto originale.

L'adattatore acquistato è un NK Mini ELM327 Bluetooth 2.0, visualizzato come
`OBDII`. L'utente ha confermato che è già associato al telefono. La compatibilità
effettiva dell'esemplare con l'app e con la centralina è ancora da verificare
sull'hardware.

Il prototipo 0.1.0 contiene già trasporto RFCOMM/SPP, inizializzazione ELM327,
letture OBD, simulatore e test del nucleo diagnostico. Il collegamento viene
avviato manualmente e l'Activity chiude la sessione passando in secondo piano,
salvo la gestione dei cambi di configurazione. Il prototipo precede il processo
di design tramite ADR concordato in questa conversazione.

Questo documento descrive la decisione approvata dall'utente. Le conferme dei
singoli requisiti hanno completato il design; il successivo comando esplicito
`APPROVO` ha autorizzato test e codice nel perimetro descritto.

## Obiettivo

Collegarsi automaticamente all'adattatore salvato quando l'utente apre l'app,
limitare i tentativi falliti e mantenere la sessione quando l'interfaccia passa
in secondo piano o lo schermo viene spento.

## Requisiti confermati dall'utente

| ID | Requisito |
| --- | --- |
| R01 | All'apertura dell'app tentare automaticamente il collegamento all'OBD già salvato, se disponibile e raggiungibile, salvo il blocco per connessione manuale descritto in R09. |
| R02 | Il profilo di destinazione identifica quello specifico dispositivo e il nome atteso `OBDII`. |
| R03 | Ogni ciclo automatico comprende al massimo **3 tentativi totali: 1 iniziale + 2 retry**. |
| R04 | Attendere **3 secondi dal fallimento** di un tentativo prima del successivo. |
| R05 | Dopo il terzo fallimento sospendere i tentativi automatici e richiedere una connessione manuale. |
| R06 | Passare a un'altra app o spegnere lo schermo non deve causare la chiusura della connessione da parte dell'app. |
| R07 | Dopo la perdita di una sessione già attiva applicare la stessa politica: massimo 3 tentativi totali, separati da 3 secondi dopo il fallimento. |
| R08 | `OBDII` è già associato al Motorola; l'associazione di sistema è un prerequisito disponibile. |
| R09 | Dopo tre fallimenti mantenere la richiesta di connessione manuale anche chiudendo e riaprendo l'app, fino all'azione esplicita dell'utente sul relativo comando. |
| R10 | Se Bluetooth e adattatore rispondono ma la centralina non risponde, mantenere il collegamento all'adattatore e mostrare «centralina non raggiungibile», senza consumare un retry Bluetooth. |

La continuità richiesta riguarda il comportamento dell'app. Una perdita reale
del collegamento Bluetooth deve essere rilevata e gestita mediante R07.
Arresto forzato dell'app e indisponibilità fisica dell'adattatore non possono
essere presentati come una connessione ancora operativa.

## Decisione architetturale accettata

### Identificazione e collegamento

Usare Bluetooth Classic tramite SPP/RFCOMM, coerentemente con il trasporto già
presente nel prototipo e con il modello dichiarato dell'adattatore. La verifica
sull'esemplare reale fa parte della validazione.

Il profilo salvato contiene il nome atteso e l'identificativo Bluetooth dello
specifico dispositivo associato. Il nome da solo non determina la destinazione:
un altro dispositivo omonimo non sostituisce automaticamente quello salvato.
Alla prima configurazione, la selezione avviene fra i dispositivi associati.

Prima di avviare il ciclo, controllare disponibilità del Bluetooth, permessi e
profilo salvato. Se manca un prerequisito, mostrare l'azione necessaria senza
consumare tentativi di connessione. Il tentativo RFCOMM verifica la concreta
raggiungibilità del dispositivo salvato; una nuova ricerca dei dispositivi
vicini non è necessaria per iniziare il collegamento a un dispositivo noto.

### Proprietà della sessione

Spostare la proprietà della sessione fuori dall'Activity e dal solo ciclo di
vita della schermata. Un servizio Android di tipo `connectedDevice` mantiene
la sessione avviata dall'app visibile e ne espone lo stato all'interfaccia.
Prevedere una notifica di sessione con un comando esplicito di interruzione.

Il ritorno a una schermata o la sua ricreazione si ricollega alla sessione
esistente. Se è già in corso un collegamento, un'attesa di retry o una sessione
attiva, non avvia un secondo ciclo parallelo.

Separare la politica di tentativi dal trasporto Android, mantenendola
verificabile nel nucleo Kotlin con trasporto e tempo simulati. Le richieste
diagnostiche restano serializzate.

### Tentativi e passaggio al comando manuale

| Evento | Azione accettata |
| --- | --- |
| Prerequisiti soddisfatti e avvio del ciclo | Tentativo 1 immediato. |
| Fallimento del tentativo 1 | Attendere 3 secondi dal fallimento, poi tentativo 2. |
| Fallimento del tentativo 2 | Attendere 3 secondi dal fallimento, poi tentativo 3. |
| Successo di un tentativo | Terminare il ciclo e annullare ogni retry ancora programmato. |
| Fallimento del tentativo 3 | Mostrare la necessità di connessione manuale; nessun tentativo 4 automatico. |
| Riapertura dell'app dopo l'esaurimento dei tentativi | Ripristinare lo stato di connessione manuale richiesta, senza avviare un nuovo ciclo automatico. |
| Perdita di una sessione attiva | Avviare un nuovo ciclo di riconnessione con lo stesso limite e la stessa attesa. |
| Bluetooth e adattatore operativi, centralina senza risposta | Mantenere il collegamento all'adattatore e mostrare lo stato della centralina separatamente; nessun retry Bluetooth per questo esito. |
| Comando esplicito di interruzione | Annullare tentativi e attese, chiudere la sessione e terminare il servizio di sessione. |

I tentativi devono essere sequenziali. Un nuovo tentativo può iniziare soltanto
dopo la conclusione o la cancellazione del precedente e la chiusura delle sue
risorse. Il contatore visualizzato si riferisce al ciclo corrente.

Come comportamento del comando manuale è previsto un tentativo per pressione
del pulsante: il suo eventuale fallimento restituisce il controllo all'utente.
Una successiva perdita di una sessione ristabilita con successo ricade in R07.

La richiesta di connessione manuale deve essere conservata anche fra chiusura
e riapertura dell'app e ha precedenza sull'autoconnessione all'apertura.
Il tentativo manuale permette di riprovare esplicitamente; se riesce, la
sessione torna operativa e la condizione che richiede l'intervento manuale
viene rimossa. Se fallisce, la richiesta di intervento manuale rimane attiva.

Il trasporto attuale impone un timeout di 15 secondi all'apertura RFCOMM.
Questo è un limite del singolo collegamento Bluetooth, distinto dall'attesa
di 3 secondi fra tentativi e dai timeout dei comandi ELM/OBD.

### Stati e riscontro all'utente

Distinguere almeno: prerequisiti mancanti, collegamento in corso con indice
del tentativo, attesa del prossimo tentativo, adattatore collegato,
inizializzazione dell'adattatore, centralina raggiunta, necessità di connessione
manuale e sessione interrotta dall'utente.

Un collegamento Bluetooth riuscito e una risposta valida della centralina
sono due risultati distinti. `NO DATA`, rifiuti della richiesta o risposte
diagnostiche incomplete non devono essere mostrati come una scansione senza
errori. I retry si applicano ai fallimenti del collegamento e
dell'inizializzazione dell'adattatore. Quando l'adattatore continua a
rispondere ma la centralina non fornisce dati, il collegamento viene mantenuto
e l'interfaccia mostra «centralina non raggiungibile» o l'esito specifico della
richiesta. Questo risultato non consuma un tentativo di connessione Bluetooth.

## Alternative valutate

| Alternativa | Valutazione |
| --- | --- |
| Connessione sempre manuale | Conservata come recupero dopo l'esaurimento dei tentativi; l'avvio normale richiesto è automatico. |
| Sessione posseduta dalla schermata | Il ciclo di vita della schermata non soddisfa il requisito di continuità in secondo piano. |
| Sessione posseduta da un servizio dedicato | Decisione accettata per separare collegamento e interfaccia e gestire la continuità richiesta. |
| Rilevamento continuo e avvio quando ci si avvicina all'auto | Il requisito concordato attiva l'autoconnessione all'apertura dell'app; l'avvio per sola vicinanza sarebbe un'estensione da progettare separatamente. |

## Conseguenze

- Introdurre un proprietario della sessione indipendente dall'Activity e uno
  stato condiviso osservabile dall'interfaccia.
- Gestire servizio, notifica, permessi e cancellazione delle operazioni.
- Conservare il profilo del dispositivo per le aperture successive dell'app.
- Conservare lo stato di connessione manuale richiesta dopo l'esaurimento dei
  tentativi, rispettandolo anche alla riapertura dell'app.
- Rendere espliciti numero del tentativo, attesa, risultato e azione manuale.
- Aggiungere test della politica di riconnessione e verifiche del ciclo di vita
  Android, oltre ai test di protocollo già presenti.
- Verificare continuità, comportamento a schermo spento e comunicazione con la
  vettura sul Motorola e sull'adattatore reali.

La continuità del collegamento non introduce da sola acquisizioni periodiche,
registrazione continua dei parametri o operazioni automatiche sulla vettura.
Questi comportamenti richiederanno un proprio design. La cancellazione errori
rimane un argomento successivo.

## Criteri di accettazione e verifiche

Questa è la specifica approvata dei test. L'implementazione e la relativa
copertura sono descritte nel [rapporto di verifica](../verification/adr-0001-verifica.md).
Le prove sull'hardware reale restano distinte da quelle automatiche.

| ID | Scenario da verificare | Tipo di verifica previsto |
| --- | --- | --- |
| AC01 | Con profilo salvato e prerequisiti soddisfatti, aprire l'app avvia il tentativo senza premere Connetti. | Orchestrazione Kotlin e integrazione Android. |
| AC02 | Dopo il primo fallimento, nessun retry parte prima dei 3 secondi; poi parte il secondo tentativo. La stessa regola vale per il terzo. | Test Kotlin con tempo virtuale. |
| AC03 | Dopo tre fallimenti complessivi non parte un quarto tentativo, anche facendo avanzare il tempo simulato. Viene richiesta la connessione manuale. | Test Kotlin e stato UI. |
| AC04 | Il successo al primo, secondo o terzo tentativo termina il ciclo e impedisce retry residui. | Test Kotlin. |
| AC05 | Dopo la perdita di una sessione attiva viene applicato un nuovo ciclo con massimo tre tentativi e attese di 3 secondi dopo i fallimenti. | Test Kotlin con trasporto simulato. |
| AC06 | Aprire altre schermate, ricreare l'Activity o tornare all'app non duplica il collegamento o un ciclo in corso. | Test di integrazione Android. |
| AC07 | Passare a un'altra app e spegnere lo schermo mantengono la sessione gestita dal servizio; lo stato resta osservabile tornando all'app. | Test Android e prova sul Motorola. |
| AC08 | Il comando di interruzione annulla un tentativo o un'attesa in corso e impedisce riconnessioni residue. | Test Kotlin e integrazione Android. |
| AC09 | Un dispositivo omonimo con identificativo diverso non viene collegato al posto dell'obiettivo salvato. | Test di selezione del profilo. |
| AC10 | Bluetooth spento, permesso assente o profilo mancante producono uno stato esplicito e non consumano tentativi di collegamento. | Test Kotlin e integrazione Android. |
| AC11 | Raggiunto il limite, chiudere e riaprire l'app mantiene la richiesta di connessione manuale e non avvia tentativi automatici. Il comando manuale permette un nuovo tentativo esplicito: il successo rimuove il blocco, il fallimento lo mantiene. | Test Kotlin con stato persistente simulato e integrazione Android/UI. |
| AC12 | Con Bluetooth e adattatore operativi, una mancata risposta della centralina mantiene il collegamento, mostra un esito esplicito e non consuma retry Bluetooth. I fallimenti del collegamento e dell'inizializzazione dell'adattatore restano soggetti al limite di tre tentativi. | Test del coordinamento della sessione. |

Le verifiche del protocollo già presenti vanno preservate. La modifica sarà
considerata completata soltanto dopo l'esito positivo dei test pertinenti;
la validazione sull'hardware sarà registrata separatamente dai test simulati.

## DA CHIEDERE

Le voci risolte restano nel documento per conservare le motivazioni delle
decisioni. Tutte le voci attualmente raccolte sono risolte. Eventuali nuovi
dubbi devono entrare in questa lista ed essere discussi prima delle modifiche
che dipendono dalla relativa decisione.

| ID | Dubbio o informazione | Stato | Esito concordato |
| --- | --- | --- | --- |
| DQ01 | Quando attivare l'autoconnessione? | Risolto | All'apertura dell'app, verso il dispositivo salvato. |
| DQ02 | `OBDII` è già associato al Motorola? | Risolto | Sì, confermato dall'utente. |
| DQ03 | Quanti tentativi complessivi? | Risolto | Tre: primo tentativo più due retry; poi connessione manuale. |
| DQ04 | Quanto attendere fra i tentativi? | Risolto | Tre secondi dal fallimento del precedente. |
| DQ05 | Continuità in secondo piano e politica dopo la perdita del collegamento? | Risolto | La sessione continua; in caso di perdita si applica lo stesso limite di tre tentativi e la stessa attesa. |
| DQ06 | Dopo tre fallimenti, chiudere e riaprire l'app deve avviare un nuovo ciclo automatico oppure mantenere la richiesta di connessione manuale? | Risolto | Mantenere la richiesta di connessione manuale fino a un'azione esplicita sul relativo comando, anche alla riapertura dell'app. Decisione confermata dall'utente. |
| DQ07 | Se Bluetooth e adattatore rispondono ma la centralina non risponde, bisogna chiudere tutto e consumare un retry oppure mantenere il collegamento all'adattatore? | Risolto | Mantenere il collegamento e mostrare «centralina non raggiungibile», senza consumare un retry Bluetooth. Decisione confermata dall'utente. |

## Approvazione e sequenza di lavoro

1. Completare la discussione dell'ADR e delle voci aperte in DA CHIEDERE.
2. Presentare il documento completo per revisione.
3. Ricevere il comando esplicito `APPROVO` per questo perimetro.
4. Portare l'ADR in stato Accettato e creare i test concordati.
5. Implementare il codice e verificare l'esito positivo dei test pertinenti.
6. Se cambia una decisione di design, tornare alla discussione dell'ADR prima
   di apportare le modifiche dipendenti da quella decisione.

## Riferimenti

- [Android: individuare e usare dispositivi Bluetooth associati](https://developer.android.com/develop/connectivity/bluetooth/find-bluetooth-devices)
- [Android: collegamento Bluetooth Classic](https://developer.android.com/develop/connectivity/bluetooth/connect-bluetooth-devices)
- [Android: permessi Bluetooth](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
- [Android: servizio di tipo connectedDevice](https://developer.android.com/develop/background-work/services/fgs/service-types#connected-device)
- [Android: vincoli sull'avvio dei servizi dal secondo piano](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [ELM327: documentazione del produttore](https://www.elmelectronics.com/wp-content/uploads/2017/01/ELM327DS.pdf)
