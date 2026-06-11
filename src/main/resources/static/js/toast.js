/* One global toast at a time — enough for a four-tab dashboard. */
export const toast = {
  current: null,
  timer: null,
  show(kind, title, detail) {
    this.current = { kind, title, detail };
    m.redraw();
    clearTimeout(this.timer);
    this.timer = setTimeout(() => { this.current = null; m.redraw(); }, 6000);
  },
  error(err) { this.show("error", err.title || "Error", err.detail || String(err)); },
  ok(title) { this.show("ok", title, ""); },
  dismiss() { clearTimeout(this.timer); this.current = null; },
};
