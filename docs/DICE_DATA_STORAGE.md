# DICE Data Storage and Cleanup

## Where Data is Stored

**dice-server** stores all ingested data **in-memory** using a `ConcurrentHashMap` in the `PropositionRepository` class.

### Storage Structure

```kotlin
// In PropositionRepository.kt
private val storage = ConcurrentHashMap<String, ConcurrentHashMap<String, Proposition>>()
// Structure: contextId -> (propositionId -> Proposition)
```

### Key Points:

1. **In-Memory Only**: Data is stored in RAM, not persisted to disk
2. **No Database**: No JPA/database configuration - pure in-memory storage
3. **Persists Until Server Restart**: Data remains in memory as long as dice-server is running
4. **Context-Based Isolation**: Each `contextId` has its own map of propositions

## Data Persistence Behavior

### During Test Runs:

- **Tests use unique contextIds**: Most tests generate contextIds with timestamps (e.g., `"test-db-pool-${System.currentTimeMillis()}"`)
- **No automatic cleanup**: Data accumulates in memory across test runs
- **Data persists**: If you run multiple tests, all ingested data remains in dice-server memory

### Example:

```kotlin
// Test 1
val contextId1 = "test-1234567890"
diceClient.ingest(contextId1, "doc1", "Some text")  // Stored in memory

// Test 2 (runs later, same dice-server instance)
val contextId2 = "test-1234567891"  
diceClient.ingest(contextId2, "doc2", "Other text")  // Also stored

// Both contextId1 and contextId2 data remain in memory
```

## Cleanup Options

### 1. Delete a Specific Context

```kotlin
// Delete all propositions for a context
diceClient.deleteContext(contextId)
```

**API**: `DELETE /api/v1/contexts/{contextId}/memory`

### 2. Clear All Data

```kotlin
// WARNING: Deletes ALL contexts and propositions!
diceClient.clearAll()
```

**API**: `DELETE /api/v1/contexts`

### 3. Using TestCleanupHelper

```kotlin
import com.example.rca.dice.TestCleanupHelper

@AfterEach
fun cleanup() {
    // Clean up after each test
    TestCleanupHelper.cleanupContext(diceClient, contextId)
}

@AfterAll
fun cleanupAll() {
    // Clean up all test data after test suite
    TestCleanupHelper.cleanupAll(diceClient)
}
```

## Repository Methods Added

The `PropositionRepository` now includes:

- `deleteContext(contextId: String)` - Delete all propositions for a context
- `clearAll()` - Clear all stored data
- `getContextCount()` - Get number of contexts
- `getTotalPropositionCount()` - Get total propositions across all contexts

## API Endpoints Added

- `DELETE /api/v1/contexts/{contextId}/memory` - Delete a context
- `DELETE /api/v1/contexts` - Delete all contexts (WARNING: destructive!)

## Recommendations

### For Test Isolation:

1. **Use unique contextIds** (already done with timestamps)
2. **Clean up after tests** using `@AfterEach`:
   ```kotlin
   @AfterEach
   fun cleanup() {
       TestCleanupHelper.cleanupContext(diceClient, contextId)
   }
   ```

### For Test Suites:

1. **Clean up all data** after test suite completes:
   ```kotlin
   @AfterAll
   fun cleanupAll() {
       TestCleanupHelper.cleanupAll(diceClient)
   }
   ```

### For Production:

- Consider adding persistent storage (database) if data needs to survive server restarts
- Current in-memory storage is fine for development/testing
- For production, you may want to add JPA/Hibernate with a real database

## Current Behavior Summary

✅ **What's Working:**
- Tests use unique contextIds (no collisions)
- Data is isolated by contextId
- Cleanup methods are available

⚠️ **What to Watch:**
- Data accumulates in memory across test runs
- No automatic cleanup between tests
- Memory usage grows with each test run (until server restart)

## Example: Adding Cleanup to Tests

```kotlin
class MyTest {
    private lateinit var diceClient: DiceClient
    private lateinit var contextId: String
    
    @BeforeEach
    fun setup() {
        diceClient = DiceClient(...)
        contextId = "test-${System.currentTimeMillis()}"
    }
    
    @Test
    fun myTest() {
        // Your test code
        diceClient.ingest(contextId, "doc1", "text")
    }
    
    @AfterEach
    fun cleanup() {
        // Clean up this test's data
        TestCleanupHelper.cleanupContext(diceClient, contextId)
    }
}
```
