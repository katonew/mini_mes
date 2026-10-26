(() => {
    const form = document.querySelector('[data-routing-form]');
    if (!form) return;
    const rows = form.querySelector('[data-steps]');
    const template = document.getElementById('routing-step-template');
    const message = form.querySelector('[data-step-message]');
    const max = Number(form.dataset.maxSteps);
    const rowList = () => [...rows.querySelectorAll('[data-step-row]')];
    const orderInput = (row) => row.querySelector('[data-field="stepNo"]');
    const show = (text) => { message.textContent = text; message.hidden = !text; };
    const indexRows = () => {
        const list = rowList();
        list.forEach((row, index) => {
            row.querySelector('[data-row-number]').textContent = String(index + 1);
            row.querySelectorAll('[data-field]').forEach((input) => {
                input.name = `steps[${index}].${input.dataset.field}`;
                input.id = `${input.dataset.field}-${index}`;
                input.closest('[class^="col-"]').querySelector('label').htmlFor = input.id;
            });
            row.querySelector('[data-move-up]').disabled = index === 0;
            row.querySelector('[data-move-down]').disabled = index === list.length - 1;
        });
        form.querySelector('[data-add-step]').disabled = list.length >= max;
    };
    const validate = () => {
        const seen = new Set();
        rowList().forEach((row) => {
            const input = orderInput(row);
            input.setCustomValidity('');
            if (!/^[0-9]+$/.test(input.value) || Number(input.value) < 1 || Number(input.value) > 4294967295) {
                input.setCustomValidity('순서는 1~4294967295 정수여야 합니다.');
            } else if (seen.has(Number(input.value))) {
                input.setCustomValidity('공정 순서는 중복될 수 없습니다.');
            }
            seen.add(Number(input.value));
        });
    };
    form.querySelector('[data-add-step]').addEventListener('click', () => {
        if (rowList().length >= max) return;
        const next = Math.max(0, ...rowList().map(row => Number(orderInput(row).value) || 0)) + 1;
        const fragment = template.content.cloneNode(true);
        fragment.querySelector('[data-field="stepNo"]').value = next <= 4294967295 ? String(next) : '';
        rows.appendChild(fragment); show(''); indexRows(); validate(); rowList().at(-1).querySelector('select').focus();
    });
    rows.addEventListener('click', (event) => {
        const button = event.target.closest('button');
        if (!button) return;
        const row = button.closest('[data-step-row]');
        if (button.hasAttribute('data-remove-step')) {
            if (rowList().length === 1) { show('공정은 1개 이상 필요합니다.'); return; }
            row.remove();
        } else {
            const up = button.hasAttribute('data-move-up');
            const neighbor = up ? row.previousElementSibling : row.nextElementSibling;
            if (!neighbor) return;
            const saved = orderInput(row).value;
            orderInput(row).value = orderInput(neighbor).value; orderInput(neighbor).value = saved;
            if (up) rows.insertBefore(row, neighbor); else rows.insertBefore(neighbor, row);
        }
        show(''); indexRows(); validate();
    });
    rows.addEventListener('input', validate);
    rows.addEventListener('change', (event) => {
        if (event.target.dataset.field !== 'stepNo') return;
        validate();
        if (rowList().every(row => orderInput(row).checkValidity())) {
            rowList().sort((a, b) => Number(orderInput(a).value) - Number(orderInput(b).value)).forEach(row => rows.appendChild(row));
            indexRows();
        }
    });
    form.addEventListener('submit', (event) => {
        validate();
        if (!rowList().length || !form.checkValidity()) { event.preventDefault(); form.reportValidity(); }
    }, true);
    window.addEventListener('pageshow', validate);
    indexRows(); validate();
})();
