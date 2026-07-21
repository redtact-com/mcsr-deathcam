/* DEATHCAM dashboard — vanilla JS, no build step. Data: GET /api/records */
'use strict';

/* ---------- constants ---------- */

const PHASES = [
  { id: 'OVERWORLD',  label: 'OW',        color: '#5da84e' },
  { id: 'NETHER',     label: 'NETHER',    color: '#c23f37' },
  { id: 'BASTION',    label: 'BASTION',   color: '#b28d22' },
  { id: 'FORTRESS',   label: 'FORTRESS',  color: '#aa4837' },
  { id: 'BLIND',      label: 'BLIND',     color: '#9a6dd4' },
  { id: 'STRONGHOLD', label: 'SH',        color: '#1d97a5' },
  { id: 'END',        label: 'END',       color: '#a8933a' },
  { id: 'UNKNOWN',    label: '?',         color: '#6b6a72' },
];
const PHASE_BY_ID = Object.fromEntries(PHASES.map(p => [p.id, p]));
const INK2 = '#b9b7ae', INK3 = '#8a887f', GRID = '#26262c', REC = '#e0584f', STEEL = '#7f9cc0';
const MONO = "'JetBrains Mono', ui-monospace, monospace";

/* ---------- state ---------- */

let records = [];
const state = {
  range: 'all',          // '7' | '30' | '90' | 'all'
  phases: new Set(PHASES.map(p => p.id)),
  cause: '',
  opponent: '',
  sort: 'new',
  table: false,
};
const charts = {};       // canvasId -> Chart
let playerList = [];     // records shown in player prev/next order
let playerIdx = -1;

/* ---------- utils ---------- */

const $ = sel => document.querySelector(sel);
const el = (tag, cls, text) => {
  const n = document.createElement(tag);
  if (cls) n.className = cls;
  if (text !== undefined) n.textContent = text;
  return n;
};
const fmtIgt = ms => {
  if (ms == null) return '—';
  const s = Math.floor(ms / 1000);
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
};
const fmtDate = ms => new Date(ms).toLocaleString(undefined,
  { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
const dayKey = ms => {
  const d = new Date(ms);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};
const weekKey = ms => {
  const d = new Date(ms);
  d.setHours(0, 0, 0, 0);
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7)); // monday
  return dayKey(d.getTime());
};
const causeLabel = c => (c || 'UNKNOWN').replace(/_/g, ' ');

/* ---------- filtering ---------- */

function filtered() {
  const now = Date.now();
  const minTs = state.range === 'all' ? 0 : now - Number(state.range) * 86400000;
  let list = records.filter(r =>
    r.detectedAtMillis >= minTs &&
    state.phases.has(r.phase || 'UNKNOWN') &&
    (!state.cause || r.cause === state.cause) &&
    (!state.opponent || (r.opponentName || '').toLowerCase().includes(state.opponent.toLowerCase()))
  );
  const by = {
    new: (a, b) => b.detectedAtMillis - a.detectedAtMillis,
    old: (a, b) => a.detectedAtMillis - b.detectedAtMillis,
    igt: (a, b) => (a.igtAtDeathMillis ?? 1e15) - (b.igtAtDeathMillis ?? 1e15),
  };
  return list.sort(by[state.sort] || by.new);
}

/* ---------- filter bar ---------- */

