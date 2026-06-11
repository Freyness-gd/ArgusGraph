import { get, post, del } from "../api.js";
import { toast } from "../toast.js";

export const ProjectsView = {
  projects: [],
  loading: false,
  name: "",
  file: null,
  uploading: false,

  oninit() {
    this.load();
  },
  load() {
    this.loading = true;
    get("/projects")
      .then((projects) => { this.projects = projects; })
      .catch((err) => toast.error(err))
      .finally(() => { this.loading = false; m.redraw(); });
  },
  upload() {
    if (!this.file) return;
    this.uploading = true;
    this.file.text()
      .then((body) => post("/projects"
          + (this.name.trim() ? "?name=" + encodeURIComponent(this.name.trim()) : ""), JSON.parse(body)))
      .then((result) => {
        toast.ok(`Imported ${result.name} — ${result.dependencies} dependencies, ${result.skipped} skipped`);
        this.name = "";
        this.file = null;
        this.load();
      })
      .catch((err) => toast.error(err.title ? err : { title: "Import failed", detail: "File is not valid JSON." }))
      .finally(() => { this.uploading = false; m.redraw(); });
  },
  remove(project) {
    if (!window.confirm(`Delete project "${project.name}"? This cannot be undone.`)) return;
    del("/projects/" + project.id)
      .then(() => { toast.ok(`Deleted ${project.name}`); this.load(); })
      .catch((err) => toast.error(err));
  },
  view() {
    return [
      m("h1", "Projects"),
      m(".card", [
        m(".card-label", "Import SBOM"),
        m("p.muted", "Upload a CycloneDX JSON SBOM (mvn cyclonedx:makeBom, npm sbom, syft …). "
            + "Components are matched against the graph when you open the project."),
        m(".filters", [
          m("input", {
            placeholder: "project name (optional — falls back to SBOM metadata)",
            value: this.name,
            oninput: (e) => { this.name = e.target.value; },
          }),
          m("input", {
            type: "file",
            accept: ".json,application/json",
            onchange: (e) => { this.file = e.target.files[0] || null; },
          }),
          m("button", { disabled: !this.file || this.uploading, onclick: () => this.upload() },
            this.uploading ? "Importing…" : "Import"),
        ]),
      ]),
      this.loading ? m("p.muted", "Loading…") : m("table", [
        m("thead", m("tr", ["Name", "Created", "Dependencies", ""].map((h) => m("th", h)))),
        m("tbody", this.projects.length === 0
          ? m("tr", m("td.muted", { colspan: 4 }, "No projects imported yet."))
          : this.projects.map((p) => m("tr", [
              m("td", m(m.route.Link, { href: "/projects/" + p.id }, p.name)),
              m("td.muted", p.createdAt ? p.createdAt.slice(0, 10) : "—"),
              m("td", p.dependencyCount),
              m("td", m("button.danger", { onclick: () => this.remove(p) }, "Delete")),
            ]))),
      ]),
    ];
  },
};

export const ProjectDetailView = {
  id: null,
  details: null,
  notFound: false,
  showUnknown: false,

  oninit(vnode) {
    this.fetch(vnode.attrs.id);
  },
  onupdate(vnode) {
    if (vnode.attrs.id !== this.id) this.fetch(vnode.attrs.id);
  },
  fetch(id) {
    this.id = id;
    this.details = null;
    this.notFound = false;
    this.showUnknown = false;
    get("/projects/" + id)
      .then((details) => { this.details = details; })
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
    if (this.notFound) {
      return [m("h1", "Project"), m("p.muted", "No such project."),
        m(m.route.Link, { href: "/projects" }, "← back to projects")];
    }
    if (!this.details) {
      return m("p.muted", "Loading…");
    }
    const d = this.details;
    const affected = d.dependencies.filter((x) => x.verdict === "AFFECTED");
    const unknown = d.dependencies.filter((x) => x.verdict === "UNKNOWN");
    return [
      m("h1", d.name),
      m("p.muted", [m(m.route.Link, { href: "/projects" }, "← projects"),
        ` · imported ${d.createdAt ? d.createdAt.slice(0, 10) : "—"}`]),
      m(".card", [
        m(".card-label", "Match summary"),
        m(".badges", [
          m("span.badge.verdict-affected", `AFFECTED ${d.summary.affected}`),
          m("span.badge.verdict-clean", `CLEAN ${d.summary.clean}`),
          m("span.badge.verdict-unknown", `UNKNOWN ${d.summary.unknown}`),
          ...Object.entries(d.summary.bySeverity || {}).map(([severity, count]) =>
            m("span.badge", { class: "sev-" + severity.toLowerCase() }, `${severity} ${count}`)),
        ]),
      ]),
      m(".card", [
        m(".card-label", `Affected dependencies (${affected.length})`),
        affected.length === 0
          ? m("p.muted", "None — nothing in the graph hits this project.")
          : m("table", [
              m("thead", m("tr", [m("th", "Dependency"), m("th", "Vulnerabilities")])),
              m("tbody", affected.map((dep) => m("tr", [
                m("td.mono", dep.purl),
                m("td", dep.vulnerabilities.map((v) => m("p", [
                  m("span.badge", { class: "sev-" + (v.severity || "none").toLowerCase() },
                      v.severity || "NONE"),
                  m("span.mono", ` ${v.id} `),
                  m("span.muted", v.cvssScore == null ? "" : `(${v.cvssScore.toFixed(1)}) `),
                  m("span.muted", v.summary || ""),
                ]))),
              ]))),
            ]),
      ]),
      m(".card", [
        m(".card-label", `Unknown to the graph (${unknown.length})`),
        m("p.muted", "No data is not the same as safe — these purls were never ingested."),
        unknown.length > 0 && m("button",
          { onclick: () => { this.showUnknown = !this.showUnknown; } },
          this.showUnknown ? "Hide" : "Show"),
        this.showUnknown && m("ul.mono", unknown.map((dep) => m("li", dep.purl))),
      ]),
      m("p.muted", `${d.summary.clean} dependencies known to the graph with no matching vulnerabilities.`),
    ];
  },
};
