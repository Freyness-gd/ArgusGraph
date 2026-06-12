import { get, post } from "../api.js";
import { toast } from "../toast.js";
import { ChartCanvas } from "../charts.js";

const ENGINES = ["naive", "semi-naive", "native"];
const ENGINE_COLORS = { "naive": "#f0883e", "semi-naive": "#58a6ff", "native": "#3fb950" };

export const InferenceView = {
  engine: "naive",
  runs: [],
  running: false,

  oninit() { this.load(); },
  load() {
    get("/inference/runs")
      .then((runs) => { this.runs = runs; })
      .catch((err) => toast.error(err))
      .finally(m.redraw);
  },
  run() {
    this.running = true;
    post("/inference/recompute?engine=" + encodeURIComponent(this.engine))
      .then((r) => toast.ok(`${r.engine}: ${r.durationMs} ms · ${r.rounds} rounds · ${r.queryCount} queries · ${r.edgesWritten} edges`))
      .then(() => this.load())
      .catch((err) => toast.error(err))
      .finally(() => { this.running = false; m.redraw(); });
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
