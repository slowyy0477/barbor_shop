/*
 * Small browser/Android API adapter. It is dormant when no HTTPS base URL is
 * configured, so the local release APK remains usable for offline setup. In API
 * mode balances and ledger data are read from the server; session tokens live in
 * sessionStorage only and are never used as a money source of truth.
 */
(function (global) {
  "use strict";
  const metaValue = (name) => {
    try { return document.querySelector(`meta[name="${name}"]`)?.getAttribute("content") || ""; } catch (_) { return ""; }
  };
  const queryValue = (name) => {
    try { return new URLSearchParams(global.location?.search || "").get(name) || ""; } catch (_) { return ""; }
  };
  // Resolve this on every call. MainActivity injects the values after the
  // bundled page has loaded, while a browser/PWA can provide them in the URL
  // or metadata before app.js starts.
  function config() {
    const base = String(global.__AYAN_API_BASE_URL__ || queryValue("apiBaseUrl") || metaValue("api-base-url") || "")
      .trim().replace(/\/+$/, "");
    const salon = String(global.__AYAN_SALON_ID__ || queryValue("salonId") || metaValue("salon-id") || "").trim();
    return { base, salon };
  }
  const TOKEN_KEY = "ayan-session-token";
  const SESSION_KEY = "ayan-session-meta";
  /**
   * Stable per-install key. The server hashes it so one phone can create only
   * one customer account, and it never stores the raw value. localStorage is
   * tried first, then sessionStorage, and a value that cannot be persisted at
   * all still stays constant for this page load instead of blocking sign-up.
   */
  const DEVICE_KEY = "ayan-device-id";
  const DEVICE_PATTERN = /^[A-Za-z0-9_-]{8,80}$/;
  let deviceKeyCache = "";

  function randomDeviceKey() {
    try {
      if (global.crypto && typeof global.crypto.randomUUID === "function") return global.crypto.randomUUID();
    } catch (_) { /* fall through to the manual generator */ }
    const bytes = new Uint8Array(16);
    let filled = false;
    try { if (global.crypto && global.crypto.getRandomValues) { global.crypto.getRandomValues(bytes); filled = true; } } catch (_) { filled = false; }
    if (!filled) for (let index = 0; index < bytes.length; index++) bytes[index] = Math.floor(Math.random() * 256);
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    let out = "";
    for (let index = 0; index < bytes.length; index++) {
      out += bytes[index].toString(16).padStart(2, "0");
      if (index === 3 || index === 5 || index === 7 || index === 9) out += "-";
    }
    return out;
  }

  function deviceId() {
    if (deviceKeyCache) return deviceKeyCache;
    for (const store of [() => global.localStorage, () => global.sessionStorage]) {
      try {
        const storage = store();
        if (!storage) continue;
        const stored = storage.getItem(DEVICE_KEY);
        if (stored && DEVICE_PATTERN.test(stored)) { deviceKeyCache = stored; return stored; }
      } catch (_) { /* private mode may deny storage */ }
    }
    const created = randomDeviceKey();
    deviceKeyCache = created;
    try { global.localStorage?.setItem(DEVICE_KEY, created); } catch (_) { /* ignore */ }
    try { global.sessionStorage?.setItem(DEVICE_KEY, created); } catch (_) { /* ignore */ }
    return created;
  }

  function enabled() {
    const base = config().base;
    if (!base) return false;
    try {
      const parsed = new URL(base);
      if (!parsed.host || parsed.username || parsed.password || parsed.search || parsed.hash) return false;
      if (parsed.protocol === "https:") return true;
      return parsed.protocol === "http:" && ["localhost", "127.0.0.1", "10.0.2.2"].includes(parsed.hostname.toLowerCase());
    } catch (_) { return false; }
  }
  function token() { try { return sessionStorage.getItem(TOKEN_KEY) || ""; } catch (_) { return ""; } }
  function metadata() { try { return JSON.parse(sessionStorage.getItem(SESSION_KEY) || "null"); } catch (_) { return null; } }
  function setSession(value) {
    try {
      if (!value) { sessionStorage.removeItem(TOKEN_KEY); sessionStorage.removeItem(SESSION_KEY); return; }
      sessionStorage.setItem(TOKEN_KEY, String(value.accessToken || ""));
      sessionStorage.setItem(SESSION_KEY, JSON.stringify(value));
    } catch (_) { /* private mode may deny storage; requests still fail closed */ }
  }
  function clearSession() { setSession(null); }
  function id(value) { return value || config().salon; }
  function makeKey(prefix) { return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`; }
  async function request(path, options = {}) {
    if (!enabled()) throw new Error("Online salon service is not configured");
    const base = config().base;
    const headers = new Headers(options.headers || {});
    headers.set("Accept", "application/json");
    // Let the browser/WebView add the multipart boundary for media uploads.
    // JSON remains the default for all other API mutations.
    const isForm = typeof FormData !== "undefined" && options.body instanceof FormData;
    if (options.body !== undefined && !isForm) headers.set("Content-Type", "application/json");
    // Only the authentication routes need the device key, and sending it there
    // keeps every other request a plain single round trip.
    if (path.startsWith("/api/auth/")) headers.set("X-Device-Id", deviceId());
    const bearer = token(); if (bearer) headers.set("Authorization", `Bearer ${bearer}`);
    const response = await fetch(`${base}${path}`, { ...options, headers, credentials: "omit" });
    let payload = null; try { payload = await response.json(); } catch (_) { /* empty response */ }
    if (!response.ok) {
      const error = new Error(payload?.message || `Request failed (${response.status})`);
      error.status = response.status; error.code = payload?.code || "REQUEST_FAILED"; throw error;
    }
    return payload;
  }
  /**
   * SMS verification codes are retired. These three stay in the public surface
   * so an old cached page fails with a plain explanation instead of a confusing
   * network error, but they never call the server.
   */
  const SMS_RETIRED = "SMS codes are switched off. Please update the app and sign in with your mobile number and password.";
  async function requestOtp() { throw new Error(SMS_RETIRED); }
  async function verifyOtp() { throw new Error(SMS_RETIRED); }
  async function registerCustomer() { throw new Error(SMS_RETIRED); }
  /**
   * Creates the customer account from the mobile number and the chosen
   * password. No verification code is involved, so the server needs the device
   * key to keep one phone to one account.
   */
  async function signupCustomer(phone, name, password, marketingConsent, targetSalonId) {
    const value = await request("/api/auth/customer/signup", {
      method: "POST",
      body: JSON.stringify({ salonId: id(targetSalonId), phone, name, password, marketingConsent: marketingConsent === true })
    });
    setSession(value); return value;
  }
  /**
   * Mobile number plus password sign-in. The salon owner resets a forgotten
   * password from the Owner workspace, because no SMS code can be sent.
   */
  async function verifyPin(phone, pin, targetSalonId) {
    const value = await request("/api/auth/pin/verify", { method: "POST", body: JSON.stringify({ salonId: id(targetSalonId), phone, pin }) });
    setSession(value); return value;
  }
  async function setPin(pin, currentPin, targetSalonId) {
    return request(`/api/salons/${encodeURIComponent(id(targetSalonId))}/me/pin`, {
      method: "PUT", body: JSON.stringify({ pin, currentPin: currentPin || null })
    });
  }
  async function logout() {
    try { if (token()) await request("/api/auth/logout", { method: "POST" }); } finally { clearSession(); }
  }
  async function publicCatalog(targetSalonId) { return request(`/api/public/salons/${encodeURIComponent(id(targetSalonId))}/catalog`); }
  async function me(targetSalonId) { return request(`/api/salons/${encodeURIComponent(id(targetSalonId))}/me`); }
  async function wallet(targetSalonId) { return request(`/api/salons/${encodeURIComponent(id(targetSalonId))}/me/wallet`); }
  async function walletTransactions(targetSalonId) { return request(`/api/salons/${encodeURIComponent(id(targetSalonId))}/me/wallet/transactions`); }
  async function bookings(targetSalonId) { return request(`/api/salons/${encodeURIComponent(id(targetSalonId))}/me/bookings`); }
  async function reminders(targetSalonId) { return request(`/api/salons/${encodeURIComponent(id(targetSalonId))}/me/reminders`); }
  async function updateProfile(value, targetSalonId) {
    return request(`/api/salons/${encodeURIComponent(id(targetSalonId))}/me`, { method: "PATCH", body: JSON.stringify(value) });
  }
  async function paymentMethods(targetSalonId, includeInactive = false) {
    const suffix = includeInactive ? "?includeInactive=true" : "";
    return request(`/api/salons/${encodeURIComponent(id(targetSalonId))}/payment-methods${suffix}`);
  }
  async function staff(targetSalonId, includeInactive = false) {
    const suffix = includeInactive ? "?includeInactive=true" : "";
    return request(`/api/salons/${encodeURIComponent(id(targetSalonId))}/staff${suffix}`);
  }
  async function settings(targetSalonId) { return request(`/api/salons/${encodeURIComponent(id(targetSalonId))}/settings`); }
  async function services(targetSalonId, includeInactive = false) {
    const suffix = includeInactive ? "?includeInactive=true" : "";
    return request(`/api/salons/${encodeURIComponent(id(targetSalonId))}/services${suffix}`);
  }
  async function haircutStyles(targetSalonId, includeInactive = false) {
    const suffix = includeInactive ? "?includeInactive=true" : "";
    return request(`/api/salons/${encodeURIComponent(id(targetSalonId))}/haircut-styles${suffix}`);
  }
  function resolveUrl(value) {
    const raw = String(value || "").trim();
    if (!raw) return "";
    try { return new URL(raw, `${config().base}/`).href; } catch (_) { return ""; }
  }
  async function uploadMedia(file, purpose, targetSalonId) {
    if (!file) throw new Error("Choose an image file first.");
    const normalizedPurpose = String(purpose || "").trim().toUpperCase();
    if (!["LOGO", "HAIRCUT_STYLE"].includes(normalizedPurpose)) throw new Error("Unsupported image purpose.");
    const form = new FormData();
    const fileName = String(file.name || `${normalizedPurpose.toLowerCase()}.webp`);
    form.append("file", file, fileName);
    return request(`/api/salons/${encodeURIComponent(id(targetSalonId))}/media?purpose=${encodeURIComponent(normalizedPurpose)}`, {
      method: "POST", body: form
    });
  }
  async function createBooking(value, targetSalonId) {
    return request(`/api/salons/${encodeURIComponent(id(targetSalonId))}/bookings`, {
      method: "POST", headers: { "Idempotency-Key": value.idempotencyKey || makeKey("booking") }, body: JSON.stringify(value)
    });
  }
  async function submitDeposit(value, targetSalonId) {
    return request(`/api/salons/${encodeURIComponent(id(targetSalonId))}/deposits`, {
      method: "POST", headers: { "Idempotency-Key": value.idempotencyKey || makeKey("deposit") }, body: JSON.stringify(value)
    });
  }
  async function addOns(serviceId = null, targetSalonId, includeInactive = false) {
    const params = new URLSearchParams();
    if (serviceId) params.set("serviceId", String(serviceId));
    if (includeInactive) params.set("includeInactive", "true");
    const query = params.toString();
    return request(`/api/salons/${encodeURIComponent(id(targetSalonId))}/add-ons${query ? `?${query}` : ""}`);
  }
  async function ownerOverview(targetSalonId) { return request(`/api/salons/${encodeURIComponent(id(targetSalonId))}/dashboard/overview`); }
  async function ownerMutation(path, method = "POST", body = undefined, keyPrefix = "owner") {
    const options = { method, headers: { "Idempotency-Key": makeKey(keyPrefix) } };
    if (body !== undefined) options.body = JSON.stringify(body);
    return request(path, options);
  }
  async function ownerBookingStatus(bookingId, status, reason, targetSalonId) {
    return ownerMutation(`/api/salons/${encodeURIComponent(id(targetSalonId))}/bookings/${encodeURIComponent(bookingId)}/status`, "POST", { status, reason: reason || null }, "booking-status");
  }
  async function ownerCompleteBooking(bookingId, targetSalonId) {
    return ownerMutation(`/api/salons/${encodeURIComponent(id(targetSalonId))}/bookings/${encodeURIComponent(bookingId)}/complete`, "POST", undefined, "service-complete");
  }
  async function ownerReviewDeposit(depositId, approve, reason, targetSalonId) {
    const base = `/api/salons/${encodeURIComponent(id(targetSalonId))}/deposits/${encodeURIComponent(depositId)}`;
    return ownerMutation(`${base}/${approve ? "approve" : "reject"}`, "POST", approve ? undefined : { reason: reason || "" }, approve ? "deposit-approve" : "deposit-reject");
  }
  async function ownerReviewWithdrawal(withdrawalId, action, reason, targetSalonId) {
    const base = `/api/salons/${encodeURIComponent(id(targetSalonId))}/withdrawals/${encodeURIComponent(withdrawalId)}`;
    return ownerMutation(`${base}/${action}`, "POST", action === "reject" ? { reason: reason || "" } : undefined, `withdrawal-${action}`);
  }
  async function ownerUpdatePaymentMethod(providerCode, value, targetSalonId) {
    return ownerMutation(`/api/salons/${encodeURIComponent(id(targetSalonId))}/payment-methods/${encodeURIComponent(providerCode)}`, "PUT", value, "payment-method");
  }
  async function ownerUpdateBranding(value, targetSalonId) {
    return ownerMutation(`/api/salons/${encodeURIComponent(id(targetSalonId))}/branding`, "PUT", value, "branding");
  }
  async function ownerSaveHaircutStyle(styleId, value, targetSalonId) {
    const suffix = styleId ? `/${encodeURIComponent(styleId)}` : "";
    return ownerMutation(`/api/salons/${encodeURIComponent(id(targetSalonId))}/haircut-styles${suffix}`, styleId ? "PUT" : "POST", value, "haircut-style");
  }
  async function ownerArchiveHaircutStyle(styleId, targetSalonId) {
    return ownerMutation(`/api/salons/${encodeURIComponent(id(targetSalonId))}/haircut-styles/${encodeURIComponent(styleId)}`, "DELETE", undefined, "haircut-style-archive");
  }
  async function resetCustomerPassword(customerId, password, targetSalonId) {
    const value = await request(`/api/salons/${encodeURIComponent(id(targetSalonId))}/customers/${encodeURIComponent(customerId)}/password-reset`, {
      method: "POST", body: JSON.stringify({ password })
    });
    return value;
  }
  const api = { enabled, token, metadata, setSession, clearSession, request, resolveUrl, uploadMedia, deviceId, requestOtp, verifyOtp, registerCustomer, signupCustomer, verifyPin, setPin, resetCustomerPassword, logout, publicCatalog, me, updateProfile, wallet, walletTransactions, bookings, reminders, paymentMethods, staff, settings, services, haircutStyles, createBooking, submitDeposit, addOns, ownerOverview, ownerMutation, ownerBookingStatus, ownerCompleteBooking, ownerReviewDeposit, ownerReviewWithdrawal, ownerUpdatePaymentMethod, ownerUpdateBranding, ownerSaveHaircutStyle, ownerArchiveHaircutStyle, makeKey };
  Object.defineProperties(api, {
    configured: { enumerable: true, get: () => config().base },
    salonId: { enumerable: true, get: () => config().salon }
  });
  global.AyanApi = Object.freeze(api);
})(typeof window !== "undefined" ? window : globalThis);