function buildFilterBar() {
  const chips = $('#phase-chips');
  for (const p of PHASES) {
    const b = el('button', 'phase-pill is-on', p.label);
    b.style.setProperty('--pc', p.color);
    b.dataset.phase = p.id;
    b.addEventListener('click', () => {
      if (state.phases.has(p.id) && state.phases.size === PHASES.length) {
        state.phases = new Set([p.id]);           // first click = solo
      } else if (state.phases.has(p.id)) {
        state.phases.delete(p.id);
        if (state.phases.size === 0) state.phases = new Set(PHASES.map(x => x.id));
      } else {
        state.phases.add(p.id);
      }
      syncFilterUi(); renderAll();
    });
    chips.appendChild(b);
  }
  $('#range-presets').addEventListener('click', e => {
    const b = e.target.closest('.f-preset');
    if (!b) return;
    state.range = b.dataset.range;
    syncFilterUi(); renderAll();
  });
  $('#cause-select').addEventListener('change', e => { state.cause = e.target.value; renderAll(); });
  $('#opponent-input').addEventListener('input', e => { state.opponent = e.target.value.trim(); renderAll(); });
  $('#sort-select').addEventListener('change', e => { state.sort = e.target.value; renderAll(); });
  $('#clear-filters').addEventListener('click', () => {
    Object.assign(state, { range: 'all', cause: '', opponent: '', sort: 'new' });
    state.phases = new Set(PHASES.map(p => p.id));
    $('#cause-select').value = ''; $('#opponent-input').value = ''; $('#sort-select').value = 'new';
    syncFilterUi(); renderAll();
  });
  for (const id of ['trend-gran', 'igt-bin', 'cause-topn', 'phase-style', 'map-dim']) {
    $('#' + id).addEventListener('change', renderStats);
  }
  $('#table-toggle').addEventListener('click', () => {
    state.table = !state.table;
    $('#table-toggle').setAttribute('aria-pressed', String(state.table));
    renderStats();
  });
}

function syncFilterUi() {
  document.querySelectorAll('.f-preset').forEach(b =>
    b.classList.toggle('is-on', b.dataset.range === state.range));
  document.querySelectorAll('.phase-pill').forEach(b => {
    const on = state.phases.has(b.dataset.phase);
    b.classList.toggle('is-on', on);
    b.classList.toggle('is-off', !on);
  });
}

function fillCauseSelect() {
  const counts = new Map();
  for (const r of records) counts.set(r.cause, (counts.get(r.cause) || 0) + 1);
  const sel = $('#cause-select');
  [...counts.entries()].sort((a, b) => b[1] - a[1]).forEach(([c, n]) => {
    const o = el('option', null, `${causeLabel(c)} (${n})`);
    o.value = c;
    sel.appendChild(o);
  });
}

/* ---------- library ---------- */

function renderLibrary() {
  const list = filtered();
  const grid = $('#card-grid');
  grid.replaceChildren();
  $('#library-empty').hidden = list.length > 0;
  list.forEach((r, i) => {
    const p = PHASE_BY_ID[r.phase] || PHASE_BY_ID.UNKNOWN;
    const card = el('article', 'death-card');
    card.style.setProperty('--pc', p.color);
    card.style.setProperty('--i', i);

    card.appendChild(el('div', 'dc-rail'));

    const top = el('div', 'dc-top');
    top.appendChild(el('span', 'phase-chip', p.label));
    top.appendChild(el('span', 'dc-cause', causeLabel(r.cause)));
    if (r.killer) top.appendChild(el('span', 'dc-killer', '← ' + r.killer));
    card.appendChild(top);

    const igt = el('div', 'dc-igt');
    igt.innerHTML = `${fmtIgt(r.igtAtDeathMillis)}<span class="u"> IGT</span>`;
    card.appendChild(igt);

    const sub = el('div', 'dc-sub');
    sub.appendChild(el('span', null, fmtDate(r.detectedAtMillis)));
    if (r.rankedTag) sub.appendChild(el('span', null, '#' + r.rankedTag));
    if (r.opponentName) sub.appendChild(el('span', null, 'vs ' + r.opponentName));
    if (r.deathX != null) sub.appendChild(el('span', null, `${r.deathX} ${r.deathY} ${r.deathZ}`));
    card.appendChild(sub);

    const foot = el('div', 'dc-foot');
    foot.appendChild(r.clipPath
      ? el('span', 'dc-watch', '▶ WATCH')
      : el('span', 'dc-noclip', 'NO CLIP'));
    if (r.hungerReset) foot.appendChild(el('span', null, 'hunger-reset'));
    if (r.rrfPath) foot.appendChild(el('span', 'dc-rrf', '.rrf ●'));
    card.appendChild(foot);

    card.addEventListener('click', () => openPlayer(list, i));
    grid.appendChild(card);
  });
}

