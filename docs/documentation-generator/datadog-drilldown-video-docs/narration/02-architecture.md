There are four main modules to remember.

First, embabel dice r c a: the analysis engine. It pulls Datadog evidence, scores candidates, and builds the incident report. The Qubo report enricher is the integration point: it sits on that report after ranking, optionally calls the Python solver, and writes findings qubo plus clearer recommendations — without replacing DICE ingest. Alert text still goes to dice server for proposition extraction before R C A runs; Q U B O does not skip that step.

Second, dice server: turns ingested incident text into propositions and answers questions over that memory. The enricher’s output lives on the R C A artifact that operators and tools use together with what DICE already stored from the same incident thread.

Third, test report server: Spring Boot on port eight zero eight one by default. It stores run history in H two, shows summaries and recent runs, and can trigger Maven tests from the browser. It can also list DICE slash Q U B O solver runs from JSON L files or the database.

Fourth, dice leap poc: the Python engine the enricher shells out to when rollover says full optimization is worth it — local classical by default, D Wave Leap optional.

In a typical flow, DICE ingest and R C A plus the enricher stack on one another: memory from text, then telemetry shaped analysis, then optional tightening of the hypothesis set on the returned report.
