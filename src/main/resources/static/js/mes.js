(() => {
    const toggle = document.querySelector('.menu-toggle');
    const backdrop = document.querySelector('.sidebar-backdrop');
    const setMenuOpen = (open) => {
        document.body.classList.toggle('menu-open', open);
        toggle?.setAttribute('aria-expanded', String(open));
        if (backdrop) backdrop.hidden = !open;
    };
    toggle?.addEventListener('click', () => setMenuOpen(!document.body.classList.contains('menu-open')));
    backdrop?.addEventListener('click', () => setMenuOpen(false));
    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape' && document.body.classList.contains('menu-open')) {
            setMenuOpen(false);
            toggle?.focus();
        }
    });
    window.matchMedia('(min-width: 992px)').addEventListener('change', (event) => {
        if (event.matches) setMenuOpen(false);
    });
    document.querySelectorAll('[data-save-form]').forEach((form) => {
        form.addEventListener('submit', (event) => {
            if (event.defaultPrevented) return;
            form.querySelector('button[type="submit"]').disabled = true;
        });
    });
    window.addEventListener('pageshow', () => {
        document.querySelectorAll('[data-save-form] button[type="submit"]').forEach((button) => {
            button.disabled = false;
        });
    });
})();
