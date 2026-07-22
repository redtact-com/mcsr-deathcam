/* DEATHCAM dashboard — vanilla JS, no build step. Data: GET /api/records */
'use strict';

/* ---------- constants ---------- */

const PHASES = [
  { id: 'OVERWORLD',  label: 'OW',        ja: '地上',       color: '#5da84e' },
  { id: 'NETHER',     label: 'NETHER',    ja: 'ネザー',     color: '#c23f37' },
  { id: 'BASTION',    label: 'BASTION',   ja: '砦',         color: '#b28d22' },
  { id: 'FORTRESS',   label: 'FORTRESS',  ja: '要塞',       color: '#aa4837' },
  { id: 'BLIND',      label: 'BLIND',     ja: 'ブラインド', color: '#9a6dd4' },
  { id: 'STRONGHOLD', label: 'SH',        ja: 'SH',         color: '#1d97a5' },
  { id: 'END',        label: 'END',       ja: 'エンド',     color: '#a8933a' },
  { id: 'UNKNOWN',    label: '?',         ja: '不明',       color: '#6b6a72' },
];
const PHASE_BY_ID = Object.fromEntries(PHASES.map(p => [p.id, p]));
const INK2 = '#b9b7ae', INK3 = '#8a887f', GRID = '#26262c', REC = '#e0584f', STEEL = '#7f9cc0';
const MONO = "'JetBrains Mono', ui-monospace, monospace";

/* ---------- i18n ---------- */

