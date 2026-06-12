/* Thin Mithril wrapper around the vendored Chart.js v4 UMD build (global Chart).
   Dark-theme defaults set once at module load. */

Chart.defaults.color = "#8b949e";
Chart.defaults.borderColor = "#21262d";
Chart.defaults.font.family = "ui-monospace, SFMono-Regular, Menlo, Consolas, monospace";
Chart.defaults.animation = false;

export const SEVERITY_COLORS = {
  CRITICAL: "#da3633",
  HIGH: "#db6d28",
  MEDIUM: "#e3b341",
  LOW: "#30363d",
  NONE: "#21262d",
};

/* One canvas, one Chart instance. Create on mount, swap data on redraw, destroy on
   unmount — no leaked charts across route switches. */
export const ChartCanvas = {
  oncreate(vnode) {
    this.chart = new Chart(vnode.dom, {
      type: vnode.attrs.type,
      data: vnode.attrs.data,
      options: vnode.attrs.options || {},
    });
  },
  onupdate(vnode) {
    this.chart.data = vnode.attrs.data;
    this.chart.update();
  },
  onremove() {
    if (this.chart) this.chart.destroy();
  },
  view() {
    return m("canvas");
  },
};
