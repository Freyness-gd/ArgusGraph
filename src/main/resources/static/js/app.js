import { toast } from "./toast.js";
import { StatsView } from "./views/stats.js";
import { BrowseView } from "./views/browse.js";
import { LookupView } from "./views/lookup.js";
import { JobsView } from "./views/jobs.js";

/* The future Projects tab is one more entry here — nothing else changes. */
const NAV = [
  { route: "/stats", label: "Stats" },
  { route: "/browse", label: "Browse" },
  { route: "/lookup", label: "Lookup" },
  { route: "/jobs", label: "Jobs" },
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

const wrap = (component) => ({ render: () => m(Layout, m(component)) });

m.route(document.body, "/stats", {
  "/stats": wrap(StatsView),
  "/browse": wrap(BrowseView),
  "/lookup": wrap(LookupView),
  "/jobs": wrap(JobsView),
});