const I18N = {
  ja: {
    tab_library: 'ライブラリ', tab_stats: '統計',
    r7: '7日', r30: '30日', r90: '90日', rall: '全期間',
    all_causes: '全死因', opponent_ph: '対戦相手…',
    sort_new: '新しい順', sort_old: '古い順', sort_igt: 'IGT 昇順',
    same_phase: '同フェーズ', same_cause: '同死因', reset: 'リセット',
    empty_code: '記録なし', empty_lib: 'この条件の死亡記録はありません。<br>フィルタをゆるめる — か、死んでこよう。',
    empty_stats: '集計できるデータがありません。',
    ctl_trend: '推移', ctl_igtbin: 'IGT 幅', ctl_causes: '死因', ctl_phaseview: 'フェーズ表示', ctl_map: 'マップ',
    table_view: '表で見る',
    opt_day: '日', opt_week: '週', opt_top6: '上位6', opt_top10: '上位10', opt_all: '全て',
    opt_bar: '棒', opt_donut: 'ドーナツ', map_nether: 'ネザー', map_surface: '地上', map_end: 'エンド',
    stat_by_phase: 'フェーズ別 死亡', stat_causes: '死因', stat_over_time: '死亡の推移',
    stat_igt: '死亡 IGT', stat_igt_note: 'フェーズ別 積み上げ', stat_map: '死亡マップ',
    th_date: '日時', th_phase: 'フェーズ', th_cause: '死因', th_killer: '相手', th_pos: '座標',
    th_opponent: '対戦相手', th_world: 'ワールド',
    mkv_warn: '⚠ このクリップは <b>.mkv</b> です。再生できないブラウザがあります。OBS → 設定 → 出力 → 録画フォーマットを <b>Fragmented MP4</b> にすると次から再生できます。',
    frame: 'コマ', prev: '前へ', next: '次へ',
    pos: '座標', match: '試合', world: 'ワールド', seed_type: 'シード種別', bastion: 'バスチオン',
    end_towers: 'エンド塔', seed_id: 'シードID', seed_ow: 'シードOW', seed_net: 'シードNET', seed_end: 'シードEND',
    replay: 'リプレイ', clips: 'クリップ',
    hint: 'SPACE 再生 · ←/→ 1秒 · SHIFT+←/→ 5秒 · , / . コマ送り · ↑/↓ 前後',
    deaths_on_record: '件', watch: '再生', no_clip: 'クリップなし', hunger_reset: 'ハンガーリセット',
    vs: 'vs', match_prefix: '試合', map_note: '· x/z', no_igt: 'IGT データなし — ランクマ後(.rrf)に付与',
    no_coords: '座標データなし — ランクマ後(.rrf)に付与', other: 'その他', phase_legend: 'フェーズ:',
    tile_deaths: '死亡数', tile_deadliest: '最多フェーズ', tile_topcause: '最多死因', tile_median: '死亡IGT 中央値',
    matches_with_death: '試合で死亡', deaths_n: '件', deaths_with_igt: '件に IGT',
    ranked: 'ランクマ', private: 'プライベート',
    WIN: '勝ち', LOSS: '負け', DRAW: '引分', FORFEIT: 'リタイア', COMPLETED: '完走',
  },
  en: {
    tab_library: 'LIBRARY', tab_stats: 'STATS',
    r7: '7D', r30: '30D', r90: '90D', rall: 'ALL',
    all_causes: 'ALL CAUSES', opponent_ph: 'opponent…',
    sort_new: 'NEWEST', sort_old: 'OLDEST', sort_igt: 'IGT ASC',
    same_phase: 'SAME PHASE', same_cause: 'SAME CAUSE', reset: 'RESET',
    empty_code: 'NO SIGNAL', empty_lib: "No deaths on record for these filters.<br>Loosen the filters — or go take some damage.",
    empty_stats: 'Nothing to aggregate for these filters.',
    ctl_trend: 'TREND', ctl_igtbin: 'IGT BIN', ctl_causes: 'CAUSES', ctl_phaseview: 'PHASE VIEW', ctl_map: 'MAP',
    table_view: 'TABLE VIEW',
    opt_day: 'DAY', opt_week: 'WEEK', opt_top6: 'TOP 6', opt_top10: 'TOP 10', opt_all: 'ALL',
    opt_bar: 'BAR', opt_donut: 'DONUT', map_nether: 'NETHER', map_surface: 'SURFACE', map_end: 'END',
    stat_by_phase: 'DEATHS BY PHASE', stat_causes: 'DEATH CAUSES', stat_over_time: 'DEATHS OVER TIME',
    stat_igt: 'IGT AT DEATH', stat_igt_note: 'stacked by phase', stat_map: 'DEATH MAP',
    th_date: 'DATE', th_phase: 'PHASE', th_cause: 'CAUSE', th_killer: 'KILLER', th_pos: 'POS',
    th_opponent: 'OPPONENT', th_world: 'WORLD',
    mkv_warn: "⚠ This clip is <b>.mkv</b> — some browsers can't play it. Set OBS → Settings → Output → Recording Format to <b>Fragmented MP4</b> for future clips.",
    frame: 'frame', prev: 'PREV', next: 'NEXT',
    pos: 'POS', match: 'MATCH', world: 'WORLD', seed_type: 'SEED TYPE', bastion: 'BASTION',
    end_towers: 'END TOWERS', seed_id: 'SEED ID', seed_ow: 'SEED OW', seed_net: 'SEED NET', seed_end: 'SEED END',
    replay: 'REPLAY', clips: 'CLIPS',
    hint: 'SPACE play · ←/→ 1s · SHIFT+←/→ 5s · , / . frame · ↑/↓ prev/next',
    deaths_on_record: 'DEATHS', watch: 'WATCH', no_clip: 'NO CLIP', hunger_reset: 'hunger-reset',
    vs: 'vs', match_prefix: 'match', map_note: '· x/z', no_igt: 'NO IGT DATA — filled in after ranked matches (.rrf)',
    no_coords: 'NO COORDINATES — filled in after ranked matches (.rrf)', other: 'OTHER', phase_legend: 'PHASE:',
    tile_deaths: 'DEATHS', tile_deadliest: 'DEADLIEST PHASE', tile_topcause: 'TOP CAUSE', tile_median: 'MEDIAN IGT AT DEATH',
    matches_with_death: 'matches with a death', deaths_n: 'deaths', deaths_with_igt: 'deaths with IGT',
    ranked: 'RANKED', private: 'PRIVATE',
    WIN: 'WIN', LOSS: 'LOSS', DRAW: 'DRAW', FORFEIT: 'FORFEIT', COMPLETED: 'COMPLETED',
  },
};
const CAUSE_JA = {
  SLAIN: '撲殺', SHOT: '射殺', FIREBALLED: '火球', BLOWN_UP: '爆死', INTENTIONAL_GAME_DESIGN: 'ベッド爆発',
  FALL: '落下', LAVA: '溶岩', FIRE: '炎上', MAGMA: 'マグマ', DROWN: '溺死', SUFFOCATE: '窒息', STARVE: '餓死',
  WITHER: 'ウィザー', MAGIC: '魔法', LIGHTNING: '落雷', CACTUS: 'サボテン', BERRY_BUSH: 'ベリー', ANVIL: '金床',
  FALLING_BLOCK: '落下ブロック', VOID: '奈落', KINETIC: '衝突', STING: '蜂', TRIDENT: 'トライデント',
  DRAGON_BREATH: 'ドラゴンブレス', THORNS: '棘', GENERIC: '死亡', UNKNOWN: '不明',
};
let lang = localStorage.getItem('deathcam-lang') || 'ja';
const t = key => (I18N[lang] && I18N[lang][key]) || (I18N.en[key] || key);
const phaseLabel = id => { const p = PHASE_BY_ID[id] || PHASE_BY_ID.UNKNOWN; return lang === 'ja' ? p.ja : p.label; };
const resultLabel = kind => kind ? (I18N[lang][kind] || kind) : '';

