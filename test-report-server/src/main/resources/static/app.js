const API = '/api';

function el(id) { return document.getElementById(id); }

function fetchJson(url, opts = {}) {
    return fetch(url, { headers: { 'Content-Type': 'application/json', ...opts.headers }, ...opts }).then(r => {
        if (!r.ok) throw new Error(r.statusText);
        return r.json();
    });
}

function postJson(url, body) {
    return fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
    }).then(r => {
        if (!r.ok) throw new Error(r.statusText);
        return r.json();
    });
}

function showStatus(container, message, type = 'info') {
    const s = el(container) || container;
    if (typeof s === 'string') return;
    s.textContent = message;
    s.className = 'status ' + type;
    s.hidden = false;
}

function renderSummary(data) {
    const div = el('summary');
    if (!data) { div.innerHTML = 'No data.'; return; }
    div.innerHTML = `
        <p><strong>Total:</strong> ${data.total} |
           <span class="passed">Passed: ${data.passed}</span> |
           <span class="failed">Failed: ${data.failed}</span> |
           <span class="error">Errors: ${data.errors}</span></p>
    `;
}

function renderRunSummaries(list) {
    const div = el('runs-list');
    if (!list || !list.length) { div.innerHTML = 'No runs.'; return; }
    div.innerHTML = `
        <table>
            <thead><tr><th>Run ID</th><th>Start</th><th>Total</th><th>Passed</th><th>Failed</th><th>Errors</th></tr></thead>
            <tbody>
                ${list.map(r => `
                    <tr>
                        <td><a href="#" data-run-id="${r.runId}">${r.runId}</a></td>
                        <td>${new Date(r.runStart).toLocaleString()}</td>
                        <td>${r.total}</td>
                        <td class="passed">${r.passed}</td>
                        <td class="failed">${r.failed}</td>
                        <td class="error">${r.errors}</td>
                    </tr>
                `).join('')}
            </tbody>
        </table>
    `;
    div.querySelectorAll('a[data-run-id]').forEach(a => {
        a.addEventListener('click', e => { e.preventDefault(); filterByRunId(a.dataset.runId); });
    });
}

function initLinkButtons() {
    document.body.addEventListener('click', e => {
        const b = e.target.closest('.link-btn[data-id]');
        if (!b) return;
        e.preventDefault();
        fetchRunDetail(Number(b.dataset.id));
    });
}
function fetchRunDetail(id) {
    fetchJson(API + '/runs/' + id).then(r => {
        const div = el('run-results');
        div.innerHTML = '<pre>' + escapeHtml(JSON.stringify({ id: r.id, runId: r.runId, testName: r.testName, scenarioId: r.scenarioId, status: r.status, durationMs: r.durationMs, passed: r.passed, errorMessage: r.errorMessage, actualRootCause: r.actualRootCause }, null, 2)) + '</pre>';
    }).catch(() => { });
}

function filterByRunId(runId) {
    el('filter-runId').value = runId;
    applyFilter();
    loadRunSummaries();
    loadSummary();
    selectRun(runId); // Sync logs and suggestions to this run
}

function renderRunsFiltered(list) {
    const div = el('runs-filtered');
    if (!list || !list.length) { div.innerHTML = 'No runs match.'; return; }
    div.innerHTML = `
        <table>
            <thead><tr><th>ID</th><th>Run</th><th>Test</th><th>Scenario</th><th>Status</th><th>Duration</th><th>Coverage</th></tr></thead>
            <tbody>
                ${list.map(r => `
                    <tr>
                        <td><button type="button" class="link-btn" data-id="${r.id}">${r.id}</button></td>
                        <td>${r.runId}</td>
                        <td>${escapeHtml(r.testName)}</td>
                        <td>${escapeHtml(r.scenarioId || '')}</td>
                        <td class="${r.passed ? 'passed' : 'failed'}">${r.status}</td>
                        <td>${r.durationMs != null ? r.durationMs + 'ms' : '–'}</td>
                        <td>${r.keywordCoverage != null ? (r.keywordCoverage * 100).toFixed(0) + '%' : '–'}</td>
                    </tr>
                `).join('')}
            </tbody>
        </table>
    `;
}

function escapeHtml(s) {
    if (s == null) return '';
    const d = document.createElement('div');
    d.textContent = s;
    return d.innerHTML;
}

function loadSummary() {
    fetchJson(API + '/analysis/summary')
        .then(renderSummary)
        .catch(() => { el('summary').innerHTML = 'Could not load summary.'; });
}