/* ---------- player ---------- */

function openPlayer(list, idx) {
  playerList = list; playerIdx = idx;
  const r = list[idx];
  const p = PHASE_BY_ID[r.phase] || PHASE_BY_ID.UNKNOWN;
  const video = $('#video');

  if (r.clipPath) {
    video.src = `/media/clip/${r.id}`;
    video.style.display = '';
  } else {
    video.removeAttribute('src');
    video.style.display = 'none';
  }
  const isMkv = /\.mkv$/i.test(r.clipPath || '');
  $('#mkv-warning').hidden = !isMkv;

  const chip = $('#t-phase');
  chip.textContent = p.label;
  chip.style.setProperty('--pc', p.color);
  chip.style.background = p.color;
  $('#t-date').textContent = new Date(r.detectedAtMillis).toLocaleString();
  $('#t-cause').textContent = causeLabel(r.cause);
  $('#t-killer').textContent = r.killer ? '← ' + r.killer : '';
  $('#t-raw').textContent = r.rawMessage || '—';
  $('#t-igt').textContent = fmtIgt(r.igtAtDeathMillis)
    + (r.finalIgtMillis ? `  (final ${fmtIgt(r.finalIgtMillis)})` : '');
  $('#t-pos').textContent = r.deathX != null ? `${r.deathX} / ${r.deathY} / ${r.deathZ}` : '—';
  $('#t-match').textContent = r.matchId ? '#' + r.matchId : '—';
  $('#t-vs').textContent = r.opponentName
    ? r.opponentName + (r.opponentElo != null ? ` (${r.opponentElo})` : '') : '—';
  $('#t-world').textContent = r.worldName || '—';
  setCopyable('#t-seed-ow', r.seedOverworld);
  setCopyable('#t-seed-net', r.seedNether);
  setCopyable('#t-seed-end', r.seedEnd);
  $('#t-rrf').textContent = r.rrfPath || '—';

  $('#player').hidden = false;
  if (r.clipPath) video.play().catch(() => {});
}

function setCopyable(sel, value) {
  const d = $(sel);
  d.textContent = value || '—';
  d.onclick = value ? () => {
    navigator.clipboard.writeText(value).then(() => {
      d.classList.add('copied');
      setTimeout(() => d.classList.remove('copied'), 1200);
    });
  } : null;
}

function closePlayer() {
  const v = $('#video');
  v.pause(); v.removeAttribute('src'); v.load();
  $('#player').hidden = true;
}

function bindPlayer() {
  const video = $('#video');
  $('#player-close').addEventListener('click', closePlayer);
  $('#player').addEventListener('click', e => { if (e.target === $('#player')) closePlayer(); });
  $('#speed').addEventListener('change', e => { video.playbackRate = Number(e.target.value); });
  document.querySelectorAll('.transport [data-seek]').forEach(b =>
    b.addEventListener('click', () => { video.currentTime += Number(b.dataset.seek); }));
  document.querySelectorAll('.transport [data-frame]').forEach(b =>
    b.addEventListener('click', () => { video.pause(); video.currentTime += Number(b.dataset.frame) / 30; }));
  $('#prev-death').addEventListener('click', () => step(-1));
  $('#next-death').addEventListener('click', () => step(1));
  const step = d => {
    if (playerIdx + d >= 0 && playerIdx + d < playerList.length) openPlayer(playerList, playerIdx + d);
  };
  document.addEventListener('keydown', e => {
    if ($('#player').hidden) return;
    if (e.key === 'Escape') return closePlayer();
    if (e.target.tagName === 'SELECT' || e.target.tagName === 'INPUT') return;
    switch (e.key) {
      case ' ': e.preventDefault(); video.paused ? video.play() : video.pause(); break;
      case 'ArrowLeft': e.preventDefault(); video.currentTime -= e.shiftKey ? 5 : 1; break;
      case 'ArrowRight': e.preventDefault(); video.currentTime += e.shiftKey ? 5 : 1; break;
      case ',': video.pause(); video.currentTime -= 1 / 30; break;
      case '.': video.pause(); video.currentTime += 1 / 30; break;
      case 'ArrowUp': e.preventDefault(); step(-1); break;
      case 'ArrowDown': e.preventDefault(); step(1); break;
    }
  });
  video.addEventListener('error', () => {
    if (video.getAttribute('src')) $('#mkv-warning').hidden = false;
  });
}

