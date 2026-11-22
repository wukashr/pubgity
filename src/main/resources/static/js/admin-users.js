(function () {
'use strict';

// ── User list filter ──────────────────────────────────────────────────────

function filterUsers() {
    const q = document.getElementById('userSearch').value.toLowerCase();
    const cards = document.querySelectorAll('.user-card');
    let visible = 0;
    cards.forEach(card => {
        const name  = (card.dataset.username || '').toLowerCase();
        const email = (card.dataset.email    || '').toLowerCase();
        const show  = name.includes(q) || email.includes(q);
        card.style.display = show ? '' : 'none';
        if (show) visible++;
    });
    document.getElementById('noMatchMsg').classList.toggle('hidden', visible > 0 || cards.length === 0);
    document.getElementById('userSearchClear').classList.toggle('hidden', q === '');
}

// ── Per-moderator player filter ───────────────────────────────────────────

function filterPlayers(searchInput) {
    const form        = searchInput.closest('form');
    const playerList  = form.querySelector('[data-player-list]');
    const q           = searchInput.value.toLowerCase();
    const onlyChecked = form.querySelector('[data-only-checked]')?.checked ?? false;
    const items       = playerList.querySelectorAll('[data-player-item]');
    let visible = 0;
    items.forEach(item => {
        const name    = (item.dataset.playerName || '').toLowerCase();
        const checked = item.querySelector('input[type=checkbox]').checked;
        const show    = name.includes(q) && (!onlyChecked || checked);
        item.style.display = show ? '' : 'none';
        if (show) visible++;
    });
    playerList.querySelector('[data-no-match-players]')
              .classList.toggle('hidden', visible > 0 || items.length === 0);
}

// Re-apply the current filter when a checkbox is toggled (keeps "show only checked" live)
function onPlayerCheckChange(checkbox) {
    const textInput = checkbox.closest('form').querySelector('input[type=text][placeholder]');
    if (textInput) filterPlayers(textInput);
}

document.getElementById('userSearch').addEventListener('input', filterUsers);
document.getElementById('userSearchClear').addEventListener('click', () => {
    document.getElementById('userSearch').value = '';
    filterUsers();
});
document.querySelectorAll('[data-player-filter]').forEach(input => {
    input.addEventListener('input', () => filterPlayers(input));
});
document.querySelectorAll('[data-only-checked]').forEach(checkbox => {
    checkbox.addEventListener('change', () => filterPlayers(checkbox.closest('form').querySelector('[data-player-filter]')));
});
document.querySelectorAll('[data-player-item] input[type=checkbox]').forEach(checkbox => {
    checkbox.addEventListener('change', () => onPlayerCheckChange(checkbox));
});
}());
