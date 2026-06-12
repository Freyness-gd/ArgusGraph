import { get, post } from "../api.js";
import { toast } from "../toast.js";
import { ChartCanvas } from "../charts.js";

const ENGINES = ["naive", "semi-naive", "native"];
const ENGINE_COLORS = { "naive": "#f0883e", "semi-naive": "#58a6ff", "native": "#3fb950" };

export const InferenceView = {
  engine: "naive",
  runs: [],
  running: false,
  rules: [],
  runningRules: false,
  imputing: false,
  evaluating: false,
  eval: null,

  oninit() { this.load(); },
  load() {
    get("/inference/runs")
      .then((runs) => { this.runs = runs; })
      .catch((err) => toast.error(err))
      .finally(m.redraw);
    get("/inference/rules")
      .then((rules) => { this.rules = rules; })
      .catch((err) => toast.error(err))
      .finally(m.redraw);
  },
  toggleRule(name, enabled) {
    post(`/inference/rules/${encodeURIComponent(name)}/enabled?enabled=${enabled}`)
      .then((rules) => { this.rules = rules; })
      .catch((err) => toast.error(err))
      .finally(m.redraw);
  },
  moveRule(index, dir) {
    const order = this.rules.map((r) => r.name);
    const j = index + dir;
    if (j < 0 || j >= order.length) return;
    [order[index], order[j]] = [order[j], order[index]];
    post("/inference/rules/order", order)
      .then((rules) => { this.rules = rules; })
      .catch((err) => toast.error(err))
      .finally(m.redraw);
  },
  runRules() {
    this.runningRules = true;
    post("/inference/run-rules")
      .then((r) => toast.ok(`rules: ${r.durationMs} ms · ${r.rounds} rounds · ${r.queryCount} queries · ${r.edgesWritten} edges`))
      .then(() => this.load())
      .catch((err) => toast.error(err))
      .finally(() => { this.runningRules = false; m.redraw(); });
  },
  run() {
    this.running = true;
    post("/inference/recompute?engine=" + encodeURIComponent(this.engine))
      .then((r) => toast.ok(`${r.engine}: ${r.durationMs} ms · ${r.rounds} rounds · ${r.queryCount} queries · ${r.edgesWritten} edges`))
      .then(() => this.load())
      .catch((err) => toast.error(err))
      .finally(() => { this.running = false; m.redraw(); });
  },
  impute() {
    this.imputing = true;
    post("/inference/impute-severity")
      .then((r) => toast.ok(`Imputed severity for ${r.predicted} advisories in ${r.durationMs} ms`))
      .catch((err) => toast.error(err))
      .finally(() => { this.imputing = false; m.redraw(); });
  },
  evaluate() {
    this.evaluating = true;
    post("/inference/eval-severity")
      .then((r) => { this.eval = r; toast.ok(`Eval: MAE ${r.mae.toFixed(2)}, label acc ${(r.labelAccuracy * 100).toFixed(0)}% (n=${r.n})`); })
      .catch((err) => toast.error(err))
      .finally(() => { this.evaluating = false; m.redraw(); });
  },
  view() {
    return [
      m("h1", "Inference engines"),
      m(".card", [
        m(".card-label", "Run a recompute"),
        m("p.muted", "Three strategies compute the same transitive closure differently. "
            + "Compare wall-clock, fixpoint rounds, and Cypher round-trips."),
        m(".filters", [
          m("select", { value: this.engine, onchange: (e) => { this.engine = e.target.value; } },
            ENGINES.map((eng) => m("option", { value: eng, selected: eng === this.engine }, eng))),
          m("button", { disabled: this.running, onclick: () => this.run() },
            this.running ? "Running…" : "Run recompute"),
        ]),
      ]),
      m(".card", [
        m(".card-label", "Rules — pluggable pipeline"),
        m("p.muted", "The engine runs these rules top-to-bottom. Toggle a rule off to exclude it, "
            + "reorder with the arrows, then Run rules to rebuild derived edges in this order."),
        this.rules.length > 0 && m("table", [
          m("thead", m("tr", ["On", "Rule", "Stratum", "Recursive", "Order"].map((h) => m("th", h)))),
          m("tbody", this.rules.map((r, i) => m("tr", [
            m("td", m("input", { type: "checkbox", checked: r.enabled,
              onchange: (e) => this.toggleRule(r.name, e.target.checked) })),
            m("td", r.name),
            m("td", r.stratum),
            m("td", r.recursive ? "yes" : "no"),
            m("td", [
              m("button", { disabled: i === 0, onclick: () => this.moveRule(i, -1) }, "▲"),
              m("button", { disabled: i === this.rules.length - 1, onclick: () => this.moveRule(i, 1) }, "▼"),
            ]),
          ]))),
        ]),
        m(".filters", [
          m("button", { disabled: this.runningRules, onclick: () => this.runRules() },
            this.runningRules ? "Running…" : "Run rules"),
        ]),
      ]),
      this.runs.length > 0 && m(".card", [
        m(".card-label", "Duration by engine (ms)"),
        m(".chart-box", m(ChartCanvas, {
          type: "bar",
          data: {
            labels: this.runs.slice(0, 12).reverse().map((r) => r.engine),
            datasets: [{
              label: "ms",
              data: this.runs.slice(0, 12).reverse().map((r) => r.durationMs),
              backgroundColor: this.runs.slice(0, 12).reverse().map((r) => ENGINE_COLORS[r.engine] || "#8b949e"),
            }],
          },
          options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } } },
        })),
      ]),
      m(".card", [
        m(".card-label", "Embedding — severity imputation (latent rₑ)"),
        m("p.muted", "Predict CVSS severity for advisories with no score, from the "
            + "similarity-weighted average of their nearest embedded neighbours. "
            + "Evaluate measures leave-one-out accuracy over scored advisories."),
        m(".filters", [
          m("button", { disabled: this.imputing, onclick: () => this.impute() },
            this.imputing ? "Imputing…" : "Impute severities"),
          m("button", { disabled: this.evaluating, onclick: () => this.evaluate() },
            this.evaluating ? "Evaluating…" : "Evaluate accuracy"),
        ]),
        this.eval && m(".badges", [
          m("span.badge", `MAE ${this.eval.mae.toFixed(2)}`),
          m("span.badge", `Label acc ${(this.eval.labelAccuracy * 100).toFixed(0)}%`),
          m("span.badge", `n=${this.eval.n}`),
        ]),
      ]),
      m(".card", [
        m(".card-label", "Recent runs"),
        this.runs.length === 0
          ? m("p.muted", "No runs yet — pick an engine and run a recompute.")
          : m("table", [
              m("thead", m("tr", ["Engine", "Time (ms)", "Rounds", "Queries", "Edges"].map((h) => m("th", h)))),
              m("tbody", this.runs.map((r) => m("tr", [
                m("td", r.engine), m("td", r.durationMs), m("td", r.rounds),
                m("td", r.queryCount), m("td", r.edgesWritten),
              ]))),
            ]),
      ]),
    ];
  },
};
