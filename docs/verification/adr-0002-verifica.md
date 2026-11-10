# Verifica di ADR-0002 · Multipla OBD 0.2.1

Data: 10 novembre 2026. ADR approvato da Luca con “Approvo” nella stessa data.

## Esito

**71 test superati, nessun fallimento, errore o test saltato.** Compilazione APK e lint completati. Lint: 0 errori e 3 avvisi non bloccanti (`NewerVersionAvailable` e due `UseKtx`).

Questa è una verifica automatica con la risposta reale riprodotta nei test. La nuova APK non è stata eseguita sul Motorola o collegata alla vettura durante questa verifica.

## Test prima del codice applicativo

I nuovi test sono stati scritti prima delle correzioni ed eseguiti sul codice applicativo originale 0.2.0. La raccolta definitiva precedente alle modifiche contiene 71 test: 10 falliscono sui comportamenti da correggere, senza errori di compilazione o di ambiente.

| Modulo | Test | Fallimenti prima | Fallimenti dopo | Saltati dopo |
| --- | ---: | ---: | ---: | ---: |
| obd-core | 61 | 6 | 0 | 0 |
| app | 10 | 4 | 0 | 0 |
| Totale | 71 | 10 | 0 | 0 |

Sono inclusi i 54 test della versione precedente e 17 nuovi test. I risultati per suite e i nomi delle regressioni sono in `adr-0002-test-results.json`, accanto a questo documento nei sorgenti.

Le regressioni iniziali riguardano puntini di ricerca, prima verifica completa, conservazione di NO DATA, blocco dopo un singolo fallimento manuale, messaggio di lettura fallita e registro delle transizioni. Il ripristino del blocco dopo un successo manuale era già implementato: la copertura è stata estesa alla riapertura, alle nuove istanze Android e alla successiva perdita del collegamento.

## Copertura dell’ADR

| Criterio | Evidenza |
| --- | --- |
| T01 | `FirstConnectionTest`: la risposta `SEARCHING...\r..4100BE3EB81100\r>` conserva tutti i byte dei dati, compreso quello finale già presente nel registro. |
| T02 | `FirstConnectionTest`: progressione sulla stessa riga e su più righe; dati corrotti, troncati o con puntini interni rifiutati; contesto limitato al comando e al primo dato; NO DATA mantenuto distinto. |
| T03 | `FirstConnectionTest`: coordinatore, sessione ELM e parser reali arrivano alla centralina con una sola connessione manuale, un solo ATZ e un solo 0100; protocollo KWP FAST e PID delle due pagine riconosciuti. |
| T04 | `ConnectionControllerTest`: esattamente tre tentativi automatici; attesa di tre secondi dopo ogni fallimento, nessun quarto tentativo; blocco persistente alla ricreazione. |
| T05 | `ConnectionControllerTest` e `ConnectionRecoveryTest`: un fallimento manuale mantiene il blocco preesistente oppure l’abilitazione preesistente, senza retry automatici immediati. `SessionServiceTest`: servizio foreground terminato, wake lock rilasciato e automatismo ancora abilitato dopo il fallimento manuale senza blocco precedente. |
| T06 | `ConnectionRecoveryTest` e `SessionServiceTest`: successo manuale, rimozione del blocco persistente, nuove istanze di sessione e preferenze Android, successiva apertura automatica senza duplicare la sessione attiva. |
| T07 | `ConnectionRecoveryTest`: dopo il successo manuale, la perdita del collegamento concede tre nuovi tentativi; il nuovo blocco scatta soltanto al terzo nuovo fallimento; un ulteriore successo manuale lo rimuove ancora. |
| T08 | `ConnectionRecoveryTest`: un adattatore inizializzato con centralina senza dati riabilita comunque l’automatismo e conserva il collegamento. |
| T09 | `ConnectionDiagnosticsTest`: rapporto con origine e numero dei tentativi, sospensione e riabilitazione esplicite; errore di formato distinto da centralina senza dati. |

I test del nucleo usano tempo simulato. I test Android del servizio e delle preferenze usano Robolectric su API 35. Non sostituiscono una prova su Android 17 e sul trasporto Bluetooth reale.

