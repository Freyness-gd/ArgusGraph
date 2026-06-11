import { get } from "../api.js";
import { toast } from "../toast.js";

const SEVERITY_ORDER = ["CRITICAL", "HIGH", "MEDIUM", "LOW", "NONE"];

export const StatsView = {
  stats: null,
  oninit() {
    this.stats = null;
    get("/graph/stats")
      .then((stats) => { this.stats = stats; })
      .catch((err) => toast.error(err))
      .finally(m.redraw);
  },
  view() {
    if (!this.stats) {
      return m("p.muted", "Loading…");
    }
    const buckets = this.stats.bySeverity || {};
    const keys = [
      ...SEVERITY_ORDER.filter((k) => k in buckets),
      ...Object.keys(buckets).filter((k) => !SEVERITY_ORDER.includes(k)),
    ];
    const total = keys.reduce((sum, k) => sum + buckets[k], 0);
    return [
      m("h1", "Stats"),
      m(".cards", [
        m(".card", [m(".card-label", "Packages"), m(".card-value", fmt(this.stats.packages))]),
        m(".card", [m(".card-label", "Versions"), m(".card-value", fmt(this.stats.packageVersions))]),
        m(".card", [
          m(".card-label", "Vulnerabilities"),
          m(".card-value.danger", fmt(this.stats.vulnerabilities)),
        ]),
      ]),
      m(".card", [
        m(".card-label", "By severity"),
        keys.length === 0
          ? m("p.muted", "No vulnerabilities yet.")
          : m(".badges", keys.map((k) =>
              m("span.badge", { class: "sev-" + k.toLowerCase() }, `${k} ${fmt(buckets[k])}`))),
        total > 0 && m(".sev-bar", keys.map((k) => m("div", {
          class: "sev-" + k.toLowerCase(),
          style: { width: (100 * buckets[k] / total) + "%" },
        }))),
      ]),
    ];
  },
};

/* 1204 -> "1 204" (thin-space look without locale surprises). */
const fmt = (n) => (n == null ? "0" : n.toLocaleString("en-US").replace(/,/g, " "));
