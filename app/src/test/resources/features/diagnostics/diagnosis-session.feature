@app-D-0001 @diagnosis @read-only
Feature: Manual read-only diagnosis session

  @DIA-S01
  Scenario Outline: Connection events do not start a diagnosis
    Dato un evento di collegamento senza richiesta esplicita di diagnosi,
    quando il collegamento diventa disponibile, l'app resta pronta e non avvia
    una nuova acquisizione diagnostica.

    Given no manual diagnosis has been requested
    When the vehicle connection becomes available after "<event>"
    Then no diagnosis is started automatically
    And a manual diagnosis can be requested when its prerequisites are satisfied

    Examples:
      | event                |
      | initial connection   |
      | successful reconnect |

  @DIA-S02
  Scenario: Unmet diagnosis prerequisites do not start vehicle requests
    Dato un collegamento in cui la centralina non è ancora raggiunta, quando
    viene richiesta la diagnosi, nessuna acquisizione parte e resta esposto
    il prerequisito da soddisfare.

    Given the vehicle control unit has not been reached
    When a manual diagnosis is requested
    Then no diagnosis acquisition is started
    And the unmet connection prerequisite remains available for presentation

  @DIA-S03
  Scenario: Only one diagnostic operation owns the session
    Data una diagnosi in corso, quando arrivano un secondo avvio e richieste
    manuali di altre letture, non si accodano nuove diagnosi o acquisizioni
    concorrenti e rimane attiva soltanto l'operazione originale.

    Given a manual diagnosis is in progress
    When another diagnosis and other manual diagnostic readings are requested
    Then no additional diagnostic operation is started or queued
    And only the original operation continues to use the diagnostic connection

  @DIA-S04
  Scenario Outline: Acquisition outcome describes the readings rather than vehicle health
    Data una diagnosi manuale con gli esiti indicati delle sezioni, quando
    l'acquisizione termina, il riepilogo descrive se è completata o parziale
    senza trasformare il risultato in un giudizio sulla salute della vettura.

    Given a manual diagnosis ends with "<section outcomes>"
    When the acquisition outcome is prepared
    Then the diagnosis outcome is "<outcome>"
    And individual section outcomes remain available
    And the result makes no vehicle health or inspection pass claim

    Examples:
      | section outcomes                                          | outcome   |
      | valid data in every planned section                        | completed |
      | valid data and confirmed unsupported functions             | completed |
      | valid data and valid absence of a stored frame             | completed |
      | valid data and an inconclusive reading                     | partial   |
      | no acquired data because all reads returned content errors | partial   |

  @DIA-S05
  Scenario: A local reading error preserves results from other sections
    Data una sezione riuscita e una successiva risposta di contenuto non
    interpretabile con trasporto ancora operativo, quando prosegue la
    diagnosi, i dati già acquisiti restano e si leggono le sezioni successive possibili.

    Given a diagnosis has acquired data from an earlier section
    And a later section returns a content error while the transport remains usable
    When the diagnosis proceeds through its remaining sections
    Then the earlier acquired data is retained
    And the failed section remains inconclusive
    And other readable sections are still acquired

  @DIA-S06
  Scenario: Diagnosis interruption waits for the active request without starting another
    Data una diagnosi con richiesta in corso, quando la persona chiede
    l'interruzione, viene attesa la conclusione entro il limite della richiesta.
    Se questa riesce, il dato si conserva, non partono altre richieste e il
    collegamento resta disponibile con diagnosi interrotta.

    Given a diagnosis has a vehicle request in progress
    When diagnosis interruption is requested
    Then diagnosis interruption remains pending until that request ends within its existing limit
    And no following diagnostic request is started
    When the active request returns a valid response
    Then its acquired data is retained
    And the diagnosis outcome is interrupted
    And later sections remain not performed
    And diagnosis interruption alone does not close the connection

  @DIA-S07
  Scenario: Explicit disconnection ends the session and retains acquired results
    Data una diagnosi con dati già acquisiti, quando la persona scollega
    esplicitamente la sessione, il collegamento viene chiuso e i risultati
    restano in memoria come diagnosi interrotta, senza riconnessione residua dovuta al comando.

    Given a diagnosis has already acquired some results
    When the diagnostic session is explicitly disconnected
    Then the active connection is closed through the explicit stop operation
    And the diagnosis outcome is interrupted
    And its acquired results remain available in session memory
    And the explicit stop does not schedule a reconnection

  @DIA-S08
  Scenario: Connection recovery does not resume an interrupted diagnosis
    Data una diagnosi interrotta dalla perdita del collegamento, quando il
    collegamento viene recuperato, i risultati restano consultabili ma
    l'acquisizione non riparte senza una nuova richiesta manuale.

    Given a diagnosis was interrupted by transport loss after acquiring some results
    When the connection is recovered by the existing connection policy
    Then the interrupted diagnosis results remain available
    And the diagnosis is not resumed or restarted automatically

  @DIA-S09
  Scenario Outline: Presentation lifecycle events do not duplicate the diagnosis
    Data una diagnosi posseduta dalla sessione di servizio, quando cambia lo
    stato della presentazione, l'acquisizione continua con lo stesso
    proprietario e senza una seconda scansione.

    Given one diagnosis is owned by the service session
    When "<event>" occurs
    Then the same diagnosis remains owned by that session
    And no duplicate diagnosis or connection is started

    Examples:
      | event                      |
      | navigation to another tab  |
      | recreation of the activity |
      | the screen turns off       |

  @DIA-S10
  Scenario: A new diagnosis does not inherit old values or late results
    Data una diagnosi precedente con risultati, quando viene avviato un
    nuovo tentativo, le sue sezioni non ereditano valori vecchi e risposte
    tardive della prima acquisizione non vengono attribuite alla nuova.

    Given a previous diagnosis has acquired section results
    When a new manual diagnosis is started
    Then its section results belong only to the new acquisition
    And prior values are not used to fill sections not yet acquired
    And late results from the previous acquisition do not update the new diagnosis

  @DIA-S11
  Scenario: Fuel annotation belongs to the acquisition that captured it
    Data una diagnosi con un'annotazione di alimentazione, quando si prepara
    un'annotazione diversa per la lettura successiva, il risultato precedente
    conserva quella iniziale e l'app non deduce l'alimentazione dai valori letti.

    Given a diagnosis captures a manual fuel annotation at its start
    When a different annotation is prepared after that diagnosis ends
    Then the ended diagnosis retains its captured annotation
    And fuel is not inferred from generic diagnostic measurements

  @DIA-S12
  Scenario: In-session results do not create a persistent diagnosis history
    Dato un risultato diagnostico concluso, quando viene reso consultabile
    nella sessione, non viene creato uno storico persistente nell'app e la
    conservazione oltre la sessione rimane affidata all'esportazione esplicita.

    Given a diagnosis has ended with results available in memory
    When those results are made available within the application session
    Then no persistent diagnosis history entry is created
    And retaining that diagnosis beyond the session requires explicit report export

  @DIA-S13
  Scenario: Demo diagnosis stays isolated from the real device state
    Data una sessione simulata e un profilo reale esistente, quando viene
    acquisita una diagnosi demo, i risultati sono identificati come simulati e
    profilo, blocco e dati del dispositivo reale non vengono sovrascritti dai dati sintetici.

    Given a real saved adapter profile and automatic connection block state exist
    And the application is using a demo session
    When a manual demo diagnosis is acquired
    Then its results are identified as simulated
    And the real saved profile and connection block are unchanged
    And synthetic results do not overwrite real diagnosis data
