(function () {
    'use strict';

    let formatter;
    let timeZone;
    try {
        timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
        formatter = new Intl.DateTimeFormat('en-GB', {
            timeZone, calendar: 'gregory', numberingSystem: 'latn', hourCycle: 'h23',
            year: 'numeric', month: '2-digit', day: '2-digit',
            hour: '2-digit', minute: '2-digit', second: '2-digit'
        });
    } catch (error) {
        console.warn('Local timestamp formatting unavailable; retaining UTC timestamps.', error);
    }

    function format(raw, dateOnly = false) {
        if (!formatter) return null;
        try {
            if (!raw) throw new TypeError('Timestamp is empty');
            const parts = Object.fromEntries(formatter.formatToParts(new Date(raw)).map(part => [part.type, part.value]));
            const date = `${parts.year}-${parts.month}-${parts.day}`;
            return dateOnly ? date : `${date} ${parts.hour}:${parts.minute}:${parts.second}`;
        } catch (error) {
            console.warn('Cannot format UI timestamp; retaining original display.', raw, error);
            return null;
        }
    }

    function localize(root = document) {
        root.querySelectorAll('time[data-local-time][datetime]').forEach(element => {
            const raw = element.getAttribute('datetime');
            const text = format(raw);
            if (text !== null) {
                element.textContent = element.dataset.localTime === 'date' ? text.slice(0, 10) : text;
                element.title = text;
            }
        });
    }

    function chartLabel(label) {
        const separator = label.indexOf(' - ');
        if (separator < 0) {
            console.warn('Invalid timestamp chart label:', label);
            return label;
        }
        const dateTime = format(label.slice(0, separator));
        return dateTime === null ? label : dateTime + label.slice(separator);
    }

    window.PubgityTimestamps = { format, localize, chartLabel };
    document.addEventListener('DOMContentLoaded', () => localize());
}());
