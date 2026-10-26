(() => {
    const form = document.querySelector('[data-workorder-form]');
    if (!form) return;
    const save = form.querySelector('[data-workorder-save]');
    const restoreSave = () => { save.disabled = save.dataset.registrationAllowed !== 'true'; };
    window.addEventListener('pageshow', restoreSave);
    restoreSave();
    const panel = document.querySelector('[data-allocation]');
    const quantity = document.getElementById('orderedQty');
    if (!panel || !quantity) return;
    const showAllocation = () => {
        const valid = /^0*[1-9][0-9]{0,11}$/.test(quantity.value);
        panel.querySelector('[data-over-plan]').hidden = true;
        if (!valid) {
            panel.querySelector('[data-expected]').textContent = '유효한 수량 입력 후 표시';
            panel.querySelector('[data-difference]').textContent = '유효한 수량 입력 후 표시';
            return;
        }
        const total = BigInt(panel.dataset.allocated) + BigInt(quantity.value);
        const difference = total - BigInt(panel.dataset.planned);
        panel.querySelector('[data-expected]').textContent = total.toString();
        panel.querySelector('[data-difference]').textContent = (difference > 0n ? '+' : '') + difference.toString();
        panel.querySelector('[data-over-plan]').hidden = difference <= 0n;
    };
    quantity.addEventListener('input', showAllocation);
    window.addEventListener('pageshow', showAllocation);
    showAllocation();
})();
