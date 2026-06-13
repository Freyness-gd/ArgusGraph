import { toast } from "./toast.js";
import { StatsView } from "./views/stats.js";
import { BrowseView } from "./views/browse.js";
import { PackagesView } from "./views/packages.js";
import { JobsView } from "./views/jobs.js";
import { ProjectsView, ProjectDetailView } from "./views/projects.js";
import { InferenceView } from "./views/inference.js";

const NAV = [
  { route: "/stats", label: "Stats" },
  { route: "/browse", label: "Browse" },
  { route: "/packages", label: "Packages" },
  { route: "/jobs", label: "Jobs" },
  { route: "/projects", label: "Projects" },
  { route: "/inference", label: "Inference" },
];

const Layout = {
  view(vnode) {
    return [
      m("aside.sidebar", [
        m(".brand", "ARGUSGRAPH"),
        m("nav", NAV.map((item) =>
          m(m.route.Link, {
            href: item.route,
            class: m.route.get().startsWith(item.route) ? "nav-item active" : "nav-item",
          }, item.label))),
      ]),
      m("main.content", vnode.children),
      toast.current && m(".toast", { class: toast.current.kind, onclick: () => toast.dismiss() }, [
        m("strong", toast.current.title),
        toast.current.detail ? m("div", toast.current.detail) : null,
      ]),
    ];
  },
};

const wrap = (component) => ({ render: (vnode) => m(Layout, m(component, vnode.attrs)) });

m.route(document.body, "/stats", {
  "/stats": wrap(StatsView),
  "/browse": wrap(BrowseView),
  "/packages": wrap(PackagesView),
  "/jobs": wrap(JobsView),
  "/projects": wrap(ProjectsView),
  "/projects/:id": wrap(ProjectDetailView),
  "/inference": wrap(InferenceView),
});
