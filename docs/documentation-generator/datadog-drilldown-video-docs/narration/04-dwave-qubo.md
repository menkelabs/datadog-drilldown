The important story is how DICE and Q U B O meet, not the algebra. DICE, through dice server, captures what the incident text says as structured propositions. R C A, through Datadog, ranks what the telemetry supports as root cause candidates. The Qubo report enricher is the bridge: it takes that ranked list and, when the case is large or tangled enough, asks the solver for a coherent short list that respects which hypotheses rule each other out. The result is attached to the same report as findings qubo and extra recommendations, so anything that displays or chats over the incident sees one combined picture: DICE memory plus a numerically grounded layer on top.

Enabling Q U B O does not turn off proposition extraction on the alert ingest path: ingest to dice server still runs first; the enricher only adds after R C A.

When the instance stays small, the enricher records heuristic only under findings and skips the Python call. When complexity crosses rollover, it runs dice leap poc and merges the solve line into the report.

You do not need mountains of raw telemetry for the solver itself. It sees a compact instance: hypotheses with scores, plus which ones conflict or depend on each other. Big log volume matters for how R C A builds candidates, not for stuffing megabytes into the Q U B O file.

The dice leap poc sample data folder holds small JSON fixtures that simulate policy and payoff, not big data: tier simple stays heuristic; tier complex forces the Q U B O path; twelve candidates with eight coupling edges sits at the heuristic ceiling; thirteen candidates or more than eight edges triggers rollover; some files are tuned so the solver beats a greedy baseline on purpose for tests and demos.

Behind the scenes the solver uses Q U B O form — binary choices with a cost landscape — but operators should think of it as glue between narrative memory and disciplined hypothesis picking. D Wave Leap is optional cloud hybrid; local classical is enough for most demos.
