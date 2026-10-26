(() => {
    const form = document.querySelector('[data-bom-form]');
    if (!form) return;
    const rows = form.querySelector('[data-components]');
    const template = document.getElementById('component-template');
    const message = form.querySelector('[data-component-message]');
    const maxComponents = Number(form.dataset.maxComponents);
    const rowList = () => [...rows.querySelectorAll('[data-component-row]')];
    const showMessage = (text) => { message.textContent = text; message.hidden = !text; };
    const renumber = () => {
        rowList().forEach((row, index) => {
            row.querySelector('[data-row-number]').textContent = String(index + 1);
            row.querySelectorAll('[data-field]').forEach((input) => {
                input.name = `components[${index}].${input.dataset.field}`;
            });
            const material = row.querySelector('[data-material-display]') || row.querySelector('select');
            material.id = `material-${index}`;
            const quantity = row.querySelector('[data-field="requiredQty"]');
            quantity.id = `quantity-${index}`;
            const labels = row.querySelectorAll('label');
            labels[0].htmlFor = material.id;
            labels[1].htmlFor = quantity.id;
        });
        form.querySelector('[data-add-component]').disabled = rowList().length >= maxComponents;
    };
    const validate = () => {
        const seen = new Set();
        rowList().forEach((row) => {
            const material = row.querySelector('[data-field="materialItemId"]');
            const quantity = row.querySelector('[data-field="requiredQty"]');
            material.setCustomValidity('');
            quantity.setCustomValidity('');
            if (material.value && seen.has(material.value)) {
                // hidden 기존 구성은 브라우저 검증 대상이 아니므로 보이는 수량 입력에 표시한다.
                quantity.setCustomValidity('같은 원자재를 중복 등록할 수 없습니다.');
                return;
            }
            seen.add(material.value);
            const unit = material.tagName === 'SELECT' ? material.selectedOptions[0]?.dataset.unit : material.dataset.unit;
            const text = quantity.value;
            if (!/^[0-9]+(\.[0-9]{1,6})?$/.test(text)) {
                quantity.setCustomValidity('정수 또는 소수 6자리 이내의 수량을 입력해 주세요.');
                return;
            }
            const [whole, fraction = ''] = text.split('.');
            const integer = whole.replace(/^0+/, '');
            if (integer.length > 12 || !/[1-9]/.test(text)) {
                quantity.setCustomValidity('소요량은 0보다 크고 정수부 12자리 이내여야 합니다.');
            } else if (unit === 'EA' && /[1-9]/.test(fraction)) {
                quantity.setCustomValidity('EA 단위의 소요량은 정수여야 합니다.');
            }
        });
    };
    form.querySelector('[data-add-component]').addEventListener('click', () => {
        if (rowList().length >= maxComponents) return;
        rows.appendChild(template.content.cloneNode(true));
        showMessage(''); renumber(); validate();
        rowList().at(-1).querySelector('select').focus();
    });
    rows.addEventListener('click', (event) => {
        const button = event.target.closest('[data-remove-component]');
        if (!button) return;
        if (rowList().length <= 1) { showMessage('구성 원자재는 1개 이상 필요합니다.'); return; }
        button.closest('[data-component-row]').remove();
        showMessage(''); renumber(); validate();
    });
    rows.addEventListener('input', validate);
    rows.addEventListener('change', validate);
    form.addEventListener('submit', (event) => {
        validate();
        if (!rowList().length || !form.checkValidity()) {
            event.preventDefault();
            if (!rowList().length) showMessage('구성 원자재는 1개 이상 필요합니다.');
            form.reportValidity();
        }
    }, true);
    window.addEventListener('pageshow', validate);
    renumber(); validate();
})();
