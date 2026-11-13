// Shared mock data, icons and candle chart for the UX prototypes. Not app code.
const ICONS = {
  bell: 'M12 22a2 2 0 0 0 2-2h-4a2 2 0 0 0 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4a1.5 1.5 0 0 0-3 0v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z',
  history: 'M13 3a9 9 0 0 0-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42A8.954 8.954 0 0 0 13 21a9 9 0 0 0 0-18zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z',
  chart: 'M9 4H7v2H5v12h2v2h2v-2h2V6H9V4zm0 12H7V8h2v8zm10-8h-2V4h-2v4h-2v7h2v5h2v-5h2V8zm-2 5h-2v-3h2v3z',
  plus: 'M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z',
  up: 'M7 14l5-5 5 5z',
  down: 'M7 10l5 5 5-5z',
  tup: 'M16 6l2.29 2.29-4.88 4.88-4-4L2 16.59 3.41 18l6-6 4 4 6.3-6.29L22 12V6z',
  tdown: 'M16 18l2.29-2.29-4.88-4.88-4 4L2 7.41 3.41 6l6 6 4-4 6.3 6.29L22 12v6z',
  battery: 'M15.67 4H14V2h-4v2H8.33C7.6 4 7 4.6 7 5.33v15.33C7 21.4 7.6 22 8.33 22h7.33c.74 0 1.34-.6 1.34-1.33V5.33C17 4.6 16.4 4 15.67 4zM13 18h-2v-2h2v2zm0-4h-2V9h2v5z',
  more: 'M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z',
  play: 'M8 5v14l11-7z',
  stop: 'M6 6h12v12H6z',
  replay: 'M12 5V1L7 6l5 5V7c3.31 0 6 2.69 6 6s-2.69 6-6 6-6-2.69-6-6H4c0 4.42 3.58 8 8 8s8-3.58 8-8-3.58-8-8-8z',
  del: 'M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z',
  back: 'M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z',
  close: 'M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z',
  check: 'M9 16.17L4.83 12l-1.42 1.42L9 19 21 7l-1.41-1.41z',
  bellOff: 'M20 18.69L7.84 6.14 5.27 3.49 4 4.76l2.8 2.8v.01c-.52.99-.8 2.16-.8 3.42v5l-2 2v1h13.73l2 2L21 19.72l-1-1.03zM12 22c1.11 0 2-.89 2-2h-4c0 1.11.89 2 2 2zm6-7.32V11c0-3.08-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68c-.15.03-.29.08-.42.12-.1.03-.2.07-.3.11h-.01c-.01 0-.01 0-.02.01-.23.09-.46.2-.68.31 0 0-.01 0-.01.01L18 14.68z',
  backspace: 'M22 3H7c-.69 0-1.23.35-1.59.88L0 12l5.41 8.11c.36.53.9.89 1.59.89h15c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-3 12.59L17.59 17 14 13.41 10.41 17 9 15.59 12.59 12 9 8.41 10.41 7 14 10.59 17.59 7 19 8.41 15.41 12 19 15.59z',
  bolt: 'M7 2v11h3v9l7-12h-4l4-8z',
};

const fmt = (v, d = 2) => v.toLocaleString('en-US', { minimumFractionDigits: d, maximumFractionDigits: d });

function rng(seed) {
  return () => (seed = (seed * 1664525 + 1013904223) % 4294967296) / 4294967296;
}

// Random walk ending at the mocked last price, so every prototype shows the same market.
function candles(n, seed) {
  const r = rng(seed);
  let p = 0;
  const out = [];
  for (let i = 0; i < n; i++) {
    const o = p, c = o + (r() - 0.45) * 420;
    const h = Math.max(o, c) + r() * 190, l = Math.min(o, c) - r() * 190;
    out.push({ o, c, h, l, v: (12 + r() * 90) * (r() < 0.12 ? 3 : 1) });
    p = c;
  }
  const d = 97412.35 - p;
  out.forEach(k => { k.o += d; k.c += d; k.h += d; k.l += d; });
  return out;
}