function loadSuggestions(syncToRunId = null) {
    const sel = el('suggestions-select');
    const contentEl = el('suggestions-content');
    if (!sel || !contentEl) return;
    sel.innerHTML = '<option value="">Loading…</option>';
    contentEl.innerHTML = '';
    contentEl.classList.add('loading');

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 10000);

    fetch(API + '/analysis/suggestions', { signal: controller.signal })
        .then(r => {
            if (!r.ok) throw new Error(r.statusText);
            return r.json();
        })
        .then(data => {
            clearTimeout(timeout);
            const all = Array.isArray(data.all) ? data.all : [];
            suggestionsData = all; // Store globally for syncing
            const latest = data.latest;
            sel.innerHTML = '';
            if (!all.length) {
                sel.appendChild(new Option('No analysis files', ''));
                contentEl.innerHTML = '<p class="muted-msg">No run-*-analysis.md or analysis-suggestions-*.md files found. Run tests to generate them.</p>';
                return;
            }
            all.forEach((f, i) => {
                const label = getAnalysisDisplayKey(f) || `file-${i}`;
                sel.appendChild(new Option(label, String(i)));
            });

            // If syncing to a specific run, find matching analysis
            let selectedIdx = 0;
            if (syncToRunId) {
                const matchIdx = findMatchingAnalysisIndex(syncToRunId);
                if (matchIdx >= 0) selectedIdx = matchIdx;
            }

            sel.value = String(selectedIdx);
            try {
                const text = all[selectedIdx]?.content || '';
                renderSuggestionContent(text);
            } catch (e) {
                contentEl.innerHTML = '<p class="error-msg">Failed to render markdown. Check console.</p>';
            }
            sel.onchange = () => {
                const idx = parseInt(sel.value, 10);
                if (!isNaN(idx) && all[idx] && all[idx].content) {
                    try { renderSuggestionContent(all[idx].content); } catch (_) { /* ignore */ }
                }
            };
        })
        .catch((err) => {
            clearTimeout(timeout);
            sel.innerHTML = '<option value="">Failed to load</option>';
            const msg = err.name === 'AbortError' ? 'Request timed out.' : ('Failed to load suggestions: ' + (err.message || 'unknown'));
            contentEl.innerHTML = '<p class="error-msg">' + escapeHtml(msg) + '</p>';
        })
        .finally(() => {
            contentEl.classList.remove('loading');
        });
}

function renderSuggestionContent(md) {
    const content = el('suggestions-content');
    if (!content) return;
    if (!md) { content.innerHTML = ''; return; }
    try {
        if (typeof marked !== 'undefined') {
            const fn = marked.parse || marked;
            const out = typeof fn === 'function' ? fn(md) : String(md);
            content.innerHTML = (typeof out === 'string') ? out : String(md);
        } else {
            const pre = document.createElement('pre');
            pre.textContent = md;
            pre.style.whiteSpace = 'pre-wrap';
            content.innerHTML = '';
            content.appendChild(pre);
        }
    } catch (_) {
        const pre = document.createElement('pre');
        pre.textContent = md;
        pre.style.whiteSpace = 'pre-wrap';
        content.innerHTML = '';
        content.appendChild(pre);
    }
}

function loadRunSummaries() {
    fetchJson(API + '/runs/summaries?limit=20')
        .then(list => {
            runSummariesData = list; // Store globally
            renderRunSummaries(list);
            // Update log select with available runs
            const select = el('log-run-select');
            if (select && list.length > 0) {
                const currentValue = select.value;
                const existing = Array.from(select.options).map(o => o.value);
                list.forEach(r => {
                    if (!existing.includes(r.runId)) {
                        const option = document.createElement('option');
                        option.value = r.runId;
                        option.textContent = r.runId;
                        select.appendChild(option);
                    }
                });
                if (!currentValue && list.length > 0) {
                    select.value = list[0].runId;
                    selectRun(list[0].runId); // Sync both panels to latest run
                }
            }
        })
        .catch(() => { el('runs-list').innerHTML = 'Could not load runs.'; });
}

function applyFilter() {
    const runId = el('filter-runId').value.trim();
    const scenario = el('filter-scenario').value.trim();
    const status = el('filter-status').value;
    const params = new URLSearchParams();
    if (runId) params.set('runId', runId);
    if (scenario) params.set('scenarioId', scenario);
    if (status) params.set('status', status);
    params.set('limit', '50');
    fetchJson(API + '/runs?' + params).then(renderRunsFiltered).catch(() => { });
}

let currentLogPollInterval = null;
let suggestionsData = []; // Store suggestions for syncing
let runSummariesData = []; // Store run summaries for syncing

