(function () {
'use strict';

// ── Table filter ───────────────────────────────────────────
function getFilterIds(tableId) {
    if (tableId === 'match-jobs-table') return { text: 'match-jobs-filter', status: 'match-jobs-status', retryable: 'retryable-match' };
    if (tableId === 'fork-jobs-table')  return { text: 'fork-jobs-filter',  status: 'fork-jobs-status',  retryable: 'retryable-fork'  };
    return null;
}

function applyFilters(tableId) {
    const ids = getFilterIds(tableId);
    const q        = ids ? (document.getElementById(ids.text)?.value.toLowerCase() ?? '')      : '';
    const status   = ids ? (document.getElementById(ids.status)?.value ?? '')                   : '';
    const retryOnly= ids ? (document.getElementById(ids.retryable)?.checked ?? false)           : false;

    document.getElementById(tableId).querySelectorAll('tbody tr').forEach(row => {
        const textMatch     = !q        || row.textContent.toLowerCase().includes(q);
        const statusMatch   = !status   || row.dataset.status === status;
        const retryMatch    = !retryOnly || row.dataset.retryable === 'true';
        row.style.display   = textMatch && statusMatch && retryMatch ? '' : 'none';
    });
}

// Legacy alias used by players table (no status/retryable controls there)
function filterTable(input, tableId) {
    const q = input.value.toLowerCase();
    document.getElementById(tableId).querySelectorAll('tbody tr').forEach(row => {
        row.style.display = row.textContent.toLowerCase().includes(q) ? '' : 'none';
    });
}

// ── Timestamp column toggle with localStorage ──────────────
const TS_KEY = 'pubgity_show_timestamps';

function toggleTs(tableId, cbId) {
    const checked = document.getElementById(cbId).checked;
    const table   = document.getElementById(tableId);
    table.classList.toggle('ts-hidden', !checked);
    localStorage.setItem(TS_KEY, checked ? '1' : '0');
}

document.addEventListener('DOMContentLoaded', () => {
    const show = localStorage.getItem(TS_KEY) === '1';
    ['match-jobs-table', 'fork-jobs-table'].forEach(id => {
        const table = document.getElementById(id);
        if (table) table.classList.toggle('ts-hidden', !show);
    });
    ['ts-match', 'ts-fork'].forEach(id => {
        const cb = document.getElementById(id);
        if (cb) cb.checked = show;
    });
});

function toggleTruncate(el) {
    el.classList.toggle('expanded');
}

function copyValue(btn) {
    const value = btn.getAttribute('data-copy');
    if (!value) return;

    const flash = () => {
        const original = btn.textContent;
        btn.textContent = '✓';
        setTimeout(() => { btn.textContent = original; }, 1000);
    };

    if (navigator.clipboard && window.isSecureContext) {
        navigator.clipboard.writeText(value).then(flash).catch(() => fallbackCopy(value, flash));
    } else {
        fallbackCopy(value, flash);
    }
}

function fallbackCopy(value, onDone) {
    const ta = document.createElement('textarea');
    ta.value = value;
    ta.style.position = 'fixed';
    ta.style.opacity = '0';
    document.body.appendChild(ta);
    ta.focus();
    ta.select();
    try {
        document.execCommand('copy');
        onDone();
    } finally {
        document.body.removeChild(ta);
    }
}

[
    ['match-jobs', 'match'],
    ['fork-jobs', 'fork']
].forEach(([prefix, suffix]) => {
    const tableId = `${prefix}-table`;
    document.getElementById(`${prefix}-filter`).addEventListener('input', () => applyFilters(tableId));
    document.getElementById(`${prefix}-status`).addEventListener('change', () => applyFilters(tableId));
    document.getElementById(`retryable-${suffix}`).addEventListener('change', () => applyFilters(tableId));
    document.getElementById(`ts-${suffix}`).addEventListener('change', () => toggleTs(tableId, `ts-${suffix}`));
});
document.getElementById('players-filter').addEventListener('input', event => filterTable(event.currentTarget, 'players-table'));
document.querySelectorAll('.truncate-cell').forEach(element => {
    element.addEventListener('click', () => toggleTruncate(element));
});
document.querySelectorAll('.copy-btn').forEach(button => {
    button.addEventListener('click', () => copyValue(button));
});
}());
