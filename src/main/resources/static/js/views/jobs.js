import { get, post } from "../api.js";
import { toast } from "../toast.js";

export const JobsView = {
  ecosystem: "Maven",
  running: false,
  wiping: false,
  status: null,
  statusError: false,
  timer: null,
  polling: false,

  oncreate() {
    clearInterval(this.timer);
    this.poll();
    this.timer = setInterval(() => this.poll(), 2000);
  },
  onremove() {
    clearInterval(this.timer);
  },
  poll() {
    if (this.polling) {
      return;
    }
    this.polling = true;
    get("/ingest/jobs/status")
      .then((status) => { this.status = status; this.statusError = false; })
      .catch(() => { this.statusError = true; })
      .finally(() => { this.polling = false; m.redraw(); });
  },
  hasActivity() {
    return this.status && ((this.status.jobs ?? []).length > 0
        || (this.status.queues ?? []).some((q) => (q.messages ?? 0) > 0));
  },
  trigger() {
    this.running = true;
    post("/ingest/jobs/osv?ecosystem=" + encodeURIComponent(this.ecosystem.trim()))
      .then(() => toast.ok(`OSV fetch started for ${this.ecosystem.trim()}`))
      .catch((err) => toast.error(err))
      .finally(() => { this.running = false; m.redraw(); this.poll(); });
  },
  reset() {
    if (!window.confirm("Delete ALL nodes — packages, versions, vulnerabilities? This cannot be undone.")) {
      return;
    }
    this.wiping = true;
    post("/graph/reset", { confirm: "WIPE" })
      .then((result) => toast.ok(`Graph wiped (${result.nodesDeleted} nodes deleted)`))
      .catch((err) => toast.error(err))
      .finally(() => { this.wiping = false; m.redraw(); });
  },
  view() {
    const jobs = this.status ? (this.status.jobs ?? []) : [];
    const active = jobs.filter((j) => j.state === "RUNNING");
    const recent = jobs.filter((j) => j.state !== "RUNNING");
    return [
      m("h1", "Ingest jobs"),
      m(".card", [
        m(".card-label", "OSV ecosystem fetch"),
        m("p.muted", "Downloads one OSV ecosystem dump and queues every advisory for ingestion. "
            + "Fire and forget — progress shows up in the application logs and RabbitMQ."),
        m(".filters", [
          m("input", {
            placeholder: "Maven",
            value: this.ecosystem,
            oninput: (e) => { this.ecosystem = e.target.value; },
          }),
          m("button",
            { disabled: this.running || !this.ecosystem.trim(), onclick: () => this.trigger() },
            this.running ? "Starting…" : "Start fetch"),
        ]),
        m("p.muted", ["Queue status: ", m("a", { href: "http://localhost:15672", target: "_blank",
            rel: "noopener" }, "RabbitMQ management UI")]),
      ]),
      this.hasActivity() && m(".card", [
        m(".card-label", "Pipeline status"),
        this.statusError && m("p.muted", "status unavailable"),
        active.map((j) => m("p", [
          m("span.dot-running", "● "),
          `${j.ecosystem} — running · ${j.documentsPublished} docs published`,
        ])),
        m("table", m("tbody", (this.status.queues ?? []).map((q) => m("tr", [
          m("td.mono", q.name),
          m("td", m("span.badge", { class: queueClass(q) },
              q.messages == null ? "n/a" : q.messages)),
        ])))),
        recent.length > 0 && m("table", [
          m("thead", m("tr", ["State", "Ecosystem", "Docs", "Duration", "Error"].map((h) => m("th", h)))),
          m("tbody", recent.map((j) => m("tr", [
            m("td", m("span.badge", { class: "state-" + j.state.toLowerCase() }, j.state)),
            m("td.mono", j.ecosystem),
            m("td", j.documentsPublished),
            m("td.muted", duration(j)),
            m("td.muted", j.error || ""),
          ]))),
        ]),
      ]),
      m(".card", [
        m(".card-label", "Danger zone — reset database"),
        m("p.muted", "Deletes every node in the graph — packages, versions, vulnerabilities. "
            + "Run it before starting a fetch; a fetch already in flight will re-add rows "
            + "from its queue. Cannot be undone."),
        m("button.danger", { disabled: this.wiping, onclick: () => this.reset() },
          this.wiping ? "Wiping…" : "Wipe graph"),
      ]),
    ];
  },
};

/* DLQs with messages are an alarm; working queues with messages are just busy. */
const queueClass = (q) => {
  if ((q.messages ?? 0) === 0) return "q-idle";
  return q.name.endsWith(".dlq") ? "q-alarm" : "q-busy";
};

const duration = (j) => {
  if (!j.startedAt || !j.finishedAt) return "—";
  return Math.round((new Date(j.finishedAt) - new Date(j.startedAt)) / 1000) + "s";
};
