# Test Result Analysis

Analysis summary
==============
Tests: 1 total, 0 passed, 1 failed, 0 errors.
Findings: 2. Suggestions: 2.

Suggested param adjustments:
  - expected_keywords: Add or allow synonyms: database, exhausted, hikaripool (These terms appear in actual root cause but not in expected keywords....)
  - expected_component / expected_cause_type: Align expected labels with LLM wording, or relax matching rules. (LLM output may use different terms than scenario expectations....)


## Findings

- **MISSING_KEYWORDS**: Certain keywords are often missing in failed verifications.
  - Consider adding or accepting synonyms: database, exhausted, hikaripool.

- **COMPONENT_CAUSE_MISMATCH**: Some failures due to component or cause-type not identified.
  - component_identified=false: 1, cause_type_identified=false: 0. Review expected_component / expected_cause_type vs actual root cause phrasing.

## Param adjustment suggestions

| Param | Current | Suggested | Confidence |
|-------|---------|-----------|------------|
| expected_keywords | — | Add or allow synonyms: database, exhausted, hikaripool | HIGH |
| expected_component / expected_cause_type | — | Align expected labels with LLM wording, or relax matching rules. | MEDIUM |

- **expected_keywords**: These terms appear in actual root cause but not in expected keywords.
- **expected_component / expected_cause_type**: LLM output may use different terms than scenario expectations.
