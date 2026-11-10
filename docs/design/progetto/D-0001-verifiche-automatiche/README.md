# D-0001 — Verifiche automatiche e formattazione

**Stato:** confermato **Area:** progetto **Data:** 2026-11-10

**ADR correlati:** [ADR-0003](../../../adr/0003-metodo-di-sviluppo-e-verifiche.md),
[ADR-0001](../../../adr/0001-collegamento-android-elm327.md) per il solo
comportamento già approvato dello scenario pilota.

**Scenari:** [automatic-retry.feature](../../../../obd-core/src/test/resources/features/connection/automatic-retry.feature).
Scoperta ed esecuzione sono documentate nel
[rapporto di verifica](../../../verification/progetto-D-0001-verifica.md).

Consolidamento documentale autorizzato con `approvo` il 10 novembre 2026.
Conferma separata per il coding ricevuta dal proprietario il 10 novembre 2026,
con incarico di registrarla e implementare l'intero perimetro qui descritto.
Gli scenari della futura diagnosi restano esclusi dall'implementazione.
Architecture Decision Record (ADR) indica il registro delle motivazioni;
Behavior-Driven Development (BDD) indica gli scenari di comportamento.

## Scopo e perimetro

Rendere eseguibili gli scenari Gherkin nel percorso di test esistente e
verificare automaticamente la formattazione di Kotlin e script Gradle.
La capacità riguarda il processo di sviluppo, senza schermate o nuove
funzionalità diagnostiche nell'app.

Sono inclusi configurazione degli strumenti, runner e step dello scenario
pilota, controlli ordinari, documentazione dei comandi provati e un passaggio
distinto di formattazione dei sorgenti esistenti. Restano fuori migrazione
generale dei test JUnit/Robolectric, nuove funzioni OBD (On-Board Diagnostics),
aggiornamento generale dello stack, cambio di firma, pubblicazione di pacchetti,
hook o operazioni Git e introduzione di una pipeline remota.

## Stato iniziale

La build usa Gradle 8.13 e Java 17. Il nucleo e i test locali Android usano
JUnit 4; i test Android comprendono Robolectric. Cucumber e Spotless non sono
configurati. Lo scenario pilota è approvato come testo di design ma non è un
test eseguibile né una prova già superata.

## Comportamento atteso: scenari

1. Lo scenario pilota viene scoperto ed eseguito dai test ordinari di
   `obd-core`, usando Cucumber compatibile con JUnit 4 e con lo stack attuale.
2. Gli step attraversano il coordinatore di connessione reale, con trasporto
   e tempo simulati. Non reimplementano la politica dei tentativi nel test e
   non usano il Bluetooth o la vettura.
3. Il pilota verifica una regola già decisa in ADR-0001: tre tentativi totali,
   tre secondi dal fallimento prima del successivo e nessun quarto tentativo
   automatico, con sospensione dell'automatismo.
4. Gli scenari non definiti, ambigui, pendenti o falliti fanno fallire la
   verifica; la mancata scoperta delle feature non vale come successo.
5. L'esecuzione produce evidenze del numero di scenari scoperti ed eseguiti.
   I test JUnit già presenti continuano a essere scoperti ed eseguiti.
6. Gli step restano nel modulo che verificano. Non si introduce Spring o una
   libreria condivisa di step. Le feature future seguono il modulo della
   capacità interessata; questo incremento esegue il pilota nel nucleo.

## Comportamento atteso: formattazione

1. Spotless esegue ktlint con stile `intellij_idea` e configurazione comune
   ispezionabile. Sono inclusi i sorgenti Kotlin di produzione e di test di
   `app` e `obd-core`, anche sotto directory `java`, oltre agli script Gradle
   Kotlin di root e dei moduli. Sono esclusi file generati, directory di build
   e cache.
2. Le convenzioni di naming Compose sono rispettate senza disattivare
   globalmente i controlli dei nomi. L'ambiente Java 17 resta supportato.
3. Il comando ordinario di verifica include la formattazione; una violazione
   fa fallire il controllo e non corregge silenziosamente i sorgenti.
4. Un comando distinto di applicazione corregge ciò che il formatter può
   sistemare. Le violazioni residue si risolvono esplicitamente; nessuna
   soppressione generale serve a far apparire conforme il codice.
5. Il primo riallineamento dei sorgenti è una modifica di sola formattazione,
   rivedibile separatamente dalla configurazione e dal lavoro sugli scenari.
   Non cambia comportamenti o contratti dell'app.
6. Le versioni degli strumenti sono fissate e verificate come stabili e
   compatibili prima dell'uso; non sono copiate dalla build server di Naedo.
   Non si installano hook Git.

## Criteri di accettazione

| ID | Evidenza richiesta |
| --- | --- |
| V01 | Runner scopre ed esegue almeno lo scenario pilota, con risultato visibile e zero step non definiti o pendenti. |
| V02 | Lo scenario pilota passa sul comportamento esistente; un'asserzione intenzionalmente errata dimostra il fallimento del percorso, poi viene ripristinata senza lasciare modifiche di prova. |
| V03 | Sono preservati scoperta ed esito delle suite esistenti; eventuali variazioni del conteggio sono spiegate, senza usare il totale storico come risultato corrente. |
| V04 | Il controllo di formattazione copre tutti i percorsi dichiarati e segnala una violazione rappresentativa senza correggere il file; dopo la correzione il controllo passa. |
| V05 | La formattazione generale è separabile dalle altre modifiche; le verifiche pertinenti restano positive. |
| V06 | L'invocazione ordinaria aggrega test pertinenti e formattazione, con fallimento propagato se uno dei controlli fallisce. |
| V07 | Compilazione e analisi statica Android restano positive; firma, versioni applicative e comportamento diagnostico non vengono modificati per integrare gli strumenti. |
| V08 | La consegna aggiorna CONTRIBUTING e l'overview sullo stato effettivo e registra comandi, versioni risolte, risultati e limiti. |

L'implementazione dovrà permettere di eseguire dalla root un controllo unico
di test e formattazione; i nomi definitivi dei task saranno documentati dopo
la prova. Compilazione e analisi statica Android restano parte della verifica
completa prevista in `CONTRIBUTING.md`.

## Sequenza dopo la conferma

Registrare il perimetro confermato, verificare le versioni effettive e
integrare il runner sul pilota già scritto. Dimostrare che il controllo rileva
un fallimento, poi riportarlo al verde. Integrare il controllo di stile,
verificarne il fallimento e applicare la formattazione in un passaggio
distinto. Eseguire i controlli pertinenti e aggiornare evidenze e comandi.

La conferma del proprietario autorizza questa sequenza. Esiti e limiti
dell'implementazione sono registrati in `docs/verification/`; il package
resta la specifica del comportamento atteso.

## Punti da chiedere

Nessun punto aperto sul perimetro confermato.
