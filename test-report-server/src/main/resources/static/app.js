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

function loadRunSummaries() {
    fetchJson(API + '/runs/summaries?limit=20')
        .then(list => {
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
                    startLogPolling(list[0].runId);
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
el('apply-filter')?.addEventListener('click', applyFilter);
el('log-run-select')?.addEventListener('change', e => {
    const runId = e.target.value;
    if (runId) {
        startLogPolling(runId);
    } else {
        stopLogPolling();
        el('log-content').textContent = '';
    }
});
el('clear-logs')?.addEventListener('click', () => {
    el('log-content').textContent = '';
});
initLinkButtons();

loadSummary();
loadRunSummaries();
