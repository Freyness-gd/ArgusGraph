// Cross-view navigation helpers: clickable entities that route to the right tab.

/** A clickable package-version purl → opens it in the Packages tab. */
export function pkgLink(purl, label) {
  return m("a.link", {
    href: "#",
    onclick: (e) => { e.preventDefault(); m.route.set("/packages", { purl }); },
  }, label || purl);
}

/** A clickable vulnerability id → opens it (filtered) in the Browse tab. */
export function vulnLink(id, label) {
  return m("a.link", {
    href: "#",
    onclick: (e) => { e.preventDefault(); m.route.set("/browse", { q: id }); },
  }, label || id);
}
