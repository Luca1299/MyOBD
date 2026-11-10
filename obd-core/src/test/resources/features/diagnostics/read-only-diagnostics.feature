@app-D-0001 @diagnosis @read-only
Feature: Read-only diagnostic results

  @DIA-C01
  Scenario: A trouble code retains each reported category
    Dato lo stesso codice nelle risposte dei memorizzati, degli errori in attesa
    e dei permanenti, quando si leggono i codici, il risultato conserva tutte
    le appartenenze senza eliminare categorie mediante una deduplicazione globale.

    Given the same trouble code is reported as stored, pending and permanent
    When the trouble code categories are read
    Then the trouble code remains in each reported category

  @DIA-C02
  Scenario: A valid empty category is conclusive only for that category
    Data una risposta valida senza codici memorizzati e una lettura degli
    errori in attesa fallita, quando si raccolgono gli esiti, soltanto la prima
    categoria risulta senza codici; la seconda resta non conclusiva.

    Given a valid stored trouble code response contains no codes
    And the pending trouble code response cannot be read
    When the trouble code categories are read
    Then the stored category reports a valid response without codes
    And the pending category reports an inconclusive reading
    And the result does not claim that the vehicle has no faults

  @DIA-C03
  Scenario Outline: An inconclusive response does not prove lack of support
    Data una lettura che termina senza un risultato interpretabile, quando
    l'esito viene classificato, non viene dichiarata l'assenza di guasti né
    il mancato supporto della funzione sulla sola base di tale esito.

    Given a diagnostic reading returns "<response>"
    When the reading outcome is classified
    Then the reading remains inconclusive
    And the function is not classified as unsupported solely from that outcome
    And the result is not classified as a valid response without codes

    Examples:
      | response           |
      | NO DATA            |
      | no response        |
      | malformed response |

  @DIA-C04
  Scenario: Confirmed lack of support is displayed with its limit
    Data un'evidenza interpretabile che dichiara una funzione non supportata,
    quando l'esito viene classificato, viene esposto quel limite senza farlo
    passare per un elenco di codici vuoto o per un timeout.

    Given valid diagnostic evidence confirms that a requested function is unsupported
    When the reading outcome is classified
    Then the function is classified as unsupported with the available reason
    And the outcome is distinct from a valid response without codes
    And the outcome is distinct from a missing response

  @DIA-C05
  Scenario: The declared confirmed code count is not rebuilt from categories
    Dato un conteggio dei codici confermati dichiarato dalla centralina e
    codici presenti in più categorie, quando si compone il risultato, il
    conteggio dichiarato viene conservato senza sostituirlo con la somma delle liste.

    Given a valid engine status declares a confirmed trouble code count
    And the collected trouble codes belong to more than one category
    When the diagnostic result is assembled
    Then the declared confirmed trouble code count is preserved
    And it is not replaced by the sum of the category sizes

  @DIA-C06
  Scenario: A verified catalogue entry describes the reported code
    Dato un codice e una voce verificata applicabile nel catalogo locale,
    quando si prepara la descrizione, vengono conservati codice, testo
    verificato e provenienza senza aggiungere cause o riparazioni dedotte.

    Given a reported trouble code has an applicable verified local catalogue entry
    And the catalogue entry has an identifiable source and version
    When the trouble code description is prepared
    Then the original trouble code and verified description are preserved
    And the description provenance remains available
    And no inferred repair instruction is added

  @DIA-C07
  Scenario: An unknown description does not hide the trouble code
    Dato un codice privo di descrizione verificata, quando si prepara il
    risultato, il codice resta visibile con la descrizione non disponibile.

    Given a reported trouble code has no verified local description
    When the trouble code description is prepared
    Then the original trouble code remains available
    And its description is explicitly unavailable
    And no description is invented

  @DIA-C08
  Scenario: A manufacturer-specific code is not described by an unrelated entry
    Dato un codice specifico del costruttore senza una definizione verificata
    applicabile alla centralina e una voce generica simile, quando si prepara
    la descrizione, la voce generica non viene usata come sostituto.

    Given a manufacturer-specific trouble code has no verified applicable definition
    And an unrelated generic catalogue entry resembles that code
    When the trouble code description is prepared
    Then the unrelated generic entry is not used
    And the original code is retained with an unavailable description

  @DIA-C09
  Scenario: The diagnostic acquisition stays within read-only vehicle services
    Data una diagnosi con tutte le sezioni previste disponibili, quando si
    acquisiscono i dati, nessun comando cancella informazioni diagnostiche,
    attiva componenti o modifica la configurazione della vettura.

    Given a controlled vehicle supports the planned read-only diagnostic sections
    When the approved diagnostic readers acquire their corresponding data
    Then the vehicle requests remain within the approved read-only services
    And no diagnostic clearing command is sent
    And no actuator control or vehicle coding command is sent

  @DIA-C10
  Scenario: A valid zero measurement remains an acquired value
    Dato un valore attuale valido pari a zero, quando il campione viene
    interpretato, il valore e la sua unità restano disponibili e non diventano
    un'indicazione di parametro mancante.

    Given a supported current parameter returns a valid zero measurement
    When the current parameter sample is decoded
    Then the zero value and its unit are retained
    And that parameter is not classified as unavailable
