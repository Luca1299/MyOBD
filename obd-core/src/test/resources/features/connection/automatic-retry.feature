@ADR-0001 @AC02 @AC03
Feature: Automatic connection retry budget

  Scenario: Automatic connection stops after three failed attempts
    Dato un adattatore salvato con i prerequisiti di collegamento soddisfatti,
    e tutti i tentativi di collegamento destinati a fallire, quando l'app viene
    aperta vengono eseguiti esattamente tre tentativi automatici. Ogni nuovo
    tentativo inizia tre secondi dopo il fallimento precedente; poi
    l'autoconnessione viene sospesa e non vengono avviati altri tentativi automatici.

    Given a saved adapter with connection prerequisites satisfied
    And every connection attempt fails
    When the application opens
    Then exactly three automatic connection attempts are made
    And each retry starts three seconds after the previous failure
    And automatic connection is suspended
    And no further automatic connection attempt is made
