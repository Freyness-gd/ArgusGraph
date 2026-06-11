import { get } from "../api.js";
import { toast } from "../toast.js";

export const LookupView = {
  purl: "",
  result: null,
  notFound: false,

  search() {
    this.result = null;
    this.notFound = false;
    get("/graph/package-versions?purl=" + encodeURIComponent(this.purl.trim()))
      .then((result) => { this.result = result; })
      .catch((err) => {
        if (err.status === 404) {
          this.notFound = true;
        } else {
          toast.error(err);
        }
      })
      .finally(m.redraw);
  },
  view() {
    return [
      m("h1", "Package lookup"),
      m(".filters", [
        m("input", {
          placeholder: "pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1",
          value: this.purl,
          oninput: (e) => { this.purl = e.target.value; },
          onkeydown: (e) => { if (e.key === "Enter" && this.purl.trim()) this.search(); },
        }),
        m("button", { disabled: !this.purl.trim(), onclick: () => this.search() }, "Lookup"),
      ]),
      this.notFound && m("p.muted", "No such package version in the graph."),
      this.result && [
        m(".card", [
          m(".card-label", "Package version"),
          m("p.mono", this.result.purl),
          m("p.muted.mono", `package: ${this.result.packagePurl} · version: ${this.result.version}`),
        ]),
        m(".card", [
          m(".card-label", `Vulnerabilities (${this.result.vulnerabilities.length})`),
          this.result.vulnerabilities.length === 0
            ? m("p.muted", "None known.")
            : m("table", [
                m("thead", m("tr", [m("th", "ID"), m("th", "Severity"), m("th", "CVSS")])),
                m("tbody", this.result.vulnerabilities.map((v) => m("tr", [
                  m("td.mono", v.id),
                  m("td", m("span.badge", { class: "sev-" + (v.severity || "none").toLowerCase() },
                      v.severity || "NONE")),
                  m("td", v.cvssScore == null ? "—" : v.cvssScore.toFixed(1)),
                ]))),
              ]),
        ]),
        m(".card", [
          m(".card-label", `Dependencies (${this.result.dependencies.length})`),
          this.result.dependencies.length === 0
            ? m("p.muted", "None recorded.")
            : m("ul.mono", this.result.dependencies.map((d) =>
                m("li", `${d.purl}${d.scope ? " (" + d.scope + ")" : ""}`))),
        ]),
      ],
    ];
  },
};