/* ---------- state ---------- */

let records = [];
const state = {
  range: 'all',          // '7' | '30' | '90' | 'all'
  phases: new Set(PHASES.map(p => p.id)),
  cause: '',
  opponent: '',
  sort: 'new',
  table: false,
  sideSort: 'new',
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
const causeLabel = c => lang === 'ja'
  ? (CAUSE_JA[c] || CAUSE_JA.UNKNOWN)
  : (c || 'UNKNOWN').replace(/_/g, ' ');

/** Fill all data-i18n / data-i18n-ph / data-i18n-html elements for the current language. */
function applyStaticI18n() {
  document.documentElement.lang = lang;
  document.querySelectorAll('[data-i18n]').forEach(node => {
    const key = node.getAttribute('data-i18n');
    if (node.hasAttribute('data-i18n-html')) {
      node.innerHTML = t(key);
    } else {
      node.textContent = t(key);
    }
  });
  document.querySelectorAll('[data-i18n-ph]').forEach(node => {
    node.setAttribute('placeholder', t(node.getAttribute('data-i18n-ph')));
  });
  const lt = $('#lang-toggle');
  if (lt) lt.textContent = lang === 'ja' ? 'EN' : '日本語';
}

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
    const b = el('button', 'phase-pill is-on', phaseLabel(p.id));
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
  const keep = sel.value;
  while (sel.options.length > 1) sel.remove(1);   // keep the "all causes" option
  [...counts.entries()].sort((a, b) => b[1] - a[1]).forEach(([c, n]) => {
    const o = el('option', null, `${causeLabel(c)} (${n})`);
    o.value = c;
    sel.appendChild(o);
  });
  sel.value = keep;
}

