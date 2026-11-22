(function () {
'use strict';

function filterParticipants(input) {
    const q = input.value.toLowerCase();
    const rows = document.querySelectorAll('#participants-table tbody tr');
    let visible = 0;
    rows.forEach(row => {
        // player name is in the second cell (index 1)
        const name = (row.children[1]?.textContent || '').toLowerCase();
        const show = !q || name.includes(q);
        row.style.display = show ? '' : 'none';
        if (show) visible++;
    });
    const countEl = document.getElementById('filter-count');
    if (countEl) countEl.textContent = q ? visible + ' / ' + rows.length + ' players' : '';
}

document.getElementById('player-filter')?.addEventListener('input', event => filterParticipants(event.currentTarget));

(function () {
    function parseNumber(text) {
        if (text == null) return NaN;
        text = text.toString().trim();
        if (text === '') return NaN;
        var cleaned = text.replace(/,/g, '').replace(/[^0-9.\-]/g, '');
        if (cleaned === '' || cleaned === '.' || cleaned === '-' || cleaned === '-.') return NaN;
        return parseFloat(cleaned);
    }
    function getCellValue(row, idx) {
        var cell = row.children[idx];
        if (!cell) return '';
        return cell.textContent || cell.innerText || '';
    }
    function updateIndexColumn(tbody) {
        Array.from(tbody.querySelectorAll('tr')).forEach(function (r, i) {
            var first = r.children[0];
            if (first) first.textContent = (i + 1);
        });
    }
    document.addEventListener('DOMContentLoaded', function () {
        var table = document.getElementById('participants-table');
        if (!table) return;
        var thead = table.tHead, tbody = table.tBodies[0];
        if (!thead || !tbody) return;
        var headers = Array.from(thead.querySelectorAll('th'));
        headers.forEach(function (th, idx) {
            if (!th.classList.contains('sortable')) return;
            th.tabIndex = 0;
            th.setAttribute('aria-sort', 'none');
            th.addEventListener('keydown', function (event) {
                if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault();
                    th.click();
                }
            });
            th.addEventListener('click', function () {
                var type = th.getAttribute('data-sort-type') || 'string';
                var currentlyAsc = th.classList.contains('sorted-asc');
                headers.forEach(function (h) {
                    h.classList.remove('sorted-asc', 'sorted-desc', 'sorted');
                    h.setAttribute('aria-sort', 'none');
                });
                th.classList.add('sorted');
                var newAsc = !currentlyAsc;
                th.classList.add(newAsc ? 'sorted-asc' : 'sorted-desc');
                th.setAttribute('aria-sort', newAsc ? 'ascending' : 'descending');
                var rows = Array.from(tbody.querySelectorAll('tr'));
                var mapped = rows.map(function (row, i) {
                    var raw = getCellValue(row, idx);
                    var val;
                    if (type === 'number') {
                        val = parseNumber(raw);
                        if (isNaN(val)) val = -Infinity;
                    } else {
                        val = (raw || '').trim().toLowerCase();
                    }
                    return {row: row, val: val, idx: i};
                });
                mapped.sort(function (a, b) {
                    if (a.val === b.val) return a.idx - b.idx;
                    if (type === 'number') return newAsc ? (a.val - b.val) : (b.val - a.val);
                    return newAsc ? (a.val > b.val ? 1 : -1) : (a.val > b.val ? -1 : 1);
                });
                var frag = document.createDocumentFragment();
                mapped.forEach(function (m) { frag.appendChild(m.row); });
                tbody.appendChild(frag);
                updateIndexColumn(tbody);
            });
        });
    });
})();
}());
