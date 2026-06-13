/* Mini SVG renderer for an exposure's dependency chain. No library — Mithril
   auto-detects the SVG namespace for svg/g/rect/line/text/marker/path/defs/title,
   so plain m("rect", …) calls render correctly. Layout is deterministic. */

const BOX_W = 150;
const BOX_H = 34;
const GAP = 46;
const PAD = 20;

const SEV_COLOR = {
  critical: "#f85149",
  high: "#db6d28",
  medium: "#d29922",
  low: "#3fb950",
  none: "#8b949e",
};

/** Short label for a node: substring after the last "/" (keep @version); ellipsis if > 22. */
function shortLabel(purl) {
  const slash = purl.lastIndexOf("/");
  let label = slash >= 0 ? purl.slice(slash + 1) : purl;
  if (label.length > 22) label = label.slice(0, 21) + "…";
  return label;
}

/** The vuln id, truncated to ~18 chars. */
function shortVuln(id) {
  return id.length > 18 ? id.slice(0, 18) + "…" : id;
}

export function exposureGraph({ path, vulnId, affectedPurl, severity }) {
  if (!path || path.length === 0) {
    return m("p.muted", "No live dependency chain found (the DEPENDS_ON edge may have changed).");
  }

  const sevKey = (severity || "none").toLowerCase();
  const sevColor = SEV_COLOR[sevKey] || SEV_COLOR.none;

  const nodeX = (i) => PAD + i * (BOX_W + GAP);
  const nodeY = PAD;
  const vulnY = PAD + BOX_H + 40;

  const width = PAD * 2 + path.length * BOX_W + (path.length - 1) * GAP;
  const height = PAD * 2 + BOX_H + 40 + BOX_H;

  const defs = m("defs", [
    m("marker#eg-arrow", { markerWidth: 8, markerHeight: 8, refX: 7, refY: 3, orient: "auto" },
      m("path", { d: "M0,0 L7,3 L0,6 Z", fill: "#8b949e" })),
    m("marker#eg-arrow-crit", { markerWidth: 8, markerHeight: 8, refX: 7, refY: 3, orient: "auto" },
      m("path", { d: "M0,0 L7,3 L0,6 Z", fill: sevColor })),
  ]);

  const nodes = path.map((purl, i) => {
    const x = nodeX(i);
    return m("g.eg-node-g", [
      m("rect.eg-node", { x, y: nodeY, width: BOX_W, height: BOX_H, rx: 5 }),
      m("title", purl),
      m("text.eg-label", { x: x + BOX_W / 2, y: nodeY + BOX_H / 2 + 4, "text-anchor": "middle" },
        shortLabel(purl)),
    ]);
  });

  const edges = [];
  for (let i = 0; i < path.length - 1; i++) {
    const x1 = nodeX(i) + BOX_W;
    const x2 = nodeX(i + 1);
    const y = nodeY + BOX_H / 2;
    const mid = (x1 + x2) / 2;
    edges.push(m("line.eg-edge", { x1, y1: y, x2, y2: y, "marker-end": "url(#eg-arrow)" }));
    edges.push(m("text.eg-edge-label", { x: mid, y: nodeY - 6, "text-anchor": "middle" }, "depends on"));
  }

  const lastX = nodeX(path.length - 1);
  const vulnCenterX = lastX + BOX_W / 2;
  const vulnGroup = m("g.eg-vuln-g", [
    m("rect.eg-vuln", { x: lastX, y: vulnY, width: BOX_W, height: BOX_H, rx: 5 }),
    m("title", vulnId),
    m("text.eg-label", { x: vulnCenterX, y: vulnY + BOX_H / 2 + 4, "text-anchor": "middle" },
      shortVuln(vulnId)),
    m("line", {
      class: "eg-affects sev-line-" + sevKey,
      x1: vulnCenterX, y1: vulnY, x2: vulnCenterX, y2: nodeY + BOX_H,
      stroke: sevColor, "marker-end": "url(#eg-arrow-crit)",
    }),
    m("text.eg-affects-label", { x: vulnCenterX + 6, y: (vulnY + nodeY + BOX_H) / 2, "text-anchor": "start" },
      "affects"),
  ]);

  return m("svg.exposure-graph", {
    viewBox: `0 0 ${width} ${height}`,
    width: "100%",
    height,
    preserveAspectRatio: "xMinYMin meet",
  }, [defs, ...nodes, ...edges, vulnGroup]);
}

