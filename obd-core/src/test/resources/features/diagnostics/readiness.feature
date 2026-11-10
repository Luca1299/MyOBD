@app-D-0001 @diagnosis @read-only
Feature: Emission monitor readiness

  @DIA-R01
  Scenario Outline: Valid monitor status distinguishes support and completion
    Dato uno stato valido dei monitor dall'ultima cancellazione, quando si
    interpreta un monitor, supporto e completamento determinano l'esito senza
    confondere un controllo incompleto con una funzione non supportata.

    Given valid monitor status since trouble codes were cleared is available
    And a monitor is "<support>" with completion "<completion>"
    When the monitor readiness is decoded
    Then that monitor is reported as "<outcome>"

    Examples:
      | support       | completion     | outcome       |
      | supported     | complete       | completed     |
      | supported     | incomplete     | incomplete    |
      | not supported | not applicable | not supported |

  @DIA-R02
  Scenario: A malformed status response does not produce default monitor states
    Data una risposta di stato incompleta o malformata, quando si interpreta
    la readiness, la lettura risulta fallita e i monitor non ricevono stati
    predefiniti di completamento o mancato supporto.

    Given the monitor status response is incomplete or malformed
    When the monitor readiness is decoded
    Then the readiness reading is reported as failed
    And unsupported monitor states are not invented
    And completed monitor states are not invented

  @DIA-R03
  Scenario: Missing readiness data remains an inconclusive reading
    Data l'assenza di una risposta interpretabile sullo stato dei monitor,
    quando si prepara la readiness, l'esito resta non conclusivo e non indica
    che tutti i monitor siano non supportati.

    Given no interpretable monitor status response is received
    When the readiness result is prepared
    Then the readiness reading remains inconclusive
    And no list of unsupported monitors is fabricated from the missing response

  @DIA-R04
  Scenario: An unrequested engine warning lamp does not complete the monitors
    Data una risposta valida con spia motore non richiesta e un monitor
    supportato incompleto, quando si interpreta lo stato, quel monitor rimane
    incompleto e il risultato resta riferito all'ultima cancellazione dei codici.

    Given valid engine status does not request the engine warning lamp
    And a supported emission monitor is incomplete since trouble codes were cleared
    When the monitor readiness is decoded
    Then that monitor remains incomplete
    And the result retains its scope since trouble codes were cleared