/* ---------- stats ---------- */

function destroyChart(id) {
  if (charts[id]) { charts[id].destroy(); delete charts[id]; }
}

/* draw an in-panel note when a chart has no data to show */
function plotEmpty(canvasId, msg) {
  const canvas = $('#' + canvasId);
  const ctx = canvas.getContext('2d');
  const { width, height } = canvas.parentElement.getBoundingClientRect();
  canvas.width = width * devicePixelRatio;
  canvas.height = height * devicePixelRatio;
  ctx.scale(devicePixelRatio, devicePixelRatio);
  ctx.clearRect(0, 0, width, height);
  ctx.font = `10px ${MONO}`;
  ctx.fillStyle = INK3;
  ctx.textAlign = 'center';
  ctx.letterSpacing = '2px';
  ctx.fillText(msg, width / 2, height / 2);
}

/* selective direct labels at the end of horizontal bars */
const barValuePlugin = {
  id: 'barValues',
  afterDatasetsDraw(chart, _args, opts) {
    if (!opts || !opts.on) return;
    const { ctx } = chart;
    ctx.save();
    ctx.font = `10px ${MONO}`;
    ctx.fillStyle = INK2;
    ctx.textBaseline = 'middle';
    const meta = chart.getDatasetMeta(0);
    chart.data.datasets[0].data.forEach((v, i) => {
      if (!v) return;
      const b = meta.data[i];
      ctx.fillText(String(v), b.x + 6, b.y);
    });
    ctx.restore();
  },
};

function baseOpts() {
  return {
    responsive: true,
    maintainAspectRatio: false,
    animation: { duration: 220 },
    plugins: {
      legend: { display: false },
      tooltip: {
        backgroundColor: '#1d1d24',
        borderColor: 'rgba(255,255,255,0.14)',
        borderWidth: 1,
        titleColor: '#f2f1ee',
        bodyColor: INK2,
        titleFont: { family: MONO, size: 11 },
        bodyFont: { family: MONO, size: 11 },
        padding: 8,
        cornerRadius: 3,
        displayColors: true,
        boxWidth: 8, boxHeight: 8, boxPadding: 3,
      },
    },
  };
}
const axis = extra => Object.assign({
  ticks: { color: INK3, font: { family: MONO, size: 10 } },
  grid: { color: GRID, drawTicks: false },
  border: { color: '#383840' },
}, extra);

function renderStats() {
  const list = filtered();
  $('#stats-empty').hidden = list.length > 0;
  $('#chart-grid').style.display = state.table ? 'none' : '';
  $('#phase-legend').style.display = state.table ? 'none' : '';
  $('#stats-table-wrap').hidden = !state.table;
  renderTiles(list);
  if (state.table) { renderTable(list); return; }
  renderPhaseChart(list);
  renderCauseChart(list);
  renderTrendChart(list);
  renderIgtChart(list);
  renderMapChart(list);
  renderPhaseLegend();
}