function drawChart(el, o) {
  const { n = 46, seed = 11, w = 340, h = 280, vol = 0.2, axis = true, lines = [], sel = null, drag = null, lastTag = true } = o;
  const ks = candles(n, seed);
  const ax = axis ? 54 : 0, cw = w - ax, ph = h * (1 - vol) - 6;
  let lo = Math.min(...ks.map(k => k.l)), hi = Math.max(...ks.map(k => k.h));
  const pad = (hi - lo) * 0.07; lo -= pad; hi += pad;
  const y = p => 8 + (hi - p) / (hi - lo) * (ph - 8);
  const step = cw / n, bw = Math.max(2, step * 0.62), vmax = Math.max(...ks.map(k => k.v));
  const last = ks[n - 1].c, ly = y(last);
  let s = `<svg class="chart" viewBox="0 0 ${w} ${h}" width="${w}" height="${h}">`;
  for (let i = 0; i < 4; i++) {
    const p = lo + (hi - lo) * (i + 0.5) / 4, yy = y(p);
    s += `<line class="grid" x1="0" x2="${cw}" y1="${yy}" y2="${yy}"/>`;
    if (axis && (!lastTag || Math.abs(yy - ly) > 16)) s += `<text class="axis" x="${w - 4}" y="${yy + 3}" text-anchor="end">${fmt(p, 0)}</text>`;
  }
  if (sel != null) { const x = sel * step + step / 2; s += `<line class="sel" x1="${x}" x2="${x}" y1="0" y2="${h}"/>`; }
  ks.forEach((k, i) => {
    const x = i * step + step / 2, cls = k.c >= k.o ? 'up' : 'down';
    const t = y(Math.max(k.o, k.c)), b = y(Math.min(k.o, k.c)), vh = k.v / vmax * h * vol * 0.85;
    s += `<line class="wick ${cls}" x1="${x}" x2="${x}" y1="${y(k.h)}" y2="${y(k.l)}"/>`;
    s += `<rect class="${cls}" x="${x - bw / 2}" y="${t}" width="${bw}" height="${Math.max(1, b - t)}" rx="1"/>`;
    if (vol) s += `<rect class="vol ${cls}" x="${x - bw / 2}" y="${h - vh}" width="${bw}" height="${vh}"/>`;
  });
  // Off-range alerts pin to the chart edge with an arrow instead of squashing the candles.
  lines.forEach(L => {
    const off = L.p > hi ? ' ↑' : L.p < lo ? ' ↓' : '';
    const yy = L.p > hi ? 12 : L.p < lo ? ph - 4 : y(L.p), label = L.label + off, lw = label.length * 6 + 12;
    if (!off) s += `<line class="aline ${L.dir}" x1="0" x2="${cw}" y1="${yy}" y2="${yy}"/>`;
    const L2 = { label };
    s += `<g class="atag ${L.dir}"><rect x="6" y="${yy - 9}" width="${lw}" height="18" rx="9"/><text x="12" y="${yy + 3.5}">${L2.label}</text></g>`;
  });
  if (drag) {
    const yy = y(drag);
    s += `<line class="drag" x1="0" x2="${w}" y1="${yy}" y2="${yy}"/>`;
    s += `<g class="tag"><rect x="${cw}" y="${yy - 9}" width="${ax}" height="18" rx="4" style="fill:var(--accent)"/><text x="${w - 4}" y="${yy + 3.5}" text-anchor="end" style="fill:var(--onaccent)">${fmt(drag, 0)}</text></g>`;
  }
  if (lastTag) {
    s += `<line class="last" x1="0" x2="${cw}" y1="${ly}" y2="${ly}"/>`;
    if (axis) s += `<g class="tag"><rect x="${cw}" y="${ly - 9}" width="${ax}" height="18" rx="4"/><text x="${w - 4}" y="${ly + 3.5}" text-anchor="end">${fmt(last, 0)}</text></g>`;
  }
  el.innerHTML = s + '</svg>';
  return ks;
}

function volume(v) { return v.toFixed(5).replace(/0+$/, '').replace(/\.$/, ''); }

function hydrate() {
  const q = new URLSearchParams(location.search).get('theme');
  if (q) document.documentElement.dataset.theme = q;
  document.querySelectorAll('[data-clone]').forEach(e => { e.innerHTML = document.getElementById(e.dataset.clone).innerHTML; });
  document.querySelectorAll('[data-chart]').forEach(e => {
    const o = JSON.parse(e.dataset.chart || '{}');
    const ks = drawChart(e, o);
    const out = e.closest('.phone').querySelector('[data-ohlc]');
    if (out) {
      const k = ks[o.sel ?? ks.length - 1];
      out.innerHTML = out.dataset.ohlc
        .replace('{o}', fmt(k.o)).replace('{h}', fmt(k.h)).replace('{l}', fmt(k.l))
        .replace('{c}', fmt(k.c)).replace('{v}', volume(k.v));
    }
  });
  document.querySelectorAll('[data-ic]').forEach(e => {
    e.innerHTML = `<svg class="ic" viewBox="0 0 24 24" aria-hidden="true"><path d="${ICONS[e.dataset.ic]}"/></svg>`;
  });
  const t = document.querySelector('[data-theme-toggle]');
  if (t) t.onclick = () => {
    const root = document.documentElement;
    const dark = root.dataset.theme ? root.dataset.theme === 'dark' : matchMedia('(prefers-color-scheme: dark)').matches;
    root.dataset.theme = dark ? 'light' : 'dark';
  };
  // Small interactions: timeframe tabs and switches toggle in place.
  document.querySelectorAll('[data-group]').forEach(g => g.querySelectorAll('button').forEach(b => b.onclick = () => {
    g.querySelectorAll('button').forEach(x => x.classList.remove('on')); b.classList.add('on');
  }));
  document.querySelectorAll('.switch').forEach(s => s.onclick = () => s.classList.toggle('on'));
}
document.addEventListener('DOMContentLoaded', hydrate);
