# Test Execution Reporting

This test reporting system captures comprehensive information during test execution and generates human-readable reports in JSON, Markdown, and HTML formats.

## Features

- **Comprehensive Data Collection**: Captures prior knowledge, alerts, analysis results, verification outcomes, and performance metrics
- **Multiple Report Formats**: Generates JSON (for programmatic access), Markdown (for documentation), and HTML (for viewing in browsers)
- **Automatic Collection**: Easy-to-use extensions for automatic report generation
- **Detailed Failure Analysis**: Shows exactly why tests failed with full context

## Quick Start

### 1. Basic Usage with Extensions

```kotlin
import com.example.rca.dice.*

class MyTest {
    private lateinit var testFramework: DiceKnowledgeTestFramework
    
    @Test
    fun `my integration test`() {
        val contextId = "test-${System.currentTimeMillis()}"
        
        runTestWithReporting(
            testName = "my integration test",
            contextId = contextId
        ) { reportBuilder ->
            // Load prior knowledge
            val priorKnowledge = PriorKnowledge(...)
            val loadResult = testFramework.loadPriorKnowledge(contextId, priorKnowledge)
            reportBuilder.withPriorKnowledge(
                loadResult = loadResult,
                dependenciesCount = priorKnowledge.dependencies.size,
                failurePatternsCount = priorKnowledge.failurePatterns.size
            )
            
            // Simulate alert
            val alert = TestAlert(
                name = "Test Alert",
                message = "Something went wrong"
            )
            val analysisResult = testFramework.simulateAlert(contextId, alert)
            reportBuilder.withAlert(alert.toAlertInfo())
            reportBuilder.withAnalysis(analysisResult)
            
            // Verify
            val expectedRootCause = ExpectedRootCause(
                keywords = listOf("error", "failure"),
                component = "service"
            )
            val verification = testFramework.verifyConclusion(analysisResult, expectedRootCause)
            reportBuilder.withVerification(verification, expectedRootCause)
            
            // Assert
            assertTrue(verification.passed)
        }
    }
    
    @AfterAll
    fun generateReports() {
        globalTestReportCollector.generateReports("test-reports")
    }
}
```

### 2. Manual Usage

```kotlin
@Test
fun `manual test reporting`() {
    val contextId = "test-${System.currentTimeMillis()}"
    val reportBuilder = TestExecutionReportBuilder(
        testName = "manual test reporting",
        testClass = this::class.java.name,
        contextId = contextId,
        diceServerUrl = "http://localhost:8080"
    )
    
    try {
        // Your test code here
        val loadResult = testFramework.loadPriorKnowledge(...)
        reportBuilder.withPriorKnowledge(loadResult, ...)
        
        val analysisResult = testFramework.simulateAlert(...)
        reportBuilder.withAnalysis(analysisResult)
        
        val verification = testFramework.verifyConclusion(...)
        reportBuilder.withVerification(verification, expectedRootCause)
        
        assertTrue(verification.passed)
        
    } catch (e: Exception) {
        reportBuilder.markError(e.message ?: "Error", e.stackTraceToString())
        throw e
    } finally {
        val report = reportBuilder.build()
        globalTestReportCollector.addReport(report)
    }
}
```

## Generating Reports

### After All Tests Complete

Add this to your test class:

```kotlin
import org.junit.jupiter.api.AfterAll

class AllScenariosTest {
    companion object {
        @JvmStatic
        @AfterAll
        fun generateReports() {
            globalTestReportCollector.generateReports("test-reports")
        }
    }
}
```

### Programmatically

```kotlin
val collector = TestReportCollector()
// ... add reports during tests ...
collector.generateReports("output-directory")
```

## Report Formats

### JSON Report
- Machine-readable format
- Contains all test data
- Useful for programmatic analysis
- File: `test-report-YYYYMMDD-HHMMSS.json`

### Markdown Report
- Human-readable documentation format
- Great for sharing in documentation
- Shows summary and detailed test results
- File: `test-report-YYYYMMDD-HHMMSS.md`

### HTML Report
- Interactive browser view
- Color-coded test results
- Easy to navigate
- File: `test-report-YYYYMMDD-HHMMSS.html`

## Report Contents

Each test report includes:

1. **Test Metadata**
   - Test name and class
   - Execution time and duration
   - Status (PASSED/FAILED/ERROR/SKIPPED)

2. **Prior Knowledge**
   - Documents loaded
   - Propositions extracted
   - Architecture, dependencies, failure patterns, etc.

3. **Alert Information**
   - Alert details
   - Metrics and queries used

4. **Analysis Results**
   - Initial assessment
   - Root cause analysis
   - Recommendations
   - Propositions and patterns found

5. **Verification Results**
   - Expected vs actual keywords
   - Keyword coverage percentage
   - Component identification
   - Full root cause text

6. **Performance Metrics**
   - Timing for each phase
   - API call counts
   - Total execution time

7. **Error Information** (if failed)
   - Error message
   - Stack trace

## Example Output

### Markdown Report Summary

```markdown
# Test Execution Report

## Summary
| Metric | Count |
|--------|-------|
| Total Tests | 23 |
| Passed | 14 |
| Failed | 9 |
| Errors | 0 |

## Test Results

### ✅ scenario - database pool exhaustion
**Status:** PASSED
**Duration:** 1234ms

#### Verification
**Status:** ✅ PASSED
**Keyword Coverage:** 100%
**Keywords Found:** connection pool, exhausted, database, timeout
```

### HTML Report
- Opens in any web browser
- Color-coded results (green for passed, red for failed)
- Expandable sections for detailed information
- Easy to share with team

## Integration with CI/CD

Add report generation to your CI pipeline:

```bash
# Run tests
mvn test

# Reports will be generated in test-reports/ directory
# Upload to artifact storage or publish as build artifacts
```

## Customization

### Custom Report Formats

Extend `TestReportGenerator` to add custom formats:

```kotlin
class CustomReportGenerator : TestReportGenerator() {
    fun generateCustomFormat(reports: List<TestExecutionReport>, outputFile: File) {
        // Your custom format here
    }
}
```

### Additional Metadata

Add custom metadata to reports:

```kotlin
reportBuilder.withMetadata("environment", "production")
reportBuilder.withMetadata("version", "1.0.0")
```

## Best Practices

1. **Always generate reports after test runs** - Use `@AfterAll` or test lifecycle hooks
2. **Include timing information** - Helps identify slow tests
3. **Capture all relevant data** - The more context, the better for debugging
4. **Review failed test reports** - Use the detailed information to understand failures
5. **Archive reports** - Keep historical reports for trend analysis

## Troubleshooting

### Reports not generating?
- Ensure `globalTestReportCollector.generateReports()` is called
- Check that reports are being added: `globalTestReportCollector.addReport(report)`
- Verify output directory permissions

### Missing data in reports?
- Ensure you're calling the appropriate `with*` methods on the report builder
- Check that test framework methods are returning expected data

### Large report files?
- Reports can be large if many propositions are extracted
- Consider filtering or summarizing data for very large test suites
- Use JSON format for programmatic processing instead of viewing