/** Switch UI language, persist it, and re-render everything dynamic. */
function setLang(l) {
  lang = l;
  localStorage.setItem('deathcam-lang', l);
  applyStaticI18n();
  document.querySelectorAll('.phase-pill').forEach(b => { b.textContent = phaseLabel(b.dataset.phase); });
  fillCauseSelect();
  renderAll();
  if (currentRecord && !$('#player').hidden) openPlayer(currentRecord);
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
    top.appendChild(el('span', 'phase-chip', phaseLabel(p.id)));
    top.appendChild(el('span', 'dc-cause', causeLabel(r.cause)));
    if (r.killer) top.appendChild(el('span', 'dc-killer', '← ' + r.killer));
    card.appendChild(top);

    const igt = el('div', 'dc-igt');
    igt.innerHTML = `${fmtIgt(r.igtAtDeathMillis)}<span class="u"> IGT</span>`;
    card.appendChild(igt);

    const sub = el('div', 'dc-sub');
    sub.appendChild(el('span', null, fmtDate(r.detectedAtMillis)));
    if (r.seedType) sub.appendChild(el('span', null, r.seedType + (r.bastionType ? ' / ' + r.bastionType : '')));
    if (r.opponentName) sub.appendChild(el('span', null, t('vs') + ' ' + r.opponentName));
    else if (r.rankedTag) sub.appendChild(el('span', null, '#' + r.rankedTag));
    if (r.deathX != null) sub.appendChild(el('span', null, `${r.deathX} ${r.deathY} ${r.deathZ}`));
    card.appendChild(sub);

    const foot = el('div', 'dc-foot');
    foot.appendChild(r.clipPath
      ? el('span', 'dc-watch', '▶ ' + t('watch'))
      : el('span', 'dc-noclip', t('no_clip')));
    if (r.eloChange != null) {
      const sign = r.eloChange > 0 ? '+' : '';
      const e = el('span', 'dc-elo', `${sign}${r.eloChange} elo`);
      e.style.color = r.eloChange > 0 ? 'var(--ph-overworld)' : r.eloChange < 0 ? 'var(--ph-nether)' : '';
      foot.appendChild(e);
    } else if (r.resultKind) {
      foot.appendChild(el('span', null, resultLabel(r.resultKind)));
    }
    if (r.hungerReset) foot.appendChild(el('span', null, t('hunger_reset')));
    if (r.rrfPath) foot.appendChild(el('span', 'dc-rrf', '.rrf ●'));
    card.appendChild(foot);

    card.addEventListener('click', () => openPlayer(r));
    grid.appendChild(card);
  });
}

/* ---------- player ---------- */

let currentRecord = null;   // the record playing in the watch view

/** Open the watch view on a record and (re)build the clip rail around it. */
function openPlayer(record) {
  currentRecord = record;
  const r = record;
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
  chip.textContent = phaseLabel(p.id);
  chip.style.setProperty('--pc', p.color);
  chip.style.background = p.color;
  $('#t-cause').textContent = causeLabel(r.cause);
  $('#t-killer').textContent = r.killer ? '← ' + r.killer : '';
  $('#t-raw').textContent = r.rawMessage || '—';

  // badges (empty string -> hidden via :empty)
  $('#t-date').textContent = new Date(r.detectedAtMillis).toLocaleString();
  const mt = r.matchType === 2 ? t('ranked') : r.matchType === 3 ? t('private') : '';
  $('#t-result').textContent = r.resultKind ? resultLabel(r.resultKind) + (mt ? ` · ${mt}` : '') : (mt || '');
  const elo = $('#t-elo');
  if (r.eloChange != null) {
    const sign = r.eloChange > 0 ? '+' : '';
    const after = r.eloBefore != null ? `${r.eloBefore} → ${r.eloBefore + r.eloChange} ` : '';
    elo.innerHTML = `ELO ${after}(${sign}${r.eloChange})`;
    elo.style.color = r.eloChange > 0 ? '#5da84e' : r.eloChange < 0 ? '#c23f37' : '';
  } else {
    elo.textContent = '';
    elo.style.color = '';
  }
  $('#t-vs').textContent = r.opponentName
    ? t('vs') + ' ' + r.opponentName + (r.opponentElo != null ? ` (${r.opponentElo})` : '') : '';

  // detail grid
  $('#t-igt').textContent = fmtIgt(r.igtAtDeathMillis)
    + (r.finalRtaMillis ? `  (${t('match_prefix')} ${fmtIgt(r.finalRtaMillis)})` : '');
  $('#t-pos').textContent = r.deathX != null ? `${r.deathX} / ${r.deathY} / ${r.deathZ}` : '—';
  $('#t-match').textContent = r.matchId ? '#' + r.matchId : '—';
  $('#t-world').textContent = r.worldName || '—';
  $('#t-seedtype').textContent = r.seedType || '—';
  $('#t-bastion').textContent = r.bastionType || '—';
  $('#t-towers').textContent = r.endTowers || '—';
  setCopyable('#t-seedid', r.seedId);
  setCopyable('#t-seed-ow', r.seedOverworld);
  setCopyable('#t-seed-net', r.seedNether);
  setCopyable('#t-seed-end', r.seedEnd);
  $('#t-rrf').textContent = r.rrfPath || '—';

  renderSidebar();
  $('#player').hidden = false;
  $('.watch-main').scrollTop = 0;
  if (r.clipPath) video.play().catch(() => {});
}