// Extract timestamp from run ID (format: run-{timestamp}-{hash} or run-{epoch})
function getRunTimestamp(runId) {
    if (runId == null || typeof runId !== 'string') return null;
    const match = String(runId).match(/^run-(\d+)/);
    if (match) {
        const n = match[1];
        return n.length >= 13 ? parseInt(n, 10) : parseInt(n, 10) * 1000;
    }
    return null;
}

// Extract runId from analysis filename.
// New: {runId}-analysis.md (e.g. run-1737701234567-abc-analysis.md). Legacy: no runId.
function getAnalysisRunId(filename) {
    if (filename == null || typeof filename !== 'string') return null;
    const m = String(filename).match(/^(run-\d+(?:-[a-f0-9]+)?)-analysis\.md$/);
    return m ? m[1] : null;
}

// Extract timestamp from analysis filename.
// Legacy: analysis-suggestions-YYYYMMDD-HHmmss.md. New: use runId from {runId}-analysis.md.
function getAnalysisTimestamp(filename) {
    if (filename == null || typeof filename !== 'string') return null;
    const legacy = String(filename).match(/analysis-suggestions-(\d{8})-(\d{6})\.md$/);
    if (legacy) {
        const [_, dateStr, timeStr] = legacy;
        const year = parseInt(dateStr.substring(0, 4), 10);
        const month = parseInt(dateStr.substring(4, 6), 10) - 1;
        const day = parseInt(dateStr.substring(6, 8), 10);
        const hour = parseInt(timeStr.substring(0, 2), 10);
        const min = parseInt(timeStr.substring(2, 4), 10);
        const sec = parseInt(timeStr.substring(4, 6), 10);
        return new Date(year, month, day, hour, min, sec).getTime();
    }
    const runId = getAnalysisRunId(filename);
    return runId ? getRunTimestamp(runId) : null;
}

// Display key for an analysis file: use runId when available so it matches runs/logs.
function getAnalysisDisplayKey(f) {
    const runId = f && f.filename ? getAnalysisRunId(f.filename) : null;
    return runId || (f && f.filename ? f.filename : '');
}

// Find the best matching analysis file for a run.
// Prefer exact runId match (same key as runs/logs); fall back to timestamp-based matching for legacy files.
function findMatchingAnalysisIndex(runId) {
    if (!runId || !suggestionsData.length) return -1;

    const exact = suggestionsData.findIndex((f) => getAnalysisRunId(f.filename) === runId);
    if (exact >= 0) return exact;

    const runTs = getRunTimestamp(runId);
    if (!runTs) return -1;

    let bestIdx = -1;
    let bestDiff = Infinity;

    suggestionsData.forEach((f, i) => {
        const analysisTs = getAnalysisTimestamp(f.filename);
        if (analysisTs) {
            let diff = analysisTs - runTs;
            if (diff >= 0 && diff < bestDiff) {
                bestDiff = diff;
                bestIdx = i;
            }
        }
    });

    if (bestIdx >= 0) return bestIdx;
    suggestionsData.forEach((f, i) => {
        const analysisTs = getAnalysisTimestamp(f.filename);
        if (analysisTs) {
            const diff = Math.abs(analysisTs - runTs);
            if (diff < 5 * 60 * 1000 && diff < bestDiff) {
                bestDiff = diff;
                bestIdx = i;
            }
        }
    });

    return bestIdx;
}

// Sync both panels to show data for a specific run
function selectRun(runId) {
    // Update logs dropdown and start polling
    const logSelect = el('log-run-select');
    if (logSelect) {
        // Add option if not exists
        const exists = Array.from(logSelect.options).some(o => o.value === runId);
        if (!exists) {
            const option = document.createElement('option');
            option.value = runId;
            option.textContent = runId;
            logSelect.appendChild(option);
        }
        logSelect.value = runId;
        startLogPolling(runId);
    }

    // Find and select matching analysis file
    const analysisIdx = findMatchingAnalysisIndex(runId);
    const sel = el('suggestions-select');
    if (sel && analysisIdx >= 0) {
        sel.value = String(analysisIdx);
        if (suggestionsData[analysisIdx] && suggestionsData[analysisIdx].content) {
            renderSuggestionContent(suggestionsData[analysisIdx].content);
        }
    }
}

function addRunToLogSelect(runId) {
    const select = el('log-run-select');
    if (!select) return;
    const option = document.createElement('option');
    option.value = runId;
    option.textContent = runId;
    select.appendChild(option);
    select.value = runId;
    startLogPolling(runId);
}

