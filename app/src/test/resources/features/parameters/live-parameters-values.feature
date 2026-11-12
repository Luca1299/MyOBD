@app-D-0002 @live-parameters @read-only
Feature: Live engine parameter values

  @LIVE-V01
  Scenario: Only selected and declared available parameters are requested
    Dati parametri del catalogo selezionati, esclusi o non dichiarati
    disponibili, quando viene eseguito un giro, vengono interrogati una volta
    solo quelli selezionati e disponibili, nell'ordine del catalogo.

    Given the current vehicle declares a subset of catalog parameters available
    And only some available parameters are selected
    When an observation cycle is performed
    Then each selected and declared available parameter is requested once
    And requests follow the existing catalog order
    And excluded or undeclared parameters are not requested
    And no lamp status, trouble code or other diagnostic section is added to the cycle

  @LIVE-V02
  Scenario: Each response updates its parameter before the cycle ends
    Dato un giro con più parametri, quando arriva una risposta valida e
    un'altra richiesta non è ancora conclusa, il primo valore è già visibile
    con il proprio momento di ricezione, senza attendere il giro completo.

    Given an observation cycle includes more than one parameter
    When the first valid parameter response arrives
    And another parameter request has not completed
    Then the first value is already available with its reception time
    And the cycle is not presented as a simultaneous vehicle snapshot

  @LIVE-V03
  Scenario: A valid zero remains a measurement
    Data una risposta valida con valore zero, quando viene acquisita,
    zero rimane visibile con la sua unità e non viene trasformato in
    assenza di dati o parametro non supportato.

    Given a selected parameter returns a valid zero measurement
    When that response is acquired
    Then zero is retained and presented with its measurement unit
    And it is not classified as missing or unsupported

  @LIVE-V04
  Scenario: An unchanged value still receives a new reading time
    Dato un valore valido già acquisito, quando una lettura successiva
    restituisce lo stesso numero, il valore rimane uguale ma il momento
    dell'ultima lettura valida viene aggiornato.

    Given a parameter already has a valid value
    When a later valid response contains the same numeric value
    Then the numeric value remains unchanged
    And its last valid reception time is replaced by the later reception time

  @LIVE-V05
  Scenario Outline: Content errors preserve the distinction between old and absent values
    Dato un parametro con o senza un precedente valore valido, quando
    l'ultimo tentativo fallisce per contenuto, il precedente valore e il
    suo tempo non vengono inventati o aggiornati. Gli altri parametri
    proseguono e il parametro viene ritentato nel giro successivo.

    Given a selected declared available parameter has "<previous_value>"
    When its next request returns "<response>"
    Then the last attempt records the available failure reason
    And any previous valid value and reception time remain unchanged
    And a previous value is identified as previous rather than newly read
    And no numeric value is invented when no valid value exists
    And the parameter is not reclassified as unsupported
    And other selected parameters can still be read
    And no additional attempt is made for that parameter in the same cycle
    And it is requested again in the next cycle

    Examples:
      | previous_value         | response               |
      | a previous valid value | NO DATA                |
      | a previous valid value | a malformed response   |
      | a previous valid value | a negative response    |
      | no valid value         | NO DATA                |
      | no valid value         | a malformed response   |
      | no valid value         | a negative response    |

  @LIVE-V06
  Scenario: A wholly inconclusive cycle does not become a successful empty result
    Dato un giro nel quale nessun parametro produce un valore valido ma
    il trasporto resta utilizzabile, quando il giro conclude, l'assenza
    di letture valide è esplicita e l'osservazione continua.

    Given every selected parameter request in a cycle is inconclusive
    And the transport remains usable
    When that cycle ends
    Then the observation states that the last cycle had no valid reading
    And missing values are not replaced with zero
    And parameters are not declared unsupported because of those failures
    And the observation remains active for its next scheduled cycle

  @LIVE-V07
  Scenario: Request timeout interrupts observation through the existing transport policy
    Data una richiesta che non conclude entro il timeout esistente,
    quando scade il limite, l'osservazione è interrotta con il motivo
    disponibile e la gestione del trasporto rimane quella esistente.

    Given an observation parameter request does not complete
    When its existing request timeout expires
    Then the observation ends as interrupted with the timeout reason
    And already acquired valid values remain available
    And connection handling follows the existing transport failure policy
    And observation does not resume automatically after reconnection

  @LIVE-V08
  Scenario: Value age belongs to its own last valid response
    Dati valori ricevuti in momenti diversi, quando passa il tempo o viene
    aggiornato un altro parametro, ogni valore conserva il proprio momento
    di ricezione e mostra la propria età senza una soglia inventata.

    Given selected parameters have valid responses received at different times
    When time passes and another parameter is updated
    Then each value retains its own last valid reception time until reread
    And each value exposes the age of that response
    And updating another parameter does not refresh its age
    And no arbitrary freshness threshold is applied

  @LIVE-V09
  Scenario: Existing constant demo values remain simulated new readings
    Data un'osservazione demo con i valori costanti esistenti, quando
    vengono eseguiti nuovi giri, i valori restano quelli simulati e i tempi
    avanzano, senza accesso o modifiche al dispositivo reale.

    Given the existing demo transport supplies its constant parameter values
    And the real saved profile and automatic connection block are known
    When successive demo observation cycles are performed
    Then values remain identified as simulated
    And valid reception times advance for each new response
    And no real device transport is accessed
    And the real saved profile and connection block remain unchanged
    And retained real results are not overwritten
