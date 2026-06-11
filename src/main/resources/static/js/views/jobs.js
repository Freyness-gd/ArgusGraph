import { post } from "../api.js";
import { toast } from "../toast.js";

export const JobsView = {
  ecosystem: "Maven",
  running: false,

  trigger() {
    this.running = true;
    post("/ingest/jobs/osv?ecosystem=" + encodeURIComponent(this.ecosystem.trim()))
      .then(() => toast.ok(`OSV fetch started for ${this.ecosystem.trim()}`))
      .catch((err) => toast.error(err))
      .finally(() => { this.running = false; m.redraw(); });
  },
  view() {
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
        m("p.muted", ["Queue status: ", m("a", { href: "http://localhost:15672", target: "_blank", rel: "noopener" },
            "RabbitMQ management UI")]),
      ]),
    ];
  },
};
