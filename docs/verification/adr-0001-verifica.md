# Verifica di ADR-0001 · Multipla OBD 0.2.0

Data: 9 novembre 2026. ADR approvato dall'utente con il comando `APPROVO`.

## Metodo

I nuovi test sono stati scritti prima dell'implementazione dei comportamenti.
La prima esecuzione non compilava perché mancavano il coordinatore e le nuove
operazioni di inizializzazione separata. Dopo l'implementazione sono state
eseguite le suite del nucleo e di integrazione Android, la compilazione APK e
l'analisi statica. I controlli sulla logica usano tempo virtuale e trasporti
simulati; i controlli sul flusso usano pipe reali in memoria.

## Copertura dei criteri

| Criterio ADR | Evidenza automatica | Verifica reale residua |
| --- | --- | --- |
| AC01 | ConnectionControllerTest: apertura automatica sul target salvato. | Collegamento al NK. |
| AC02 | Tempo virtuale: retry a 3 secondi dal termine del fallimento, anche se il tentativo dura 500 ms. | Osservazione dei tempi del collegamento reale. |
| AC03 | Esattamente tre tentativi; avanzare ulteriormente il tempo non ne produce un quarto. | Stato visualizzato sul Motorola. |
| AC04 | Successo al primo, secondo e terzo tentativo; nessun retry residuo. | Risposta reale dell'adattatore. |
| AC05 | Perdita del link simulata, nuovo ciclo con tre tentativi complessivi. PromptConnectionTest verifica EOF senza polling. | Perdita e ripristino della portata Bluetooth. |
| AC06 | Aperture ripetute non duplicano i tentativi; bind/unbind/rebind del servizio non chiudono né duplicano il link. | Rotazione e ritorno all'app sul telefono. |
| AC07 | Servizio con notifica, sessione conservata al distacco della UI e interattività simulata disattivata; wake lock mantenuto. | Schermo spento e app in secondo piano sul Motorola Android 17. |
| AC08 | Stop annulla tentativi e attese, chiude il link e ignora segnali di perdita successivi; il comando della notifica rilascia il wake lock. | Comando Interrompi sul telefono. |
| AC09 | L'identificativo passato al trasporto è quello salvato. | Conferma del dispositivo selezionato fra eventuali omonimi. |
| AC10 | Prerequisiti mancanti non avviano tentativi; ripristinarli permette di procedere. | Permessi e stato Bluetooth del telefono. |
| AC11 | Blocco manuale preservato creando un nuovo coordinatore e nuove istanze dello store Android; successo manuale lo rimuove, fallimento lo mantiene. | Chiusura e riapertura dell'app dopo tre fallimenti reali. |
| AC12 | NO DATA mantiene l'adattatore e non consuma retry; una nuova verifica raggiunge la centralina senza riaprire/reset dell'adattatore. | Risposta con quadro acceso/spento, secondo il comportamento della vettura. |

Ulteriori verifiche coprono richieste concorrenti, stop durante un collegamento
pendente, cancellazione e timeout del flusso, rifiuto di risposte obsolete,
isolamento della demo dal blocco reale e conservazione dei test di protocollo
precedenti. Robolectric usa API 35: non è una prova dell'hardware o di Android 17.

## Esito delle verifiche

| Controllo | Esito |
| --- | --- |
| Test del nucleo JVM | 50 passati, nessun errore o test saltato. |
| Integrazione Android con Robolectric API 35 | 4 passati, nessun errore o test saltato. |
| Totale | **54 test passati**. |
| Compilazione debug | `assembleDebug` completato. |
| Analisi statica | `lintDebug` completato, nessun errore. |
| Firma APK | Verificata con apksigner; stesso firmatario debug della versione 0.1.0. |
| Hardware reale e Android 17 | Non ancora verificati. |

I test del nucleo comprendono 19 casi del coordinatore, 4 del flusso passivo,
1 della separazione adattatore/centralina e i 26 test di protocollo preesistenti.
I 4 test Android coprono ciclo di vita, notifica/stop/wake lock, persistenza e
isolamento della demo. Gli avvisi non bloccanti di lint sono registrati nel
report generato dal build; non sono errori delle verifiche.

## Prova da eseguire sulla vettura

1. Con vettura ferma, configurare il profilo OBDII e verificare il collegamento.
2. Aprire un'altra app, spegnere lo schermo per almeno un minuto e tornare:
   controllare stato, notifica e registro, senza un nuovo collegamento involontario.
3. Interrompere dalla notifica e verificare la chiusura della sessione.
4. Con adattatore non raggiungibile e profilo salvato, aprire l'app: osservare
   tre tentativi totali, con due pause di tre secondi dopo i fallimenti.
5. Dopo il terzo fallimento chiudere e riaprire l'app: deve rimanere richiesta la
   connessione manuale, senza ulteriori tentativi automatici.
6. Rendere disponibile l'adattatore e premere Connetti manualmente: il successo
   deve rimuovere il blocco. Un tentativo manuale fallito deve mantenerlo.
7. Durante una sessione ripristinata provocare una perdita del collegamento
   all'adattatore e verificare lo stesso ciclo di riconnessione.
8. Se l'adattatore risponde ma la centralina no, verificare lo stato distinto e
   usare Riprova verifica centralina: la connessione Bluetooth deve restare attiva.
9. Esportare il TXT, anche dopo un errore, e conservare i risultati per il
   confronto con i test simulati.

Questi casi non sono stati eseguiti sul Motorola o sulla Multipla durante questa
implementazione. La verifica fisica resta separata dall'esito dei test automatici.