/* Star/cross neighbourhood: center version in the middle, dependents column to the
   left, dependencies to the right, direct vulns stacked top, transitive vulns
   stacked bottom. Each group is capped at 8 nodes + a muted "+N more". The center
   node is not clickable; every other node calls onNavigate(kind, value). */
const N_CAP = 8;
const N_BOX_W = 150;
const N_BOX_H = 30;
const N_V_GAP = 10;
const N_COL_GAP = 90;
const N_PAD = 24;
const N_ROW_GAP = 60;

/** Lay out one capped group; returns { rows, more } where rows are the visible items. */
function capGroup(items) {
  const rows = items.slice(0, N_CAP);
  return { rows, more: Math.max(0, items.length - N_CAP) };
}

export function neighbourhoodGraph(data, onNavigate) {
  const dependents = capGroup(data.dependents || []);
  const dependencies = capGroup(data.dependencies || []);
  const vulns = capGroup(data.vulnerabilities || []);
  const transitive = capGroup(data.transitive || []);

  const total = dependents.rows.length + dependencies.rows.length
    + vulns.rows.length + transitive.rows.length;
  if (total === 0) {
    return m("p.muted", "No neighbours in the graph.");
  }

  // Vertical extent of a side column (dependents / dependencies).
  const colHeight = (n) => n > 0 ? n * N_BOX_H + (n - 1) * N_V_GAP : 0;
  const leftH = colHeight(dependents.rows.length);
  const rightH = colHeight(dependencies.rows.length);
  const topH = colHeight(vulns.rows.length);
  const botH = colHeight(transitive.rows.length);

  // Center is placed so that top/bottom stacks fit above/below it. The core band
  // is the tallest side column (dependents/dependencies); add room below it for a
  // "+N more" caption if a side group overflows.
  const sideOverflow = (dependents.more > 0 || dependencies.more > 0) ? 18 : 0;
  const sideMax = Math.max(leftH, rightH, N_BOX_H) + sideOverflow;
  const topBlock = topH > 0 ? topH + N_ROW_GAP : 0;
  const botExtra = transitive.more > 0 ? 18 : 0;
  const botBlock = botH > 0 ? botH + N_ROW_GAP + botExtra : 0;
  const coreH = Math.max(sideMax, N_BOX_H);

  const cx = N_PAD + N_BOX_W + N_COL_GAP + N_BOX_W / 2;
  const cy = N_PAD + topBlock + Math.max(leftH, rightH, N_BOX_H) / 2;

  const width = N_PAD * 2 + 3 * N_BOX_W + 2 * N_COL_GAP;
  const height = N_PAD * 2 + topBlock + coreH + botBlock;

  // Y for the i-th item in a vertical group of n boxes centred on cy.
  const stackY = (i, n) => cy - colHeight(n) / 2 + i * (N_BOX_H + N_V_GAP);

  const els = [];

  // Center node (not clickable).
  const centerX = cx - N_BOX_W / 2;
  const centerY = cy - N_BOX_H / 2;
  els.push(m("g", [
    m("rect.eg-node.eg-center", { x: centerX, y: centerY, width: N_BOX_W, height: N_BOX_H, rx: 5 }),
    m("title", data.center || ""),
    m("text.eg-label", { x: cx, y: cy + 4, "text-anchor": "middle" },
      shortLabel(data.center || data.version || "center")),
  ]));

  // A clickable package/vuln node + its edge to center.
  const pkgNode = (purl, x, y, ex, ey) => {
    els.push(m("line.eg-edge", { x1: ex, y1: ey, x2: x + N_BOX_W / 2, y2: y + N_BOX_H / 2 }));
    els.push(m("g", { style: "cursor:pointer", onclick: () => onNavigate("package", purl) }, [
      m("rect.eg-node", { x, y, width: N_BOX_W, height: N_BOX_H, rx: 5 }),
      m("title", purl),
      m("text.eg-label", { x: x + N_BOX_W / 2, y: y + N_BOX_H / 2 + 4, "text-anchor": "middle" },
        shortLabel(purl)),
    ]));
  };

  const vulnNode = (v, x, y, ex, ey) => {
    const sevKey = (v.severity || "none").toLowerCase();
    const sevColor = SEV_COLOR[sevKey] || SEV_COLOR.none;
    els.push(m("line.eg-edge", { x1: ex, y1: ey, x2: x + N_BOX_W / 2, y2: y + N_BOX_H / 2, stroke: sevColor }));
    els.push(m("g", { style: "cursor:pointer", onclick: () => onNavigate("vuln", v.id) }, [
      m("rect.eg-node", { x, y, width: N_BOX_W, height: N_BOX_H, rx: 5, stroke: sevColor }),
      m("title", v.id + (v.depth != null ? ` (depth ${v.depth})` : "")),
      m("text.eg-label", { x: x + N_BOX_W / 2, y: y + N_BOX_H / 2 + 4, "text-anchor": "middle", fill: sevColor },
        shortVuln(v.id)),
    ]));
  };

  // Dependents — left column (edge from each neighbour to the center's left edge).
  const leftX = N_PAD;
  dependents.rows.forEach((purl, i) => {
    pkgNode(purl, leftX, stackY(i, dependents.rows.length), centerX, cy);
  });

  // Dependencies — right column (edge to the center's right edge).
  const rightX = N_PAD + 2 * N_BOX_W + 2 * N_COL_GAP;
  dependencies.rows.forEach((purl, i) => {
    pkgNode(purl, rightX, stackY(i, dependencies.rows.length), centerX + N_BOX_W, cy);
  });

  // Direct vulns — stacked top.
  const topY0 = N_PAD;
  vulns.rows.forEach((v, i) => {
    vulnNode(v, cx - N_BOX_W / 2, topY0 + i * (N_BOX_H + N_V_GAP), cx, centerY);
  });

  // Transitive vulns — stacked bottom.
  const botY0 = cy + coreH / 2 + N_ROW_GAP;
  transitive.rows.forEach((t, i) => {
    vulnNode(t, cx - N_BOX_W / 2, botY0 + i * (N_BOX_H + N_V_GAP), cx, centerY + N_BOX_H);
  });

  // Group labels (muted). Placed just outside each group.
  const label = (text, x, y, anchor) =>
    els.push(m("text.eg-edge-label", { x, y, "text-anchor": anchor || "middle" }, text));

  if (dependents.rows.length) label("dependents", leftX + N_BOX_W / 2, stackY(0, dependents.rows.length) - 8);
  if (dependencies.rows.length) label("dependencies", rightX + N_BOX_W / 2, stackY(0, dependencies.rows.length) - 8);
  if (vulns.rows.length) label("vulns", cx, topY0 - 8);
  if (transitive.rows.length) label("exposed via", cx, botY0 - 8);

  // "+N more" muted text under any overflowing group.
  const more = (n, x, y) => { if (n > 0) els.push(m("text.eg-edge-label", { x, y, "text-anchor": "middle" }, `+${n} more`)); };
  more(dependents.more, leftX + N_BOX_W / 2, stackY(dependents.rows.length - 1, dependents.rows.length) + N_BOX_H + 12);
  more(dependencies.more, rightX + N_BOX_W / 2, stackY(dependencies.rows.length - 1, dependencies.rows.length) + N_BOX_H + 12);
  more(vulns.more, cx, topY0 + vulns.rows.length * (N_BOX_H + N_V_GAP) + 4);
  more(transitive.more, cx, botY0 + transitive.rows.length * (N_BOX_H + N_V_GAP) + 4);

  return m("svg.exposure-graph", {
    viewBox: `0 0 ${width} ${height}`,
    width: "100%",
    height,
    preserveAspectRatio: "xMinYMin meet",
  }, els);
}
