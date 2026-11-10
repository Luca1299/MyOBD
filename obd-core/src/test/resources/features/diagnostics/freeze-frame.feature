@app-D-0001 @diagnosis @read-only
Feature: Stored diagnostic freeze frame

  @DIA-F01
  Scenario: Stored freeze frame values remain distinct from current measurements
    Dato un freeze frame memorizzato con codice associato e un valore diverso
    dal corrispondente parametro attuale, quando si raccoglie la diagnosi, il
    frame conserva il proprio valore e il proprio codice separatamente dal campione attuale.

    Given a valid stored freeze frame identifies an associated trouble code
    And a stored parameter differs from its current measurement
    When the diagnostic data is collected
    Then the freeze frame retains its stored value and associated code
    And the current measurement remains a separate value

  @DIA-F02
  Scenario: Valid absence of a freeze frame is distinct from a missing response
    Data una risposta valida che dichiara l'assenza del frame e un altro caso
    senza risposta, quando si classificano i due esiti, soltanto il primo
    dichiara che non è stato restituito un frame memorizzato.

    Given one controlled reading validly declares that no freeze frame is stored
    And another controlled reading receives no freeze frame response
    When their freeze frame outcomes are classified
    Then the first reading reports valid absence of a stored frame
    And the second reading remains inconclusive

  @DIA-F03
  Scenario: A missing stored parameter is not replaced by current data
    Dato un frame leggibile con un parametro mancante e un valore attuale
    disponibile per lo stesso parametro, quando si prepara il frame, i valori
    memorizzati restano e la mancanza viene esposta senza usare il valore attuale.

    Given a readable freeze frame is missing one expected parameter
    And a current measurement for that parameter is available
    When the freeze frame result is prepared
    Then its readable stored parameters are retained
    And the missing stored parameter is reported with its limitation
    And its value is not filled from the current measurement

  @DIA-F04
  Scenario: Current parameter support does not determine freeze frame support
    Data una disponibilità dei parametri attuali diversa da quella dichiarata
    per il frame, quando si acquisisce il frame, si usa la disponibilità del
    contesto memorizzato senza copiarla dal campione attuale.

    Given current parameter support differs from valid freeze frame parameter support
    When the freeze frame acquisition is prepared
    Then supported stored parameters are determined in the freeze frame context
    And current parameter support is not used as a substitute

  @DIA-F05
  Scenario: Uncertain response provenance does not create a guessed association
    Date risposte che non permettono di associare con certezza codice, frame
    e centralina, quando si prepara il risultato, il limite resta esplicito
    e non vengono inventate associazioni o attribuzioni alla gestione metano.

    Given the received data does not establish a certain association between frame, code and control unit
    When the freeze frame result is prepared
    Then uncertain associations remain explicitly unverified
    And no methane control unit attribution is inferred
    And no single coherent frame is fabricated from ambiguously related data

  @DIA-F06
  Scenario: Frame acquisition time is not presented as fault occurrence time
    Dato un frame valido senza data dell'evento e l'ora in cui l'app lo legge,
    quando si prepara il risultato, l'ora di lettura resta un dato di
    acquisizione e non viene mostrata come momento del guasto.

    Given a valid freeze frame does not provide a fault occurrence time
    And the application knows when it reads the frame
    When the freeze frame result is prepared
    Then the read time remains acquisition metadata
    And the fault occurrence time remains unknown