function renderTiles(list) {
  const row = $('#tile-row');
  row.replaceChildren();
  const tile = (label, valueHtml, sub) => {
    const t = el('div', 'tile');
    t.appendChild(el('div', 'tile-label', label));
    const v = el('div', 'tile-value');
    v.innerHTML = valueHtml;
    t.appendChild(v);
    if (sub) t.appendChild(el('div', 'tile-sub', sub));
    row.appendChild(t);
  };
  tile('DEATHS', String(list.length),
    `${new Set(list.map(r => r.worldName)).size} matches with a death`);
  const topBy = key => {
    const m = new Map();
    list.forEach(r => { const k = key(r); if (k) m.set(k, (m.get(k) || 0) + 1); });
    return [...m.entries()].sort((a, b) => b[1] - a[1])[0];
  };
  const tp = topBy(r => r.phase);
  if (tp) {
    const p = PHASE_BY_ID[tp[0]] || PHASE_BY_ID.UNKNOWN;
    tile('DEADLIEST PHASE',
      `<span class="phase-chip" style="background:${p.color}">${p.label}</span>`,
      `${tp[1]} deaths (${Math.round(tp[1] / list.length * 100)}%)`);
  } else tile('DEADLIEST PHASE', '—');
  const tc = topBy(r => r.cause);
  tile('TOP CAUSE', tc ? causeLabel(tc[0]) : '—', tc ? `${tc[1]} deaths` : '');
  const igts = list.map(r => r.igtAtDeathMillis).filter(v => v != null).sort((a, b) => a - b);
  tile('MEDIAN IGT AT DEATH',
    igts.length ? `<span class="amber">${fmtIgt(igts[Math.floor(igts.length / 2)])}</span>` : '—',
    `${igts.length} deaths with IGT`);
}

function renderPhaseChart(list) {
  destroyChart('c-phase');
  const style = $('#phase-style').value;
  const counts = PHASES.map(p => list.filter(r => (r.phase || 'UNKNOWN') === p.id).length);
  const keep = PHASES.map((p, i) => ({ p, n: counts[i] })).filter(x => x.n > 0);
  const opts = baseOpts();
  if (style === 'donut') {
    charts['c-phase'] = new Chart($('#c-phase'), {
      type: 'doughnut',
      data: {
        labels: keep.map(x => x.p.label),
        datasets: [{
          data: keep.map(x => x.n),
          backgroundColor: keep.map(x => x.p.color),
          borderColor: '#17171c', borderWidth: 2,   // 2px surface gap
        }],
      },
      options: Object.assign(opts, { cutout: '62%' }),
    });
  } else {
    opts.indexAxis = 'y';
    opts.scales = { x: axis({ beginAtZero: true, ticks: { precision: 0, color: INK3, font: { family: MONO, size: 10 } } }), y: axis({ grid: { display: false } }) };
    opts.plugins.barValues = { on: true };
    opts.layout = { padding: { right: 26 } };
    charts['c-phase'] = new Chart($('#c-phase'), {
      type: 'bar',
      data: {
        labels: keep.map(x => x.p.label),
        datasets: [{
          data: keep.map(x => x.n),
          backgroundColor: keep.map(x => x.p.color),
          maxBarThickness: 22,
          borderRadius: { topRight: 4, bottomRight: 4 },
          borderSkipped: 'left',
        }],
      },
      options: opts,
      plugins: [barValuePlugin],
    });
  }
}

function renderCauseChart(list) {
  destroyChart('c-cause');
  const topN = Number($('#cause-topn').value);
  const m = new Map();
  list.forEach(r => m.set(r.cause, (m.get(r.cause) || 0) + 1));
  let entries = [...m.entries()].sort((a, b) => b[1] - a[1]);
  if (topN && entries.length > topN) {
    const rest = entries.slice(topN).reduce((s, e) => s + e[1], 0);
    entries = entries.slice(0, topN).concat([['OTHER', rest]]);
  }
  const opts = baseOpts();
  opts.indexAxis = 'y';
  opts.scales = { x: axis({ beginAtZero: true, ticks: { precision: 0, color: INK3, font: { family: MONO, size: 10 } } }), y: axis({ grid: { display: false } }) };
  opts.plugins.barValues = { on: true };
  opts.layout = { padding: { right: 26 } };
  charts['c-cause'] = new Chart($('#c-cause'), {
    type: 'bar',
    data: {
      labels: entries.map(e => causeLabel(e[0])),
      datasets: [{
        data: entries.map(e => e[1]),
        backgroundColor: STEEL,
        maxBarThickness: 18,
        borderRadius: { topRight: 4, bottomRight: 4 },
        borderSkipped: 'left',
      }],
    },
    options: opts,
    plugins: [barValuePlugin],
  });
}

