/**
 * Override before loading page scripts:
 *   window.__DELTAVOICE_CONVEX_URL__ = 'https://your-deployment.convex.site';
 * Or set data-convex-url on <html>.
 */
(function () {
  const fromHtml = document.documentElement.getAttribute("data-convex-url");
  const fromGlobal = typeof window !== "undefined" && window.__DELTAVOICE_CONVEX_URL__;
  window.DELTAVOICE_CONVEX_URL = (fromGlobal || fromHtml || "https://kindred-curlew-363.eu-west-1.convex.site").replace(/\/$/, "");
})();
