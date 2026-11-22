document.querySelectorAll('tr[data-href]').forEach(row => {
    row.addEventListener('click', event => {
        if (!event.target.closest('a, button')) window.location = row.dataset.href;
    });
});