function renderTrendChart(list) {
  destroyChart('c-trend');
  if (!list.length) return;
  const gran = $('#trend-gran').value;
  const key = gran === 'week' ? weekKey : dayKey;
  const m = new Map();
  list.forEach(r => { const k = key(r.detectedAtMillis); m.set(k, (m.get(k) || 0) + 1); });
  const keys = [...m.keys()].sort();
  // fill gaps between first and last bucket
  const filledKeys = [], filled = [];
  if (keys.length) {
    const stepMs = gran === 'week' ? 7 * 86400000 : 86400000;
    for (let t = new Date(keys[0]).getTime(); t <= new Date(keys[keys.length - 1]).getTime(); t += stepMs) {
      const k = key(t);
      if (!filledKeys.includes(k)) { filledKeys.push(k); filled.push(m.get(k) || 0); }
    }
  }
  const opts = baseOpts();
  opts.scales = {
    x: axis({ grid: { display: false }, ticks: { maxTicksLimit: 14, color: INK3, font: { family: MONO, size: 10 } } }),
    y: axis({ beginAtZero: true, ticks: { precision: 0, color: INK3, font: { family: MONO, size: 10 } } }),
  };
  charts['c-trend'] = new Chart($('#c-trend'), {
    type: 'line',
    data: {
      labels: filledKeys.map(k => k.slice(5)),
      datasets: [{
        data: filled,
        borderColor: REC,
        borderWidth: 2,
        pointRadius: filled.length > 40 ? 0 : 3,
        pointBackgroundColor: REC,
        tension: 0.25,
        fill: { target: 'origin' },
        backgroundColor: 'rgba(224,88,79,0.09)',
      }],
    },
    options: opts,
  });
}

function renderIgtChart(list) {
  destroyChart('c-igt');
  const withIgt = list.filter(r => r.igtAtDeathMillis != null);
  if (!withIgt.length) { plotEmpty('c-igt', 'NO IGT DATA — filled in after ranked matches (.rrf)'); return; }
  const binSec = Number($('#igt-bin').value);
  const maxBin = Math.min(Math.ceil(Math.max(...withIgt.map(r => r.igtAtDeathMillis)) / 1000 / binSec), 24);
  const labels = [];
  for (let i = 0; i <= maxBin; i++) labels.push(i === maxBin ? `${Math.floor(i * binSec / 60)}m+` : `${Math.floor(i * binSec / 60)}:${String(i * binSec % 60).padStart(2, '0')}`);
  const datasets = PHASES.filter(p => p.id !== 'UNKNOWN').map(p => ({
    label: p.label,
    data: new Array(maxBin + 1).fill(0),
    backgroundColor: p.color,
    borderColor: '#17171c', borderWidth: 1,   // surface gap between stacked segments
    maxBarThickness: 26,
  }));
  withIgt.forEach(r => {
    const bin = Math.min(Math.floor(r.igtAtDeathMillis / 1000 / binSec), maxBin);
    const di = PHASES.findIndex(p => p.id === (r.phase || 'UNKNOWN'));
    if (di >= 0 && di < datasets.length) datasets[di].data[bin]++;
  });
  const opts = baseOpts();
  opts.scales = {
    x: axis({ stacked: true, grid: { display: false }, ticks: { maxTicksLimit: 13, color: INK3, font: { family: MONO, size: 10 } } }),
    y: axis({ stacked: true, beginAtZero: true, ticks: { precision: 0, color: INK3, font: { family: MONO, size: 10 } } }),
  };
  charts['c-igt'] = new Chart($('#c-igt'), {
    type: 'bar',
    data: { labels, datasets: datasets.filter(d => d.data.some(v => v)) },
    options: opts,
  });
}

const MAP_GROUPS = {
  nether: ['NETHER', 'BASTION', 'FORTRESS'],
  surface: ['OVERWORLD', 'BLIND', 'STRONGHOLD'],
  end: ['END'],
};

