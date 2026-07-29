(() => {
  'use strict';

  const flash = document.querySelector('.admin-flash');
  document.querySelector('[data-dismiss-flash]')?.addEventListener('click', () => {
    flash?.remove();
  });

  document.querySelectorAll('[data-reload-page]').forEach((button) => {
    button.addEventListener('click', () => window.location.reload());
  });

  document.querySelectorAll('[data-fill-value][data-fill-target]').forEach((button) => {
    button.addEventListener('click', () => {
      const target = document.getElementById(button.dataset.fillTarget || '');
      if (!(target instanceof HTMLInputElement)) return;
      target.value = button.dataset.fillValue || '';
      target.focus();
    });
  });

  document.querySelectorAll('form[data-confirm]').forEach((form) => {
    form.addEventListener('submit', (event) => {
      const message = form.dataset.confirm || 'Confirmer cette opération ?';
      if (!window.confirm(message)) event.preventDefault();
    });
  });

  const itemForm = document.querySelector('[data-item-form]');
  if (!itemForm) return;

  const search = itemForm.querySelector('[data-item-search]');
  const results = itemForm.querySelector('[data-item-results]');
  const templateId = itemForm.querySelector('[data-template-id]');
  const selected = itemForm.querySelector('[data-selected-item]');
  const submit = itemForm.querySelector('[data-give-submit]');
  if (!(search instanceof HTMLInputElement)
      || !(results instanceof HTMLElement)
      || !(templateId instanceof HTMLInputElement)
      || !(selected instanceof HTMLElement)
      || !(submit instanceof HTMLButtonElement)) return;

  let timer = 0;
  let requestController = null;

  function resetSelection() {
    templateId.value = '';
    selected.textContent = 'Aucun objet sélectionné.';
    selected.classList.remove('valid');
    submit.disabled = true;
  }

  function state(message) {
    results.replaceChildren();
    const node = document.createElement('p');
    node.className = 'item-results-state';
    node.textContent = message;
    results.appendChild(node);
    results.hidden = false;
  }

  function render(items) {
    results.replaceChildren();
    if (!Array.isArray(items) || items.length === 0) {
      state('Aucun template trouvé.');
      return;
    }

    const fragment = document.createDocumentFragment();
    items.forEach((item) => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = `item-result${item.supported ? '' : ' unsupported'}`;
      button.disabled = !item.supported;

      const name = document.createElement('strong');
      name.textContent = String(item.name || `Template ${item.id}`);
      const meta = document.createElement('small');
      meta.textContent = `#${item.id} · ${item.typeName || 'Type inconnu'} · niv. ${item.level || 1}`;
      const badge = document.createElement('span');
      badge.textContent = item.supported ? 'Compatible' : String(item.reason || 'Protégé');
      button.append(name, meta, badge);

      if (item.supported) {
        button.addEventListener('click', () => {
          templateId.value = String(item.id);
          search.value = String(item.name || `Template ${item.id}`);
          selected.textContent = `Template #${item.id} sélectionné · ${item.typeName || 'objet'}`;
          selected.classList.add('valid');
          submit.disabled = false;
          results.hidden = true;
          submit.focus();
        });
      }
      fragment.appendChild(button);
    });
    results.appendChild(fragment);
    results.hidden = false;
  }

  async function lookup(query) {
    requestController?.abort();
    requestController = new AbortController();
    state('Recherche en cours…');
    try {
      const params = new URLSearchParams({ q: query });
      const response = await fetch(`/admin/api/items?${params.toString()}`, {
        headers: { Accept: 'application/json' },
        cache: 'no-store',
        signal: requestController.signal,
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const payload = await response.json();
      render(payload.ok ? payload.items : []);
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') return;
      state('Recherche momentanément indisponible.');
    }
  }

  search.addEventListener('input', () => {
    resetSelection();
    window.clearTimeout(timer);
    const query = search.value.trim();
    if (query.length < 2 && !/^\d$/.test(query)) {
      results.hidden = true;
      results.replaceChildren();
      return;
    }
    timer = window.setTimeout(() => lookup(query), 220);
  });

  search.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') results.hidden = true;
  });

  document.addEventListener('pointerdown', (event) => {
    if (!itemForm.contains(event.target)) results.hidden = true;
  });
})();
