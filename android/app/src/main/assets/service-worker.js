const CACHE_NAME = "ayan-beauty-salon-shell-v14";
const APP_SHELL = ["./", "./index.html", "./styles.css?v=ayan12", "./api-client.js?v=ayan5", "./app.js?v=ayan14", "./manifest.webmanifest", "./icon.svg"];
self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(CACHE_NAME).then((cache) => cache.addAll(APP_SHELL)));
  self.skipWaiting();
});
self.addEventListener("activate", (event) => {
  event.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key)))));
  self.clients.claim();
});
self.addEventListener("fetch", (event) => {
  if (event.request.method !== "GET") return;
  const path = new URL(event.request.url).pathname;
  // Never cache API responses. Wallet balances, bookings and authentication
  // state must always come from the server or show an explicit offline error.
  if (path.startsWith("/api/")) return;
  // The published salon address changes whenever the laptop restarts its free
  // tunnel, so it is always read from the network.
  if (path.endsWith("/api.json") || path.endsWith("/actuator/health")) return;
  event.respondWith(caches.match(event.request).then((cached) => cached || fetch(event.request).then((response) => {
    const copy = response.clone(); caches.open(CACHE_NAME).then((cache) => cache.put(event.request, copy)); return response;
  }).catch(() => caches.match("./index.html"))));
});
