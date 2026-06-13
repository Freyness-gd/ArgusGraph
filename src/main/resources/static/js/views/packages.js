import { get } from "../api.js";
import { toast } from "../toast.js";
import { debounce } from "../utils.js";
import { pkgLink, vulnLink } from "../nav.js";

function sevBadge(severity) {
  const s = severity || "NONE";
  return m("span.badge", { class: "sev-" + s.toLowerCase() }, s);
}

export const PackagesView = {
  items: [],
  total: 0,
  page: 0,
  size: 25,
  q: "",
  loading: false,
  detail: null,
  detailLoading: false,
  expanded: {},

  oninit() {
    this.debouncedSearch = debounce(() => this.load(0), 250);
    const purl = m.route.param("purl");
    if (purl) { this.open(purl); } else { this.load(0); }
  },

  onupdate() {
    const purl = m.route.param("purl");
    if (purl && purl !== this._openedFor) {
      this._openedFor = purl;
      this.open(purl);
    }
  },

  load(page) {
    this.loading = true;
    this.page = page;
    const params = new URLSearchParams({ page, size: this.size });
    if (this.q) params.set("q", this.q);
    get("/graph/packages?" + params)
      .then((result) => { this.items = result.items; this.total = result.total; })
      .catch((err) => toast.error(err))
      .finally(() => { this.loading = false; m.redraw(); });
  },

  open(packagePurl) {
    this._openedFor = packagePurl;
    this.detailLoading = true;
    this.detail = null;
    this.expanded = {};
    get("/graph/packages/detail?purl=" + encodeURIComponent(packagePurl))
      .then((d) => { this.detail = d; })
      .catch((err) => toast.error(err))
      .finally(() => { this.detailLoading = false; m.redraw(); });
  },

  back() {
    this.detail = null;
    this.detailLoading = false;
    this.expanded = {};
  },

  toggleVersion(purl) {
    if (this.expanded[purl]) {
      delete this.expanded[purl];
      m.redraw();
      return;
    }
    this.expanded[purl] = { loading: true, deps: [], transitive: [] };
    Promise.all([
      get("/graph/package-versions?purl=" + encodeURIComponent(purl)).catch(() => ({ dependencies: [] })),
      get("/inference/transitive?purls=" + encodeURIComponent(purl)).catch(() => []),
    ]).then(([pv, hits]) => {
      const transitive = (hits[0] && hits[0].vulnerabilities) || [];
      this.expanded[purl] = { loading: false, deps: pv.dependencies || [], transitive };
    }).finally(m.redraw);
  },

  versionPanel(v) {
    const x = this.expanded[v.purl];
    return m(".version-panel", [
      m(".card-label", "CVEs"),
      v.vulnerabilities.length === 0
        ? m("p.muted", "None known.")
        : m("table", [
            m("thead", m("tr", [m("th", "ID"), m("th", "Severity"), m("th", "CVSS"), m("th", "Summary")])),
            m("tbody", v.vulnerabilities.map((vuln) => m("tr", [
              m("td.mono", vulnLink(vuln.id)),
              m("td", sevBadge(vuln.severity)),
              m("td", vuln.cvssScore == null ? "—" : vuln.cvssScore.toFixed(1)),
              m("td.muted", vuln.summary || ""),
            ]))),
          ]),
      m(".card-label", "Dependencies"),
      x.loading
        ? m("p.muted", "Loading…")
        : x.deps.length === 0
          ? m("p.muted", "None recorded.")
          : m("ul.mono", x.deps.map((d) => m("li", [pkgLink(d.purl), d.scope ? ` (${d.scope})` : ""]))),
      m(".card-label", "Transitive exposure"),
      x.loading
        ? m("p.muted", "Loading…")
        : x.transitive.length === 0
          ? m("p.muted", "No transitive exposure.")
          : m("table", [
              m("thead", m("tr", [m("th", "ID"), m("th", "Severity"), m("th", "CVSS"), m("th", "Depth")])),
              m("tbody", x.transitive.map((t) => m("tr", [
                m("td.mono", vulnLink(t.id)),
                m("td", sevBadge(t.severity)),
                m("td", t.cvssScore == null ? "—" : t.cvssScore.toFixed(1)),
                m("td", t.depth),
              ]))),
            ]),
    ]);
  },

  detailView() {
    if (this.detailLoading) return m("p.muted", "Loading…");
    const d = this.detail;
    return [
      m("a.muted", { href: "#", onclick: (e) => { e.preventDefault(); this.back(); } }, "← packages"),
      m("h1", d.packagePurl),
      m("p.muted.mono", `type: ${d.type || "—"} · ${d.versions.length} versions`),
      m(".card", [
        m(".card-label", "Versions"),
        m("table", [
          m("thead", m("tr", [m("th", "Version"), m("th", "CVEs"), m("th", "")])),
          m("tbody", d.versions.map((v) => [
            m("tr.clickable", { onclick: () => this.toggleVersion(v.purl) }, [
              m("td.mono", v.version),
              m("td", v.vulnerabilities.length),
              m("td.muted", this.expanded[v.purl] ? "▼" : "▶"),
            ]),
            this.expanded[v.purl] && m("tr", m("td", { colspan: 3 }, this.versionPanel(v))),
          ])),
        ]),
      ]),
    ];
  },

  listView() {
    const pages = Math.max(1, Math.ceil(this.total / this.size));
    return [
      m("h1", "Packages"),
      m(".filters", [
        m("input", {
          placeholder: "search package purl / name…",
          value: this.q,
          oninput: (e) => { this.q = e.target.value; this.debouncedSearch(); },
          onkeydown: (e) => { if (e.key === "Enter") this.load(0); },
        }),
        m("button", { onclick: () => this.load(0) }, "Search"),
      ]),
      this.loading ? m("p.muted", "Loading…") : m("table", [
        m("thead", m("tr", ["Package", "Type", "Versions", "Vulnerabilities"].map((h) => m("th", h)))),
        m("tbody", this.items.length === 0
          ? m("tr", m("td.muted", { colspan: 4 }, "No packages found."))
          : this.items.map((p) => m("tr.clickable", { onclick: () => this.open(p.packagePurl) }, [
              m("td.mono", p.packagePurl),
              m("td.muted", p.type || "—"),
              m("td", p.versionCount),
              m("td", p.vulnerabilityCount),
            ]))),
      ]),
      m(".pager", [
        m("button", { disabled: this.page === 0, onclick: () => this.load(this.page - 1) }, "← prev"),
        m("span.muted", ` page ${this.page + 1} / ${pages} · ${this.total} total `),
        m("button", { disabled: this.page >= pages - 1, onclick: () => this.load(this.page + 1) }, "next →"),
      ]),
    ];
  },

  view() {
    if (this.detail || this.detailLoading) return this.detailView();
    return this.listView();
  },
};
