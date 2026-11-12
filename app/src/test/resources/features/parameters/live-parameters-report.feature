@app-D-0002 @live-parameters @read-only
Feature: Ended live observation in the session text report

  @LIVE-T01
  Scenario Outline: Export preserves the ended observation without new readings
    Data un'osservazione conclusa volontariamente o interrotta, quando
    viene esportato il Registro, la fotografia conserva contesto, selezione,
    tempi, valori, unità ed esiti senza nuove interrogazioni.

    Given an observation has ended as "<outcome>"
    And its result distinguishes valid, previous and unacquired values
    When the session text report is exported
    Then it includes that ended observation as a distinct snapshot
    And it preserves mode, manual fuel annotation and selected parameters
    And it includes available adapter, protocol, start and end information
    And it preserves values, units, valid reading times and last attempt outcomes
    And it includes the available ending reason and last cycle duration
    And unacquired values are not represented by invented numbers
    And no new vehicle reading is performed for export

    Examples:
      | outcome           |
      | voluntarily ended |
      | interrupted       |

  @LIVE-T02
  Scenario Outline: A current unfinished observation is not exported as ended
    Data un'osservazione ancora attiva o in pausa, quando viene richiesta
    l'esportazione del tentativo corrente, non viene prodotto un rapporto
    che la presenti come conclusa.

    Given the current observation is "<state>"
    When export of that current observation as an ended result is requested
    Then no ended observation report is produced for that current observation
    And its values are not represented as a concluded acquisition

    Examples:
      | state          |
      | acquiring      |
      | pause pending  |
      | paused         |
      | ending pending |

  @LIVE-T03
  Scenario: Prepared export remains an immutable snapshot
    Data la fotografia preparata per l'esportazione di un'osservazione
    conclusa, quando lo stato dell'app cambia prima del salvataggio,
    il testo salvato rimane quello preparato.

    Given a text export snapshot has been prepared from an ended observation
    When application state changes before that snapshot is saved
    Then the saved text still contains the prepared observation snapshot
    And later state changes do not alter its context, values or reading times

  @LIVE-T04
  Scenario: Demo export cannot be mistaken for real vehicle measurements
    Data un'osservazione demo conclusa, quando viene esportato il Registro,
    i dati sono identificati come simulati e non vengono attribuiti
    alla vettura reale o a una centralina benzina o metano accertata.

    Given an ended observation was acquired in demo mode
    When the session text report is exported
    Then the observation snapshot is explicitly identified as simulated
    And its values are not represented as measurements from the real vehicle
    And manual fuel annotation does not become verified control unit provenance

  @LIVE-T05
  Scenario: Observation, diagnosis and bounded log remain distinct in export
    Dati una diagnosi conclusa e un'osservazione successiva con contesti
    diversi, quando viene esportato il Registro, i due risultati mantengono
    i propri contesti e gli scambi disponibili non vengono presentati come
    una registrazione completa di tutta l'osservazione.

    Given an ended diagnosis and an ended observation have different captured contexts
    And the current exchange log contains only its bounded retained entries
    When the session text report is exported
    Then diagnosis and observation remain distinct with their original contexts
    And preparation of a subsequent reading does not relabel either result
    And the exchange log is identified as bounded
    And the report does not claim a complete continuous observation history