function renderMapChart(list) {
  destroyChart('c-map');
  const dim = $('#map-dim').value;
  $('#map-note').textContent = `${dim} · x/z`;
  const phases = MAP_GROUPS[dim];
  const pts = list.filter(r => r.deathX != null && phases.includes(r.phase));
  if (!pts.length) { plotEmpty('c-map', 'NO COORDINATES — filled in after ranked matches (.rrf)'); return; }
  const datasets = phases.map(id => {
    const p = PHASE_BY_ID[id];
    return {
      label: p.label,
      data: pts.filter(r => r.phase === id).map(r => ({ x: r.deathX, y: -r.deathZ, r })),
      backgroundColor: p.color + 'd9',
      borderColor: '#17171c', borderWidth: 1,
      pointRadius: 5, pointHoverRadius: 7,
    };
  }).filter(d => d.data.length);
  const opts = baseOpts();
  opts.plugins.legend = datasets.length > 1
    ? { display: true, labels: { color: INK2, font: { family: MONO, size: 10 }, boxWidth: 9, boxHeight: 9 } }
    : { display: false };
  opts.plugins.tooltip.callbacks = {
    title: () => '',
    label: ctx => {
      const r = ctx.raw.r;
      return ` ${causeLabel(r.cause)}${r.killer ? ' ← ' + r.killer : ''}  (${r.deathX}, ${r.deathY}, ${r.deathZ})`;
    },
  };
  opts.scales = {
    x: axis({ title: { display: true, text: 'X', color: INK3, font: { family: MONO, size: 9 } } }),
    y: axis({ title: { display: true, text: '−Z (north ↑)', color: INK3, font: { family: MONO, size: 9 } } }),
  };
  charts['c-map'] = new Chart($('#c-map'), { type: 'scatter', data: { datasets }, options: opts });
}

function renderPhaseLegend() {
  const lg = $('#phase-legend');
  lg.replaceChildren();
  lg.appendChild(el('span', null, 'PHASE:'));
  for (const p of PHASES.filter(p => p.id !== 'UNKNOWN')) {
    const k = el('span', 'key');
    const sw = el('span', 'swatch');
    sw.style.background = p.color;
    k.appendChild(sw);
    k.appendChild(el('span', null, p.id.toLowerCase()));
    lg.appendChild(k);
  }
}

function renderTable(list) {
  const tbody = $('#stats-table tbody');
  tbody.replaceChildren();
  for (const r of list) {
    const tr = el('tr');
    const cells = [
      new Date(r.detectedAtMillis).toLocaleString(),
      r.phase || '—',
      causeLabel(r.cause),
      r.killer || '—',
      fmtIgt(r.igtAtDeathMillis),
      r.deathX != null ? `${r.deathX} ${r.deathY} ${r.deathZ}` : '—',
      r.opponentName || '—',
      r.worldName || '—',
    ];
    cells.forEach(c => tr.appendChild(el('td', null, c)));
    tbody.appendChild(tr);
  }
}

/* ---------- routing ---------- */

function route() {
  if (!$('#player').hidden) closePlayer();
  const stats = location.hash === '#stats';
  $('#view-library').hidden = stats;
  $('#view-stats').hidden = !stats;
  $('#tab-library').classList.toggle('is-active', !stats);
  $('#tab-stats').classList.toggle('is-active', stats);
  renderAll();
}

function renderAll() {
  if (location.hash === '#stats') renderStats();
  else renderLibrary();
  $('#total-badge').textContent = `${filtered().length} / ${records.length} DEATHS ON RECORD`;
}

/* ---------- boot ---------- */

async function boot() {
  buildFilterBar();
  bindPlayer();
  try {
    const res = await fetch('/api/records');
    records = await res.json() || [];
  } catch (e) {
    console.error('failed to load records', e);
    records = [];
  }
  records.forEach(r => { if (!r.phase) r.phase = 'UNKNOWN'; });
  fillCauseSelect();
  window.addEventListener('hashchange', route);
  if (!location.hash) location.hash = '#library';
  route();
}

boot();
