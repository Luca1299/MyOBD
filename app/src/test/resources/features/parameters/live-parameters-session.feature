@app-D-0002 @live-parameters @read-only
Feature: Live engine parameter observation session

  @LIVE-S01
  Scenario: Observation starts manually with the prepared selection
    Data una centralina verificata e nessuna selezione corrente, quando viene
    preparata l'osservazione, tutti i parametri dichiarati disponibili sono
    selezionati ma nessuna osservazione parte automaticamente.
    Quando viene richiesto l'avvio manuale, inizia il primo giro.

    Given the vehicle has been verified on the current connection
    And no other diagnostic operation is active
    And there is no current parameter selection
    When live observation is prepared
    Then all declared available catalog parameters are selected
    And no observation starts automatically
    When manual observation start is requested
    Then one observation is admitted with the prepared selection
    And its first cycle starts immediately

  @LIVE-S02
  Scenario Outline: Missing prerequisites do not create an observation
    Dato un prerequisito mancante, quando viene richiesto l'avvio,
    nessuna osservazione viene ammessa o accodata, il motivo resta
    riconoscibile e l'eventuale risultato precedente rimane invariato.

    Given the observation prerequisite "<prerequisite>" is missing
    And an ended observation may already be retained
    When observation start is requested
    Then no observation is admitted or queued
    And no observation parameter request is sent
    And the missing prerequisite is explained
    And any previously retained observation remains unchanged

    Examples:
      | prerequisite                         |
      | connected adapter                    |
      | verified vehicle                     |
      | nonempty parameter selection         |
      | declared available catalog parameter |
      | exclusive diagnostic access          |

  @LIVE-S03
  Scenario: Repeated starts do not create additional observations
    Data un'osservazione attiva, quando viene richiesto nuovamente l'avvio,
    rimane una sola osservazione senza cicli paralleli o avvii accodati.

    Given a live observation is acquiring parameters
    When observation start is requested repeatedly
    Then the same observation remains active
    And no parallel reading loop is created
    And no additional observation is queued

  @LIVE-S04
  Scenario Outline: Cycle scheduling does not accumulate missed cycles
    Data un'osservazione con periodo nominale controllato, quando un giro
    termina prima, alla scadenza o dopo la scadenza del proprio periodo,
    il successivo parte secondo la regola concordata senza recuperi arretrati.

    Given an observation uses a controlled nominal cycle period
    When a cycle finishes "<completion>"
    Then the next cycle starts "<next_start>"
    And no missed cycle is queued or replayed
    And parameter requests never overlap

    Examples:
      | completion                   | next_start                     |
      | before its nominal deadline  | at its nominal deadline        |
      | at its nominal deadline      | after that cycle has completed |
      | after its nominal deadline   | after that cycle has completed |

  @LIVE-S05
  Scenario Outline: Pause stops new requests and retains exclusive access
    Data un'osservazione nella fase indicata, quando viene richiesta la pausa,
    l'eventuale richiesta pendente termina entro il proprio limite e non ne
    partono altre. La pausa conserva osservazione, dati e accesso esclusivo.

    Given an observation is "<position>"
    When pause is requested
    Then no new parameter request is started
    And the observation becomes paused after any in-flight request ends
    And a valid in-flight response may be retained
    And the same observation retains exclusive diagnostic access

    Examples:
      | position                   |
      | waiting for a response     |
      | between parameter requests |
      | waiting for the next cycle |

  @LIVE-S06
  Scenario: Manual start resumes the paused observation with a new cycle
    Data un'osservazione in pausa con dati acquisiti, quando viene richiesto
    Avvia, riprende la stessa osservazione con un nuovo giro, mantenendo
    contesto e valori precedenti finché vengono riletti.

    Given an observation is paused with acquired parameter values
    When manual observation start is requested
    Then the same observation resumes with a new cycle
    And its captured context remains unchanged
    And previous values retain their original reading times until reread

  @LIVE-S07
  Scenario Outline: Ending an observation keeps a healthy connection
    Data un'osservazione nella fase indicata e un trasporto utilizzabile,
    quando viene richiesto Termina, l'eventuale richiesta pendente si conclude,
    non ne partono altre e i dati restano consultabili con il collegamento aperto.

    Given an observation is "<position>"
    And its transport remains healthy
    When ending the observation is requested
    Then no new parameter request is started
    And the observation ends after any in-flight request ends
    And acquired values remain available
    And the adapter connection remains open
    And exclusive diagnostic access is released

    Examples:
      | position                   |
      | waiting for a response     |
      | waiting for the next cycle |
      | paused                     |

  @LIVE-S08
  Scenario: Ending takes precedence over a pending pause
    Data una pausa richiesta durante una lettura pendente, quando viene
    richiesto Termina e la lettura si conclude, l'osservazione termina
    senza tornare in pausa o inviare altre richieste.

    Given pause has been requested while a parameter response is pending
    When ending the observation is requested
    And the pending request completes
    Then the observation is ended rather than paused
    And no further parameter request is sent

  @LIVE-S09
  Scenario Outline: Observation excludes manual diagnostic operations
    Data un'osservazione nello stato indicato, quando vengono richieste
    diagnosi o altre letture manuali, queste non partono né vengono accodate.
    I risultati già conclusi rimangono consultabili senza nuove letture.

    Given an observation is "<state>"
    When manual diagnosis and other manual readings are requested
    Then those operations are neither started nor queued
    And no diagnostic request is interleaved with the observation
    And ended results remain consultable without new readings

    Examples:
      | state          |
      | acquiring      |
      | pause pending  |
      | paused         |
      | ending pending |

  @LIVE-S10
  Scenario Outline: Real observation survives interface lifecycle changes
    Data un'osservazione reale con trasporto disponibile, quando cambia
    la visibilità o viene ricreata l'interfaccia, la stessa osservazione
    continua nel servizio senza nuovi collegamenti o cicli duplicati.

    Given a real observation is active with a healthy transport
    When "<event>" occurs
    Then the service continues the same observation
    And no additional connection or reading loop is created

    Examples:
      | event                       |
      | another tab is opened       |
      | the interface is recreated  |
      | another application is used |
      | the screen is switched off  |

  @LIVE-S11
  Scenario Outline: Hidden demo observation pauses without automatic resume
    Data un'osservazione demo attiva, quando l'app non è più visibile,
    viene richiesta la pausa secondo la regola della richiesta pendente.
    Tornando all'app, la ripresa richiede Avvia manuale.

    Given a demo observation is acquiring parameters
    When "<event>" makes the application not visible
    Then pause is requested for the demo observation
    And no new parameter request starts after the pending request
    When the application becomes visible again
    Then the demo does not resume automatically
    And resuming requires manual observation start

    Examples:
      | event                       |
      | another application is used |
      | the screen is switched off  |

  @LIVE-S12
  Scenario Outline: Demo navigation does not request background pause
    Data un'osservazione demo attiva, quando si cambia scheda o viene
    ricreata l'interfaccia, la stessa osservazione continua senza
    richiedere la pausa automatica o creare un secondo ciclo.

    Given a demo observation is acquiring parameters
    When "<event>" occurs
    Then the same demo observation remains active
    And background pause is not requested
    And no additional reading loop is created

    Examples:
      | event                      |
      | another tab is opened      |
      | the interface is recreated |

  @LIVE-S13
  Scenario: A connection lost before admission cannot receive a prepared start
    Dato un avvio preparato per un collegamento, quando quel collegamento
    viene perso prima dell'ammissione, l'avvio viene rifiutato anche se
    esiste già un collegamento sostitutivo. Il risultato precedente resta intatto.

    Given observation start was prepared for the current connection
    And an ended observation is retained
    When that connection is lost before admission
    And a replacement connection becomes available
    Then the prepared observation is not admitted on the replacement connection
    And the retained observation remains unchanged
    And no observation parameter request is sent for the rejected start

  @LIVE-S14
  Scenario Outline: Connection loss interrupts an admitted observation
    Data un'osservazione già ammessa nella fase indicata, quando il collegamento
    viene perso, l'osservazione termina interrotta conservando i dati ricevuti.
    La riconnessione non la riprende e serve un nuovo avvio manuale.

    Given an admitted observation is "<position>"
    When its connection is lost
    Then that observation ends as interrupted with the available reason
    And already acquired values remain associated with that observation
    When the connection is restored
    Then no observation resumes automatically
    And a new observation requires manual start

    Examples:
      | position                          |
      | admitted before its first request |
      | waiting for a parameter response  |
      | paused                            |
      | waiting for voluntary ending      |

  @LIVE-S15
  Scenario: Late results cannot update a newer observation
    Dati risultati tardivi di un'osservazione precedente, quando è già stata
    ammessa una nuova osservazione, i risultati tardivi non aggiornano
    quella attiva né sostituiscono un risultato concluso più recente.

    Given results from a previous observation can still arrive
    And a newer observation has been admitted
    When the previous observation publishes a late result
    Then the newer observation remains unchanged
    And no newer retained result is replaced by the late publication
    And no result is transferred to another session mode

  @LIVE-S16
  Scenario Outline: Captured context cannot be changed retroactively
    Data un'osservazione in acquisizione o in pausa, quando vengono richiesti
    cambi di selezione, alimentazione o modalità, il contesto rimane invariato.
    Preparare un'altra alimentazione dopo la conclusione non modifica i dati conclusi.

    Given an observation is "<state>" with captured selection and fuel annotation
    When parameter selection, fuel annotation or session mode changes are requested
    Then the active observation context remains unchanged
    When that observation ends and a different annotation is prepared
    Then its ended result retains the original annotation
    And fuel is not inferred from generic parameter values

    Examples:
      | state     |
      | acquiring |
      | paused    |

  @LIVE-S17
  Scenario: Notification disconnect closes the whole real session
    Data un'osservazione reale con richiesta pendente, quando viene usato
    Scollega dalla notifica, collegamento, servizio e notifica vengono arrestati.
    L'osservazione è interrotta e non parte una riconnessione per lo stop esplicito.

    Given a real observation has a pending parameter request
    When disconnect is requested from the session notification
    Then the connection, service and notification are stopped
    And the observation is interrupted with already acquired values retained
    And no automatic reconnection is scheduled because of that explicit stop

  @LIVE-S18
  Scenario Outline: Ended observations survive service recreation in the same process
    Data un'osservazione conclusa nella modalità indicata, quando il servizio
    viene arrestato e ricreato nello stesso processo, risultato e contesto
    restano consultabili senza riavviare l'acquisizione.

    Given an ended observation is retained in "<mode>" mode
    When its service is stopped and recreated in the same application process
    Then that ended observation remains available in the same mode
    And its captured context and values remain unchanged
    And restoring it does not start an observation

    Examples:
      | mode |
      | real |
      | demo |

  @LIVE-S19
  Scenario: Retention separates observation, diagnosis and session mode
    Dati risultati conclusi reali e demo di diagnosi e osservazione, quando
    una nuova osservazione conclude, sostituisce solo l'osservazione della
    stessa modalità, mantenendo gli altri tre risultati.

    Given ended diagnoses and observations exist in real and demo modes
    When a new observation ends in one mode
    Then only the retained observation in that mode is replaced
    And both retained diagnoses remain unchanged
    And the retained observation in the other mode remains unchanged

  @LIVE-S20
  Scenario: Process termination does not create persistent observation history
    Dati risultati conservati nella memoria del processo, quando il processo
    termina e l'app viene riaperta, i risultati non vengono recuperati da
    uno storico su disco e nessuna osservazione viene ripresa.

    Given observation results are retained only in application process memory
    When that process ends and the application is opened in a new process
    Then no observation result is restored from persistent history
    And no previous observation is resumed
