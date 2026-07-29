(() => {
  'use strict';

  const list = document.querySelector('#feed-list');
  if (!list) return;

  const filter = document.body.dataset.currentFilter || 'all';
  const status = document.querySelector('#feed-status');
  const empty = document.querySelector('#empty-feed');
  const loadMore = document.querySelector('#load-more');
  let latestId = Number(list.dataset.latestId || 0);
  let oldestId = Number(list.dataset.oldestId || 0);
  let loading = false;

  const formatter = new Intl.RelativeTimeFormat('fr', { numeric: 'auto' });

  function relativeTime(value) {
    const timestamp = Date.parse(value);
    if (!Number.isFinite(timestamp)) return '';
    const seconds = Math.round((timestamp - Date.now()) / 1000);
    const absolute = Math.abs(seconds);
    if (absolute < 60) return formatter.format(seconds, 'second');
    const minutes = Math.round(seconds / 60);
    if (Math.abs(minutes) < 60) return formatter.format(minutes, 'minute');
    const hours = Math.round(minutes / 60);
    if (Math.abs(hours) < 24) return formatter.format(hours, 'hour');
    return formatter.format(Math.round(hours / 24), 'day');
  }

  function refreshTimes() {
    document.querySelectorAll('time[datetime]').forEach((node) => {
      node.textContent = relativeTime(node.dateTime);
    });
  }

  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = String(text);
    return node;
  }

  function eventCard(event) {
    const article = element('article', `event-card importance-${Math.max(1, Math.min(3, Number(event.importance || 1)))}`);
    article.dataset.eventId = String(event.id);
    article.dataset.eventType = event.type;

    const avatar = element(event.playerId ? 'a' : 'div', 'event-avatar', event.playerName ? event.classGlyph : event.icon);
    if (event.playerId) {
      avatar.href = `/player?id=${encodeURIComponent(String(event.playerId))}`;
      avatar.setAttribute('aria-label', `Profil de ${event.playerName}`);
    } else {
      avatar.setAttribute('aria-hidden', 'true');
    }
    article.appendChild(avatar);

    const content = element('div', 'event-content');
    const meta = element('div', 'event-meta');
    const kind = element('span', 'event-kind');
    const icon = element('span', '', event.icon);
    icon.setAttribute('aria-hidden', 'true');
    kind.append(icon, document.createTextNode(` ${event.label}`));
    const time = element('time', '', relativeTime(event.happenedAt));
    time.dateTime = event.happenedAt;
    time.title = new Date(event.happenedAt).toLocaleString('fr-FR');
    meta.append(kind, time);
    content.appendChild(meta);
    const title = element('h2');
    if (event.playerId) {
      const link = element('a', 'event-title-link', event.title);
      link.href = `/player?id=${encodeURIComponent(String(event.playerId))}`;
      title.appendChild(link);
    } else {
      title.textContent = event.title;
    }
    content.appendChild(title);
    if (event.detail) content.appendChild(element('p', '', event.detail));

    if (event.playerName) {
      const tags = element('div', 'event-tags');
      tags.appendChild(element('span', '', event.className));
      if (event.guildName) tags.appendChild(element('span', '', event.guildName));
      if (event.itemLevel) tags.appendChild(element('span', '', `Niv. ${event.itemLevel}`));
      if (event.playerId) {
        const profile = element('a', '', 'Voir le profil →');
        profile.href = `/player?id=${encodeURIComponent(String(event.playerId))}`;
        tags.appendChild(profile);
      }
      content.appendChild(tags);
    }

    article.appendChild(content);
    return article;
  }

  function updateDashboard(dashboard) {
    if (!dashboard || !dashboard.stats) return;
    Object.entries(dashboard.stats).forEach(([key, value]) => {
      document.querySelectorAll(`[data-stat="${CSS.escape(key)}"]`).forEach((node) => {
        node.textContent = String(value);
      });
    });

    if (status) {
      status.textContent = dashboard.worker && dashboard.worker.online
        ? 'Mis à jour automatiquement'
        : 'Synchronisation en cours';
    }

    const leaderboard = document.querySelector('#leaderboard');
    if (leaderboard && Array.isArray(dashboard.leaderboard)) {
      leaderboard.replaceChildren(...dashboard.leaderboard.map((leader) => {
        const item = element('li');
        item.appendChild(element('span', 'rank', leader.position));
        const avatar = element('span', 'mini-avatar', leader.classGlyph);
        avatar.setAttribute('aria-hidden', 'true');
        item.appendChild(avatar);
        const identity = element('a', 'leader-name');
        identity.href = `/player?id=${encodeURIComponent(String(leader.id))}`;
        identity.append(element('strong', '', leader.name), element('small', '', leader.className));
        item.append(identity, element('span', 'leader-level', `Niv. ${leader.level}`));
        if (leader.online) {
          const dot = element('span', 'online-indicator');
          dot.title = 'En ligne';
          item.appendChild(dot);
        }
        return item;
      }));
    }
  }

  async function request(params) {
    const response = await fetch(`/api/feed.php?${params.toString()}`, {
      headers: { Accept: 'application/json' },
      cache: 'no-store',
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return response.json();
  }

  async function poll() {
    if (loading) return;
    loading = true;
    try {
      const params = new URLSearchParams({ filter, after: String(latestId), limit: '30' });
      if (latestId === 0) params.delete('after');
      const data = await request(params);
      if (!data.ok) throw new Error(data.error || 'Erreur du fil');
      const events = Array.isArray(data.events) ? data.events : [];
      if (latestId > 0 && events.length) {
        const fragment = document.createDocumentFragment();
        events.slice().reverse().forEach((event) => fragment.appendChild(eventCard(event)));
        list.prepend(fragment);
        latestId = Math.max(latestId, ...events.map((event) => Number(event.id)));
        if (oldestId === 0) oldestId = Math.min(...events.map((event) => Number(event.id)));
        list.dataset.latestId = String(latestId);
        list.dataset.oldestId = String(oldestId);
        if (status) status.textContent = `${events.length} nouvelle${events.length > 1 ? 's' : ''} activité${events.length > 1 ? 's' : ''}`;
      } else if (latestId === 0 && events.length) {
        list.replaceChildren(...events.map(eventCard));
        latestId = Math.max(...events.map((event) => Number(event.id)));
        oldestId = Math.min(...events.map((event) => Number(event.id)));
      }
      if (empty) empty.hidden = list.children.length > 0;
      updateDashboard(data.dashboard);
      refreshTimes();
    } catch (error) {
      if (status) status.textContent = 'Actualisation momentanément indisponible';
    } finally {
      loading = false;
    }
  }

  async function more() {
    if (loading || oldestId <= 0) return;
    loading = true;
    loadMore.disabled = true;
    try {
      const params = new URLSearchParams({ filter, before: String(oldestId), limit: '25' });
      const data = await request(params);
      const events = data.ok && Array.isArray(data.events) ? data.events : [];
      events.forEach((event) => list.appendChild(eventCard(event)));
      if (events.length) {
        oldestId = Math.min(oldestId, ...events.map((event) => Number(event.id)));
        list.dataset.oldestId = String(oldestId);
      }
      if (events.length < 25) loadMore.hidden = true;
      refreshTimes();
    } catch (error) {
      if (status) status.textContent = 'Impossible de charger davantage d’activités';
    } finally {
      loading = false;
      loadMore.disabled = false;
    }
  }

  loadMore?.addEventListener('click', more);
  refreshTimes();
  window.setInterval(refreshTimes, 30_000);
  window.setInterval(poll, 15_000);
})();
