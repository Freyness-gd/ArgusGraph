import { get } from "../api.js";
import { toast } from "../toast.js";
import { ChartCanvas } from "../charts.js";

const SEVERITY_ORDER = ["CRITICAL", "HIGH", "MEDIUM", "LOW", "NONE"];

const isoDate = (d) => d.toISOString().slice(0, 10);

export const StatsView = {
  stats: null,
  trend: null,
  topPackages: null,
  from: "",
  to: "",

  oninit() {
    this.stats = null;
    this.trend = null;
    this.topPackages = null;
    const today = new Date();
    this.to = isoDate(today);
    this.from = isoDate(new Date(today.getTime() - 30 * 24 * 3600 * 1000));
    get("/graph/stats")
      .then((stats) => { this.stats = stats; })
      .catch((err) => toast.error(err))
      .finally(m.redraw);
    this.loadTrend();
    this.loadTop();
  },
  loadTrend() {
    get(`/graph/stats/vulnerability-trend?from=${this.from}&to=${this.to}`)
      .then((trend) => { this.trend = trend; })
      .catch((err) => toast.error(err))
      .finally(m.redraw);
  },
  loadTop() {
    get("/graph/stats/top-packages?limit=10")
      .then((top) => { this.topPackages = top; })
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
      m(".card", [
        m(".card-label", "New vulnerabilities"),
        m(".filters", [
          m("input", { type: "date", value: this.from,
            onchange: (e) => { this.from = e.target.value; this.loadTrend(); } }),
          m("input", { type: "date", value: this.to,
            onchange: (e) => { this.to = e.target.value; this.loadTrend(); } }),
        ]),
        this.trend && this.trend.buckets.some((b) => b.count > 0)
          ? m(".chart-box", m(ChartCanvas, {
              type: "line",
              data: {
                labels: this.trend.buckets.map((b) => b.label),
                datasets: [{
                  data: this.trend.buckets.map((b) => b.count),
                  borderColor: "#58a6ff",
                  backgroundColor: "rgba(88, 166, 255, 0.15)",
                  fill: true,
                  tension: 0.3,
                  pointRadius: 2,
                }],
              },
              options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { display: false } },
                scales: { y: { beginAtZero: true, ticks: { precision: 0 } } },
              },
            }))
          : m("p.muted", "No published vulnerabilities in this range."),
      ]),
      m(".card", [
        m(".card-label", "Top compromised packages"),
        this.topPackages && this.topPackages.length > 0
          ? m(".chart-box", m(ChartCanvas, {
              type: "bar",
              data: {
                labels: this.topPackages.map((p) => p.packagePurl),
                datasets: [{
                  data: this.topPackages.map((p) => p.vulnerabilities),
                  backgroundColor: "#bb8009",
                }],
              },
              options: {
                responsive: true,
                maintainAspectRatio: false,
                indexAxis: "y",
                plugins: { legend: { display: false } },
                scales: {
                  x: { beginAtZero: true, ticks: { precision: 0 } },
                  y: { ticks: { callback(value) {
                    const label = this.getLabelForValue(value);
                    return label.length > 38 ? "…" + label.slice(-37) : label;
                  } } },
                },
              },
            }))
          : m("p.muted", "No affected packages yet."),
      ]),
    ];
  },
};

/* 1204 -> "1 204" (thin-space look without locale surprises). */
const fmt = (n) => (n == null ? "0" : n.toLocaleString("en-US").replace(/,/g, " "));