function startLogPolling(runId) {
    if (currentLogPollInterval) {
        clearInterval(currentLogPollInterval);
    }
    const logContent = el('log-content');
    if (!logContent) return;

    const poll = () => {
        fetchJson(API + '/tests/run/' + encodeURIComponent(runId) + '/log?tailLines=500')
            .then(data => {
                if (data.content) {
                    logContent.textContent = data.content;
                    logContent.scrollTop = logContent.scrollHeight;
                }
            })
            .catch(() => { });
    };
    poll();
    currentLogPollInterval = setInterval(poll, 2000);
}

function stopLogPolling() {
    if (currentLogPollInterval) {
        clearInterval(currentLogPollInterval);
        currentLogPollInterval = null;
    }
}

function pollRunResults(runId, btn) {
    const stop = (msg) => {
        if (btn) btn.disabled = false;
        showStatus('run-status', msg, 'ok');
        loadSummary();
        loadRunSummaries();
        loadSuggestions(runId); // Sync suggestions to this run
        filterByRunId(runId);
        // Keep polling logs for a bit after run ends
        setTimeout(() => {
            if (el('log-run-select')?.value === runId) {
                stopLogPolling();
            }
        }, 10000);
    };
    let attempts = 0;
    const max = 180;
    const t = setInterval(() => {
        attempts++;
        Promise.all([
            fetchJson(API + '/runs?runId=' + encodeURIComponent(runId) + '&limit=5'),
            fetchJson(API + '/tests/run/' + encodeURIComponent(runId) + '/status'),
        ]).then(([runs, status]) => {
            if (runs.length) {
                clearInterval(t);
                stop('Run finished. ' + runs.length + ' result(s).');
                return;
            }
            if (!status.running) {
                clearInterval(t);
                // Check one more time after a short delay in case DB write is still in progress
                setTimeout(() => {
                    fetchJson(API + '/runs?runId=' + encodeURIComponent(runId) + '&limit=5')
                        .then(runs => {
                            if (runs.length) {
                                stop('Run finished. ' + runs.length + ' result(s).');
                            } else {
                                stop('Run process ended. No results in DB yet. Check logs for test execution details.');
                            }
                        })
                        .catch(() => {
                            stop('Run process ended. No results in DB yet. Check logs for test execution details.');
                        });
                }, 3000);
                return;
            }
            if (attempts >= max) {
                clearInterval(t);
                stop('Polling stopped after ' + max + ' attempts.');
            }
        }).catch(() => { });
    }, 2000);
}

function onRunSubmit(e) {
    e.preventDefault();
    const btn = el('run-btn');
    const pattern = el('pattern').value.trim();
    const verbose = el('verbose').checked;
    btn.disabled = true;
    showStatus('run-status', 'Starting…', 'info');
    el('run-results').innerHTML = '';

    postJson(API + '/tests/run', { pattern, verbose })
        .then(res => {
            if (res.status === 'error') {
                showStatus('run-status', 'Error: ' + res.message, 'err');
                btn.disabled = false;
                return;
            }
            showStatus('run-status', 'Started run ' + res.runId + '. Polling for results…', 'info');
            addRunToLogSelect(res.runId);
            pollRunResults(res.runId, btn);
        })
        .catch(err => {
            showStatus('run-status', 'Request failed: ' + err.message, 'err');
            btn.disabled = false;
        });
}

