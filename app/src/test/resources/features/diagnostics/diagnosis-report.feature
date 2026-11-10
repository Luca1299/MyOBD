@app-D-0001 @diagnosis @read-only
Feature: Read-only diagnosis text report

  @DIA-T01
  Scenario: Export preserves the ended acquisition without new vehicle readings
    Data una diagnosi conclusa con contesto e risultati, quando viene esportato
    il rapporto di testo, questo conserva gli stessi dati e tempi, categorie
    dei codici, unità e separazione fra valori attuali e freeze frame senza nuove letture.

    Given an ended diagnosis has captured context, times and section results
    When its text report is exported
    Then the report preserves that acquisition context and results
    And it includes adapter, protocol, acquisition times and the captured fuel annotation
    And trouble code categories and measurement units are preserved
    And current measurements and freeze frame values remain separate
    And no new vehicle reading is performed for export

  @DIA-T02
  Scenario Outline: An ended incomplete diagnosis remains exportable with its limits
    Data una diagnosi conclusa parziale o interrotta, quando viene esportato
    il rapporto, i dati acquisiti restano presenti e gli esiti non conclusivi,
    le sezioni non eseguite e il motivo della conclusione non vengono trasformati in successi.

    Given a diagnosis has ended as "<outcome>"
    And its section results distinguish acquired data, inconclusive readings and unperformed sections
    When its text report is exported
    Then the report preserves acquired data and individual section outcomes
    And it explains the available reason for the incomplete acquisition
    And failed or unperformed sections are not represented as valid empty results

    Examples:
      | outcome     |
      | partial     |
      | interrupted |

  @DIA-T03
  Scenario: Export preserves unknown descriptions and response provenance
    Dati un codice senza descrizione verificata e una centralina di origine
    non identificata, quando si esporta il rapporto, entrambi i limiti restano
    espliciti senza attribuire il codice alla gestione benzina o metano.

    Given a reported code has no verified description
    And the responding control unit has not been identified
    When the diagnosis text report is exported
    Then the original code remains present with its description unavailable
    And the unknown response provenance remains explicit
    And no gasoline or methane control unit attribution is invented

  @DIA-T04
  Scenario Outline: A running acquisition is not exported as an ended diagnosis
    Data una diagnosi ancora in acquisizione o in attesa dell'interruzione,
    quando viene richiesta l'esportazione del tentativo corrente, non si
    produce un rapporto che lo presenti come concluso.

    Given the current diagnosis is "<state>"
    When export of the current acquisition is requested
    Then no ended diagnosis report is produced for that running acquisition
    And export remains available after the acquisition has ended

    Examples:
      | state                |
      | acquiring data       |
      | interruption pending |

  @DIA-T05
  Scenario: The exported demo report remains unmistakably simulated
    Data una diagnosi demo conclusa, quando si esporta il rapporto, questo
    identifica esplicitamente i dati come simulati senza presentarli come
    letture della vettura reale.

    Given an ended diagnosis was acquired in a demo session
    When its text report is exported
    Then the report explicitly identifies the diagnosis data as simulated
    And it does not represent synthetic measurements as readings from the real vehicle