## Comandi e pacchetto

Verifica completata:

```sh
./gradlew :obd-core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Nell’ambiente di verifica è stata usata la stessa distribuzione Gradle 8.13 del wrapper, con JDK 17.0.21, Android SDK 36 e Build-Tools 35.0.0. Dipendenze applicative e protocolli diagnostici restano quelli del progetto precedente.

| Controllo | Esito |
| --- | --- |
| Identità APK | `it.multipla.obd`, versione `0.2.1`, versionCode `3`. |
| SDK | minSdk 31, target/compile SDK 36. |
| Firma | `apksigner verify` superato; firma APK v2 valida. |
| Allineamento | `zipalign -c -P 16 4` superato. |
| Collegamento alla chiave inclusa | Certificato esportato da `signing/debug.keystore` corrispondente al firmatario APK. |
| Rete | Manifest dell’APK privo di `INTERNET` e `ACCESS_NETWORK_STATE`. |
| Operazioni OBD | Conservata la sola lettura; nessuna cancellazione errori o comando arbitrario aggiunto. |

SHA-256 APK:

```text
db21595d512b320f34fd14a602d07b2d876951ab77de465552f43d260fcb34b1
```

## Installazione: firma diversa dalla 0.2.0

La chiave con cui era stata firmata la APK 0.2.0 non era inclusa nei sorgenti recuperati e non è disponibile. Il confronto fra i certificati conferma che l’aggiornamento diretto sopra quella APK non è possibile.

1. Esportare dalla vecchia app eventuali rapporti ancora da conservare.
2. Disinstallare la vecchia app e installare la APK 0.2.1.
3. Concedere nuovamente i permessi e selezionare/salvare OBDII. La disinstallazione rimuove profilo e dati interni dell’app.
4. Conservare i sorgenti di questa versione: contengono la chiave debug già configurata per firmare gli aggiornamenti successivi.

| Versione | SHA-256 del certificato |
| --- | --- |
| 0.2.0 | `690e933b3fdbbab2d70d5aac9426bfa349afb70181d6a85b373ff24d9dbd57be` |
| 0.2.1 | `ea6442a00c98a7f12698a9bddc77dd01b5ad862b382209177a18ca48e32e07fc` |

## Prove da eseguire sulla vettura

Eseguire le prove con vettura ferma.

1. **Primo collegamento:** con adattatore disponibile e quadro acceso, avviare il collegamento. La risposta iniziale con SEARCHING e puntini deve consentire di raggiungere la centralina senza premere una seconda volta. Esportare il registro anche se l’esito fosse diverso.
2. **Tre fallimenti automatici:** con profilo salvato e adattatore non raggiungibile, aprire l’app. Verificare tentativi 1/3, 2/3 e 3/3, poi `AUTOCONNESSIONE SOSPESA`; nessun quarto tentativo.
3. **Persistenza della sospensione:** chiudere e riaprire l’app prima di un successo manuale. Il blocco deve rimanere e non deve partire un nuovo ciclo.
4. **Successo manuale:** rendere disponibile l’adattatore e usare Connetti manualmente. Nel registro deve comparire `AUTOCONNESSIONE RIABILITATA · Collegamento manuale riuscito`. L’eventuale stato della centralina rimane separato.
5. **Apertura successiva:** terminare la sessione, chiudere l’app e riaprirla con adattatore disponibile. Deve essere possibile l’avvio automatico. Se una sessione è ancora attiva, il ritorno all’app deve riutilizzarla.
6. **Perdita successiva:** dopo un successo manuale, perdere il collegamento. Devono tornare disponibili tre tentativi automatici, con nuovo blocco soltanto se falliscono tutti.
7. **Continuità:** ripetere passaggio ad altra app e prova a schermo spento. Il passaggio ad altra app era riuscito nella 0.2.0 secondo la prova di Luca; la nuova build e lo schermo spento richiedono verifica reale.

I registri di queste prove permetteranno di chiudere la verifica hardware. Gli altri punti della lista **DA CHIEDERE** restano rinviati.

