Continuous integration runs Maven test across the Java reactor: embabel dice r c a, dice server, and test report server. That is the java modules workflow.

The R C A module holds most tests: unit style checks, Q U B O and Python bridge integration tests, Dice scenario tests, and heavier integration tests that may require DICE server U R L and API keys.

Test report server adds J Unit for solver run plumbing and Playwright end to end tests on port one eight zero eight one in C I. They assert the main headings, summary and runs panels, the solver runs table, and key A P I routes including actuator health.

Python dice leap poc runs pytest on three dot ten in its own workflow. Leap marked tests are optional when D W A V E A P I token is not available.

The Test Report U I reads the same H two file that reporting tests write under embabel dice r c a test reports. From the Run tests panel you can pass a surefire pattern and optional verbose flag; the server invokes run tests with server shell and tags rows with test run id.

Solver history can sync from dice leap poc runs JSON L into H two so operators see quantum experiments next to classic test history.
