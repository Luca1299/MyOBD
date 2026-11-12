@app-D-0001 @presentation @read-only
Feature: Explanatory presentation of the existing diagnostic interface

  @UI-R01
  Scenario: Session exchanges preserve their original content in labelled columns
    Date voci di sessione con orario, richieste, risposte multilinea ed eventi,
    quando il registro viene presentato, ora, tipo e contenuto sono distinti
    e il testo originale degli scambi resta integro.

    Given session entries include timestamped requests and multiline responses
    When the session register is presented
    Then entry times and explanatory event labels are distinct from their content
    And the original exchange content is preserved

  @UI-R02
  Scenario: Unrecognised entries are preserved without invented metadata
    Date voci con prefisso sconosciuto o senza orario riconoscibile, quando
    il registro viene presentato, il contenuto resta leggibile e non viene
    attribuito un orario inesistente.

    Given session entries include unknown prefixes and missing timestamps
    When the session register is presented
    Then unrecognised content remains available without an invented timestamp

  @UI-R03
  Scenario: The visible register limit is separate from the retained report
    Date più di centoventi voci conservate nella sessione, quando il registro
    viene presentato, sono mostrate le ultime centoventi nell'ordine originale
    e sono spiegati i limiti distinti della vista e del rapporto.

    Given a session contains more than one hundred and twenty retained entries
    When the session register is presented
    Then only the latest one hundred and twenty entries are selected in original order
    And the interface explains the screen and report limits separately

  @UI-P01
  Scenario: A single engine sample preserves labels units and valid zero values
    Dato un campione con i sette parametri gestiti e una velocità pari a zero,
    quando il campione viene presentato, etichette, valori e unità restano
    disponibili senza attribuire lo zero a un dato mancante.

    Given one engine sample contains the seven supported measurements including zero speed
    When the engine sample is presented
    Then every acquired label value and unit remains available
    And zero speed remains a valid measurement

  @UI-C01
  Scenario: Adapter connection does not imply that the vehicle is verified
    Dato un adattatore collegato senza verifica conclusa della centralina,
    quando il collegamento viene presentato, i due stati rimangono distinti
    e la verifica può essere richiesta senza avviare una diagnosi.

    Given the adapter is connected but vehicle verification is incomplete
    When the connection is presented
    Then adapter and vehicle verification states remain distinct
    And vehicle verification remains an explicit action

  @UI-D01
  Scenario: Empty and inconclusive code sections remain distinguishable
    Date una risposta valida senza codici in attesa e una lettura dei codici
    permanenti non conclusiva, quando la diagnosi viene presentata, i due
    esiti rimangono distinti e l'esportazione riguarda la diagnosi conclusa.

    Given an ended diagnosis has empty pending codes and inconclusive permanent codes
    When the diagnosis is presented
    Then empty and inconclusive code results have distinct explanations
    And the ended diagnosis export is distinct from the session export

  @UI-N01
  Scenario: Navigating the four sections does not request an acquisition
    Data una sessione pronta senza nuove richieste manuali, quando vengono
    consultate le quattro schede, la sola navigazione non avvia letture,
    diagnosi o esportazioni.

    Given a ready presentation has received no manual acquisition request
    When all four sections are visited
    Then navigation alone requests no reading diagnosis or export