/** Order the clip rail relative to the current record per the sort selector. */
function sidebarOrder() {
  const list = filtered().slice();
  const cur = currentRecord;
  switch (state.sideSort) {
    case 'old': list.sort((a, b) => a.detectedAtMillis - b.detectedAtMillis); break;
    case 'igt': list.sort((a, b) => (a.igtAtDeathMillis ?? 1e15) - (b.igtAtDeathMillis ?? 1e15)); break;
    case 'phase':
      list.sort((a, b) => sameFirst(a, b, r => r.phase === cur.phase)
        || b.detectedAtMillis - a.detectedAtMillis);
      break;
    case 'cause':
      list.sort((a, b) => sameFirst(a, b, r => r.cause === cur.cause)
        || b.detectedAtMillis - a.detectedAtMillis);
      break;
    default: list.sort((a, b) => b.detectedAtMillis - a.detectedAtMillis);
  }
  return list;
}
const sameFirst = (a, b, pred) => (pred(b) ? 1 : 0) - (pred(a) ? 1 : 0);

function renderSidebar() {
  const list = sidebarOrder();
  playerList = list;
  playerIdx = list.findIndex(r => r.id === currentRecord.id);
  $('#side-count').textContent = `(${list.length})`;
  const box = $('#side-list');
  box.replaceChildren();
  for (const r of list) {
    const p = PHASE_BY_ID[r.phase] || PHASE_BY_ID.UNKNOWN;
    const item = el('div', 'side-item' + (r.id === currentRecord.id ? ' is-current' : ''));

    const thumb = el('div', 'si-thumb');
    thumb.style.setProperty('--pc', p.color);
    thumb.appendChild(el('span', 'si-phase', phaseLabel(p.id)));
    if (r.igtAtDeathMillis != null) thumb.appendChild(el('span', 'si-igt', fmtIgt(r.igtAtDeathMillis)));
    if (!r.clipPath) thumb.appendChild(el('span', 'si-noclip', t('no_clip')));
    item.appendChild(thumb);

    const body = el('div', 'si-body');
    body.appendChild(el('div', 'si-cause', causeLabel(r.cause) + (r.killer ? ' ← ' + r.killer : '')));
    const bits = [fmtDate(r.detectedAtMillis)];
    if (r.seedType) bits.push(r.seedType);
    if (r.opponentName) bits.push(t('vs') + ' ' + r.opponentName);
    if (r.eloChange != null) bits.push((r.eloChange > 0 ? '+' : '') + r.eloChange + ' elo');
    body.appendChild(el('div', 'si-sub', bits.join(' · ')));
    item.appendChild(body);

    item.addEventListener('click', () => openPlayer(r));
    box.appendChild(item);
  }
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
  $('#side-sort').addEventListener('change', e => {
    state.sideSort = e.target.value;
    if (currentRecord) renderSidebar();
  });
  const step = d => {
    if (playerIdx + d >= 0 && playerIdx + d < playerList.length) openPlayer(playerList[playerIdx + d]);
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
  tile(t('tile_deaths'), String(list.length),
    `${new Set(list.map(r => r.worldName)).size} ${t('matches_with_death')}`);
  const topBy = key => {
    const m = new Map();
    list.forEach(r => { const k = key(r); if (k) m.set(k, (m.get(k) || 0) + 1); });
    return [...m.entries()].sort((a, b) => b[1] - a[1])[0];
  };
  const tp = topBy(r => r.phase);
  if (tp) {
    const p = PHASE_BY_ID[tp[0]] || PHASE_BY_ID.UNKNOWN;
    tile(t('tile_deadliest'),
      `<span class="phase-chip" style="background:${p.color}">${phaseLabel(p.id)}</span>`,
      `${tp[1]} ${t('deaths_n')} (${Math.round(tp[1] / list.length * 100)}%)`);
  } else tile(t('tile_deadliest'), '—');
  const tc = topBy(r => r.cause);
  tile(t('tile_topcause'), tc ? causeLabel(tc[0]) : '—', tc ? `${tc[1]} ${t('deaths_n')}` : '');
  const igts = list.map(r => r.igtAtDeathMillis).filter(v => v != null).sort((a, b) => a - b);
  tile(t('tile_median'),
    igts.length ? `<span class="amber">${fmtIgt(igts[Math.floor(igts.length / 2)])}</span>` : '—',
    `${igts.length} ${t('deaths_with_igt')}`);
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
        labels: keep.map(x => phaseLabel(x.p.id)),
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
        labels: keep.map(x => phaseLabel(x.p.id)),
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
    entries = entries.slice(0, topN).concat([[t('other'), rest]]);
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
  if (!withIgt.length) { plotEmpty('c-igt', t('no_igt')); return; }
  const binSec = Number($('#igt-bin').value);
  const maxBin = Math.min(Math.ceil(Math.max(...withIgt.map(r => r.igtAtDeathMillis)) / 1000 / binSec), 24);
  const labels = [];
  for (let i = 0; i <= maxBin; i++) labels.push(i === maxBin ? `${Math.floor(i * binSec / 60)}m+` : `${Math.floor(i * binSec / 60)}:${String(i * binSec % 60).padStart(2, '0')}`);
  const datasets = PHASES.filter(p => p.id !== 'UNKNOWN').map(p => ({
    label: phaseLabel(p.id),
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
  $('#map-note').textContent = `${t('map_' + dim) || dim} ${t('map_note')}`;
  const phases = MAP_GROUPS[dim];
  const pts = list.filter(r => r.deathX != null && phases.includes(r.phase));
  if (!pts.length) { plotEmpty('c-map', t('no_coords')); return; }
  const datasets = phases.map(id => {
    const p = PHASE_BY_ID[id];
    return {
      label: phaseLabel(p.id),
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
  lg.appendChild(el('span', null, t('phase_legend')));
  for (const p of PHASES.filter(p => p.id !== 'UNKNOWN')) {
    const k = el('span', 'key');
    const sw = el('span', 'swatch');
    sw.style.background = p.color;
    k.appendChild(sw);
    k.appendChild(el('span', null, phaseLabel(p.id)));
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
      r.phase ? phaseLabel(r.phase) : '—',
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
  $('#total-badge').textContent = `${filtered().length} / ${records.length} ${t('deaths_on_record')}`;
}

/* ---------- boot ---------- */

async function boot() {
  buildFilterBar();
  bindPlayer();
  applyStaticI18n();
  $('#lang-toggle').addEventListener('click', () => setLang(lang === 'ja' ? 'en' : 'ja'));
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
