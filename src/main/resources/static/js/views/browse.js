import { get } from "../api.js";
import { toast } from "../toast.js";

const SEVERITIES = ["", "CRITICAL", "HIGH", "MEDIUM", "LOW", "NONE"];

export const BrowseView = {
  items: [],
  total: 0,
  page: 0,
  size: 25,
  severity: "",
  q: "",
  loading: false,

  oninit() {
    this.load(0);
  },
  load(page) {
    this.loading = true;
    this.page = page;
    const params = new URLSearchParams({ page, size: this.size });
    if (this.severity) params.set("severity", this.severity);
    if (this.q) params.set("q", this.q);
    get("/graph/vulnerabilities?" + params)
      .then((result) => { this.items = result.items; this.total = result.total; })
      .catch((err) => toast.error(err))
      .finally(() => { this.loading = false; m.redraw(); });
  },
  view() {
    const pages = Math.max(1, Math.ceil(this.total / this.size));
    return [
      m("h1", "Browse vulnerabilities"),
      m(".filters", [
        m("input", {
          placeholder: "search id / summary…",
          value: this.q,
          oninput: (e) => { this.q = e.target.value; },
          onkeydown: (e) => { if (e.key === "Enter") this.load(0); },
        }),
        m("select", {
          value: this.severity,
          onchange: (e) => { this.severity = e.target.value; this.load(0); },
        }, SEVERITIES.map((s) => m("option", { value: s }, s || "severity: all"))),
        m("button", { onclick: () => this.load(0) }, "Search"),
      ]),
      this.loading ? m("p.muted", "Loading…") : m("table", [
        m("thead", m("tr", ["ID", "Severity", "CVSS", "Summary", "Published"].map((h) => m("th", h)))),
        m("tbody", this.items.length === 0
          ? m("tr", m("td.muted", { colspan: 5 }, "No vulnerabilities found."))
          : this.items.map((v) => m("tr", [
              m("td.mono", v.id),
              m("td", m("span.badge", { class: "sev-" + (v.severity || "none").toLowerCase() },
                  v.severity || "NONE")),
              m("td", v.cvssScore == null ? "—" : v.cvssScore.toFixed(1)),
              m("td.muted", v.summary || ""),
              m("td.muted", v.published ? v.published.slice(0, 10) : "—"),
            ]))),
      ]),
      m(".pager", [
        m("button", { disabled: this.page === 0, onclick: () => this.load(this.page - 1) }, "← prev"),
        m("span.muted", ` page ${this.page + 1} / ${pages} · ${this.total} total `),
        m("button", { disabled: this.page >= pages - 1, onclick: () => this.load(this.page + 1) },
          "next →"),
      ]),
    ];
  },
};