el('run-form')?.addEventListener('submit', onRunSubmit);
el('quick-test-btn')?.addEventListener('click', () => {
    el('pattern').value = 'DiceRcaIntegration';
    el('verbose').checked = false;
    el('run-form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
});
el('apply-filter')?.addEventListener('click', () => {
    applyFilter();
    loadRunSummaries();
    loadSummary();
});
el('refresh-runs')?.addEventListener('click', () => {
    loadRunSummaries();
    loadSummary();
});
el('refresh-summary')?.addEventListener('click', () => {
    loadSummary();
    loadRunSummaries();
});
el('log-run-select')?.addEventListener('change', e => {
    const runId = e.target.value;
    if (runId) {
        startLogPolling(runId);
        // Sync suggestions to same run
        const analysisIdx = findMatchingAnalysisIndex(runId);
        const sel = el('suggestions-select');
        if (sel && analysisIdx >= 0) {
            sel.value = String(analysisIdx);
            if (suggestionsData[analysisIdx] && suggestionsData[analysisIdx].content) {
                renderSuggestionContent(suggestionsData[analysisIdx].content);
            }
        }
    } else {
        stopLogPolling();
        el('log-content').textContent = '';
    }
});
el('clear-logs')?.addEventListener('click', () => {
    el('log-content').textContent = '';
});
el('refresh-suggestions')?.addEventListener('click', loadSuggestions);

function showAdminStatus(message, type = 'ok') {
    const s = el('admin-status');
    if (!s) return;
    s.textContent = message;
    s.className = 'admin-status ' + type;
    s.hidden = false;
}

function adminRefresh() {
    loadSummary();
    loadRunSummaries();
    loadSuggestions();
    applyFilter();
}

el('admin-reset-db')?.addEventListener('click', () => {
    const clearLogs = el('admin-reset-clear-logs')?.checked ?? false;
    const msg = clearLogs
        ? 'Reset DB and delete all log and analysis files? This cannot be undone.'
        : 'Reset DB (delete all test runs)? This cannot be undone.';
    if (!confirm(msg)) return;
    showAdminStatus('Resetting…', 'ok');
    postJson(API + '/admin/reset-db', { clearLogs })
        .then(r => {
            const parts = [`${r.deleted} DB row(s)`];
            if (r.logsDeleted) parts.push(`${r.logsDeleted} log file(s)`);
            if (r.analysisDeleted) parts.push(`${r.analysisDeleted} analysis file(s)`);
            showAdminStatus('Deleted ' + parts.join(', ') + '.', 'ok');
            adminRefresh();
        })
        .catch(err => {
            showAdminStatus('Error: ' + err.message, 'err');
        });
});

el('admin-purge-btn')?.addEventListener('click', () => {
    const input = el('admin-purge-before');
    const beforeVal = input?.value?.trim();
    if (!beforeVal) {
        showAdminStatus('Set a date for "Purge before".', 'err');
        return;
    }
    const before = new Date(beforeVal).toISOString();
    const clearLogs = el('admin-purge-clear-logs')?.checked ?? false;
    const msg = clearLogs
        ? `Purge DB rows and log/analysis files before ${beforeVal}? This cannot be undone.`
        : `Purge DB rows before ${beforeVal}? This cannot be undone.`;
    if (!confirm(msg)) return;
    showAdminStatus('Purging…', 'ok');
    postJson(API + '/admin/purge-before', { before, clearLogs })
        .then(r => {
            const parts = [`${r.deleted} DB row(s)`];
            if (r.logsDeleted) parts.push(`${r.logsDeleted} log file(s)`);
            if (r.analysisDeleted) parts.push(`${r.analysisDeleted} analysis file(s)`);
            showAdminStatus('Purged ' + parts.join(', ') + '.', 'ok');
            adminRefresh();
        })
        .catch(err => {
            showAdminStatus('Error: ' + err.message, 'err');
        });
});

el('admin-clear-logs-btn')?.addEventListener('click', () => {
    const mode = el('admin-clear-logs-mode')?.value ?? 'all';
    const beforeInput = el('admin-clear-logs-before');
    let before = null;
    if (mode === 'before') {
        const v = beforeInput?.value?.trim();
        if (!v) {
            showAdminStatus('Set a date for "Clear logs & analysis before".', 'err');
            return;
        }
        before = new Date(v).toISOString();
    }
    const msg = before
        ? `Delete log and analysis files (last modified before ${beforeInput.value})? This cannot be undone.`
        : 'Delete all log and analysis files? This cannot be undone.';
    if (!confirm(msg)) return;
    showAdminStatus('Clearing logs and analysis…', 'ok');
    postJson(API + '/admin/clear-logs', before != null ? { before } : {})
        .then(r => {
            const parts = [];
            if (r.logsDeleted) parts.push(`${r.logsDeleted} log file(s)`);
            if (r.analysisDeleted) parts.push(`${r.analysisDeleted} analysis file(s)`);
            showAdminStatus(parts.length ? 'Deleted ' + parts.join(', ') + '.' : 'Nothing to delete.', 'ok');
            adminRefresh();
        })
        .catch(err => {
            showAdminStatus('Error: ' + err.message, 'err');
        });
});

el('admin-clear-logs-mode')?.addEventListener('change', () => {
    const row = el('admin-clear-logs-row');
    const mode = el('admin-clear-logs-mode')?.value ?? 'all';
    if (row) row.classList.toggle('show-before', mode === 'before');
});

initLinkButtons();

// Load suggestions first, then runs (so syncing works on initial load)
loadSummary();
loadSuggestions();
// Delay run summaries slightly to ensure suggestions are loaded for syncing
setTimeout(loadRunSummaries, 100);
