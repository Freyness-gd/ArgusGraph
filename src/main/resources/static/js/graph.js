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
