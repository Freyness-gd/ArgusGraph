import { get } from "../api.js";
import { toast } from "../toast.js";
import { debounce } from "../utils.js";
import { exposureGraph } from "../graph.js";

function sevBadge(severity) {
  const s = severity || "NONE";
  return m("span.badge", { class: "sev-" + s.toLowerCase() }, s);
}

export const DerivedView = {
  items: [],
  total: 0,
  page: 0,
  size: 25,
  q: "",
  loading: false,
  selected: null,
  chain: null,
  chainLoading: false,

  oninit() {
    this.debouncedSearch = debounce(() => this.load(0), 250);
    this.load(0);
  },

  load(page) {
    this.loading = true;
    this.page = page;
    const params = new URLSearchParams({ page, size: this.size });
    if (this.q) params.set("q", this.q);
    get("/inference/derived?" + params)
      .then((result) => { this.items = result.items; this.total = result.total; })
      .catch((err) => toast.error(err))
      .finally(() => { this.loading = false; m.redraw(); });
  },

  open(edge) {
    this.selected = edge;
    this.chain = null;
    this.chainLoading = true;
    get("/inference/chain?vulnId=" + encodeURIComponent(edge.vulnId)
        + "&purl=" + encodeURIComponent(edge.exposedPurl))
      .then((c) => { this.chain = c; })
      .catch((err) => toast.error(err))
      .finally(() => { this.chainLoading = false; m.redraw(); });
  },

  back() {
    this.selected = null;
    this.chain = null;
    this.chainLoading = false;
  },

  detailView() {
    const e = this.selected;
    const affectedPurl = this.chain && this.chain.affectedPurl;
    return [
      m("a.muted", { href: "#", onclick: (ev) => { ev.preventDefault(); this.back(); } }, "← derived"),
      m(".card", [
        m(".card-label", "Exposure"),
        m("p.mono", e.vulnId),
        m("p", [sevBadge(e.severity), " ", m("span.muted", e.summary || "")]),
        m("p.muted.mono", `exposed: ${e.exposedPurl} · depth ${e.depth} · rule ${e.inferredBy}`),
      ]),
      m(".card", [
        m(".card-label", "Exposure chain"),
        this.chainLoading
          ? m("p.muted", "Loading…")
          : [
              exposureGraph({
                path: (this.chain && this.chain.path) || [],
                vulnId: e.vulnId,
                affectedPurl,
                severity: e.severity,
              }),
              this.chain && this.chain.path && this.chain.path.length > 0
                ? m("ul.mono", this.chain.path.map((p) => m("li", p)))
                : null,
              affectedPurl ? m("p.muted.mono", `affects: ${affectedPurl}`) : null,
            ],
      ]),
    ];
  },

  listView() {
    const pages = Math.max(1, Math.ceil(this.total / this.size));
    return [
      m("h1", "Derived knowledge"),
      m("p.muted", "Inferred transitive exposure — each row is a vulnerability reaching a "
          + "package-version through the dependency graph. Click a row to see the chain that explains it."),
      m(".filters", [
        m("input", {
          placeholder: "search vuln id / package / summary…",
          value: this.q,
          oninput: (e) => { this.q = e.target.value; this.debouncedSearch(); },
          onkeydown: (e) => { if (e.key === "Enter") this.load(0); },
        }),
        m("button", { onclick: () => this.load(0) }, "Search"),
      ]),
      this.loading ? m("p.muted", "Loading…") : m("table", [
        m("thead", m("tr",
          ["Vulnerability", "Severity", "CVSS", "Exposed package-version", "Depth", "Rule"].map((h) => m("th", h)))),
        m("tbody", this.items.length === 0
          ? m("tr", m("td.muted", { colspan: 6 }, "No derived edges found."))
          : this.items.map((e) => m("tr.clickable", { onclick: () => this.open(e) }, [
              m("td.mono", e.vulnId),
              m("td", sevBadge(e.severity)),
              m("td", e.cvssScore == null ? "—" : e.cvssScore.toFixed(1)),
              m("td.mono", e.exposedPurl),
              m("td", e.depth),
              m("td.muted", e.inferredBy),
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
    if (this.selected) return this.detailView();
    return this.listView();
  },
};
