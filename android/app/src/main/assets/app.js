const STORAGE_KEY = "ayan-beauty-salon-os-v2";
const DEFAULT_OWNER_ACCESS_CODE = "530146";
const OWNER_SESSION_MAX_MS = 15 * 60 * 1000;
const APP_SCHEMA_VERSION = 5;
const DEFAULT_THEME_COLOR = "#FF9E3B";
const DARK_MODE_STORAGE_KEY = "ayan-beauty-salon-dark-mode";
const MAX_IMAGE_BYTES = 2 * 1024 * 1024;
const API_QUERY_MODE = typeof window !== "undefined" && /(?:^|[?&])apiBaseUrl=[^&]+/i.test(window.location.search || "");
// Production and the installed PWA start empty. Test fixtures are opt-in only
// on a local development origin so a public release URL cannot expose them.
const QA_MODE = typeof window !== "undefined"
  && /^(localhost|127\.0\.0\.1|\[::1\])$/i.test(window.location.hostname || "")
  && /(?:^|[?&])mode=qa(?:&|$)/i.test(window.location.search || "");
const RELEASE_MODE = !QA_MODE || API_QUERY_MODE;
const ACTIVE_STORAGE_KEY = RELEASE_MODE ? `${STORAGE_KEY}-release` : STORAGE_KEY;
const START_LOOKUP = typeof window !== "undefined" && /(?:^|[?&])lookup=1(?:&|$)/i.test(window.location.search || "");

const today = new Date();
const iso = (date) => new Date(date).toISOString().slice(0, 10);
const addDays = (date, days) => { const d = new Date(date); d.setDate(d.getDate() + days); return iso(d); };
const money = (value) => `PKR ${Number(value || 0).toLocaleString("en-PK")}`;
const uid = (prefix) => `${prefix}_${Math.random().toString(36).slice(2, 9)}`;
const dateLabel = (value, options = { day: "numeric", month: "short" }) => value ? new Date(`${value}T12:00:00`).toLocaleDateString("en-PK", options) : "-";
const timeLabel = (value) => value || "-";

function safeHexColor(value, fallback = DEFAULT_THEME_COLOR) {
  const raw = String(value || "").trim();
  return /^#[0-9a-f]{6}$/i.test(raw) ? raw.toUpperCase() : fallback;
}

function hexRgb(value) {
  const hex = safeHexColor(value).slice(1);
  return { r: parseInt(hex.slice(0, 2), 16), g: parseInt(hex.slice(2, 4), 16), b: parseInt(hex.slice(4, 6), 16) };
}

function rgbaColor(value, alpha) {
  const { r, g, b } = hexRgb(value);
  return `rgba(${r}, ${g}, ${b}, ${Math.max(0, Math.min(1, Number(alpha) || 0))})`;
}

function mixHexColor(value, target, amount) {
  const source = hexRgb(value); const destination = hexRgb(target); const ratio = Math.max(0, Math.min(1, Number(amount) || 0));
  const channel = (from, to) => Math.round(from + (to - from) * ratio).toString(16).padStart(2, "0");
  return `#${channel(source.r, destination.r)}${channel(source.g, destination.g)}${channel(source.b, destination.b)}`.toUpperCase();
}

function safeImageDataUrl(value) {
  const raw = String(value || "");
  return /^data:image\/(?:png|jpe?g|webp|gif);base64,[a-z0-9+/=]+$/i.test(raw) ? raw : "";
}

function compressImageFile(file, maxDimension = 720) {
  return new Promise((resolve, reject) => {
    if (!file || !String(file.type || "").toLowerCase().startsWith("image/")) { reject(new Error("Choose an image file.")); return; }
    if (Number(file.size || 0) > MAX_IMAGE_BYTES) { reject(new Error("Choose an image smaller than 2 MB.")); return; }
    const reader = new FileReader();
    reader.onerror = () => reject(new Error("The image could not be read."));
    reader.onload = () => {
      const image = new Image();
      image.onerror = () => reject(new Error("The image could not be decoded."));
      image.onload = () => {
        const scale = Math.min(1, maxDimension / Math.max(image.width || 1, image.height || 1));
        const canvas = document.createElement("canvas");
        canvas.width = Math.max(1, Math.round((image.width || 1) * scale));
        canvas.height = Math.max(1, Math.round((image.height || 1) * scale));
        const context = canvas.getContext("2d");
        if (!context) { reject(new Error("Image processing is unavailable on this device.")); return; }
        context.drawImage(image, 0, 0, canvas.width, canvas.height);
        resolve(canvas.toDataURL("image/webp", 0.82));
      };
      image.src = String(reader.result || "");
    };
    reader.readAsDataURL(file);
  });
}

function dataUrlToBlob(value) {
  const raw = String(value || "");
  const match = raw.match(/^data:(image\/[a-z0-9.+-]+);base64,([a-z0-9+/=]+)$/i);
  if (!match || typeof atob !== "function" || typeof Blob === "undefined") return null;
  const binary = atob(match[2]);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
  return new Blob([bytes], { type: match[1].toLowerCase() });
}

function apiAssetUri(value) {
  try {
    return window.AyanApi?.resolveUrl?.(value) || "";
  } catch (_) { return ""; }
}

function remoteImageSource(value) {
  const raw = String(value || "").trim();
  if (!raw) return "";
  const resolved = apiModeEnabled() ? (apiAssetUri(raw) || raw) : raw;
  return safeImageSource(resolved);
}

// The offline shell keeps only a digest of the local owner code. Real deployments
// must replace this gate with server-issued authentication before handling money.
function hashOwnerAccessCode(value) {
  let hash = 2166136261;
  for (const character of String(value || "")) {
    hash ^= character.charCodeAt(0);
    hash = Math.imul(hash, 16777619);
  }
  hash ^= hash >>> 13;
  hash = Math.imul(hash, 0x5bd1e995);
  hash ^= hash >>> 15;
  return (hash >>> 0).toString(16).padStart(8, "0");
}

function validOwnerAccessCode(value) {
  return /^[0-9A-Za-z]{6,32}$/.test(String(value || ""));
}

function validOwnerAccessCodeHash(value) {
  return /^[0-9a-f]{8}$/i.test(String(value || ""));
}

// Sign-in password helpers. A customer signs in with a mobile number plus a
// 10 to 20 character password that mixes letters and digits. SMS verification
// codes are switched off, so a password is the only secret in the app.
// The offline shell keeps only a salted digest in local storage; the online
// server keeps the authoritative PBKDF2 digest and its own lockout counters.
const SIGN_IN_PIN_PATTERN = /^[A-Za-z0-9@#$%^&*!._+-]{10,20}$/;
const SIGN_IN_PASSWORD_RULE = "Use 10 to 20 characters with at least one letter and one number.";
const SIGN_IN_PIN_MAX_ATTEMPTS = 5;
const SIGN_IN_PIN_LOCK_MS = 15 * 60 * 1000;
// Deliberately slow for a phone: this runs once per password entry, not per screen.
const SIGN_IN_PIN_HASH_ROUNDS = 20000;

function validSignInPin(value) {
  const text = String(value == null ? "" : value).trim();
  if (!SIGN_IN_PIN_PATTERN.test(text)) return false;
  return /[A-Za-z]/.test(text) && /\d/.test(text);
}

function newSignInPinSalt() {
  const bytes = new Uint8Array(8);
  try {
    window.crypto.getRandomValues(bytes);
  } catch (_) {
    for (let index = 0; index < bytes.length; index += 1) bytes[index] = Math.floor(Math.random() * 256);
  }
  return Array.from(bytes, (value) => value.toString(16).padStart(2, "0")).join("");
}

function mixSignInPin(seed) {
  let hash = seed >>> 0;
  hash ^= hash >>> 13;
  hash = Math.imul(hash, 0x5bd1e995);
  hash ^= hash >>> 15;
  return (hash >>> 0).toString(16).padStart(8, "0");
}

function hashSignInPin(pin, salt) {
  let first = 2166136261;
  let second = 0x9e3779b9;
  for (const character of `${salt}|${String(pin || "").trim()}|ayan-pin`) {
    const code = character.charCodeAt(0);
    first ^= code;
    first = Math.imul(first, 16777619);
    second = Math.imul(second ^ code, 2246822519);
  }
  // A 10 to 20 character password is the only secret, so a single fast pass would be
  // guessable by anyone holding the phone's storage. Stretching the digest keeps
  // the offline sign-in check honest without a noticeable wait on a phone.
  for (let round = 0; round < SIGN_IN_PIN_HASH_ROUNDS; round++) {
    first = Math.imul(first ^ (second >>> 7), 2654435761);
    second = Math.imul(second ^ (first >>> 11), 2246822519);
    first ^= first >>> 15;
    second ^= second >>> 13;
  }
  return `${mixSignInPin(first)}${mixSignInPin(second)}`;
}

function customerHasPin(target) {
  return !!target && /^[0-9a-f]{16}$/i.test(String(target.pinHash || "")) && !!target.pinSalt;
}

function verifyCustomerPin(target, pin) {
  return customerHasPin(target) && hashSignInPin(pin, target.pinSalt) === String(target.pinHash).toLowerCase();
}

function pinGuard(customerId) {
  if (!state.pinGuards || typeof state.pinGuards !== "object") state.pinGuards = {};
  const guard = state.pinGuards[customerId] || { attempts: 0, lockedUntil: 0 };
  state.pinGuards[customerId] = guard;
  return guard;
}

function pinLockRemainingMinutes(customerId) {
  const guard = pinGuard(customerId);
  return Math.max(0, Math.ceil((Number(guard.lockedUntil || 0) - Date.now()) / 60000));
}

function registerPinFailure(customerId) {
  const guard = pinGuard(customerId);
  guard.attempts = Number(guard.attempts || 0) + 1;
  if (guard.attempts >= SIGN_IN_PIN_MAX_ATTEMPTS) {
    guard.attempts = 0;
    guard.lockedUntil = Date.now() + SIGN_IN_PIN_LOCK_MS;
  }
  saveState();
  return guard;
}

function clearPinFailures(customerId) {
  const guard = pinGuard(customerId);
  guard.attempts = 0;
  guard.lockedUntil = 0;
  saveState();
}

function assignCustomerPin(target, pin) {
  target.pinSalt = newSignInPinSalt();
  target.pinHash = hashSignInPin(pin, target.pinSalt);
  target.pinUpdatedAt = new Date().toISOString();
  clearPinFailures(target.id);
}

function resetCustomerPin(target) {
  delete target.pinSalt;
  delete target.pinHash;
  delete target.pinUpdatedAt;
  clearPinFailures(target.id);
}

/** True only inside the installed Android shell, where the bridge is injected. */
function nativeShellAvailable() {
  return typeof window !== "undefined" && !!window.AyanSalonNative;
}

function nativeServerUrl() {
  try { return String(window.AyanSalonNative?.getServerUrl?.() || "").trim(); } catch (_) { return ""; }
}

/** The salon name already stored inside the installed Android shell, if any. */
function nativeSalonName() {
  try { return String(window.AyanSalonNative?.getSalonName?.() || "").trim(); } catch (_) { return ""; }
}

// Production defaults contain only salon configuration. Browser-only test records
// live in qa-seed.js and are never copied into the Android asset bundle.
const baseState = () => ({
  schemaVersion: APP_SCHEMA_VERSION,
  releaseMode: false,
  // A fresh install ships with no salon name, phone or address: the owner fills
  // those in from Owner settings and every screen picks them up immediately.
  salon: { id: "salon_local", name: "", tagline: "Bookings, wallet and reminders", phone: "", address: "", hours: "08:00 - 23:00", timezone: "Asia/Karachi", currency: "PKR", themeColor: DEFAULT_THEME_COLOR, logoDataUrl: "" },
  settings: { walletTopUp: 500, walletBonus: 50, walletExpiryDays: 180, reminderDefaultDays: 25, referralEnabled: true, referralReferrerReward: 100, referralNewCustomerDiscount: 100, monthlyReferralLimit: 30, cancellationHours: 4, promotionsEnabled: true, withdrawalMinimum: 100, withdrawalDailyLimit: 10000, ownerAccessCodeHash: hashOwnerAccessCode(DEFAULT_OWNER_ACCESS_CODE) },
  services: [
    { id: "svc_haircut", name: "Classic Haircut", category: "Hair", price: 800, duration: 35, repeatDays: 25, active: true },
    { id: "svc_beard", name: "Beard Trim", category: "Beard", price: 450, duration: 20, repeatDays: 12, active: true },
    { id: "svc_combo", name: "Haircut + Beard", category: "Hair", price: 1100, duration: 50, repeatDays: 28, active: true },
    { id: "svc_kids", name: "Kids Cut", category: "Hair", price: 600, duration: 30, repeatDays: 30, active: true },
    { id: "svc_colour", name: "Hair Colour", category: "Colour", price: 2800, duration: 120, repeatDays: 35, active: true },
    { id: "svc_facial", name: "Signature Facial", category: "Skin", price: 1500, duration: 55, repeatDays: 35, active: true },
    { id: "svc_massage", name: "Head Massage", category: "Wellness", price: 700, duration: 30, repeatDays: 18, active: true },
    { id: "svc_wash", name: "Wash & Blow Dry", category: "Hair", price: 900, duration: 45, repeatDays: 28, active: true },
    { id: "svc_bridal", name: "Bridal Styling", category: "Occasion", price: 4500, duration: 150, repeatDays: 45, active: true },
    { id: "svc_cleanup", name: "Skin Cleanup", category: "Skin", price: 1200, duration: 45, repeatDays: 30, active: true }
  ],
  addons: [
    { id: "addon_beard", name: "Beard Trim", price: 99, duration: 12, serviceIds: ["svc_haircut", "svc_kids", "svc_wash"], staffIds: [], active: true },
    { id: "addon_massage", name: "Head Massage", price: 199, duration: 18, serviceIds: ["svc_haircut", "svc_combo", "svc_facial", "svc_wash", "svc_cleanup"], staffIds: [], active: true }
  ],
  staff: [
    { id: "st_adeel", name: "Adeel Khan", role: "Senior Barber", skills: ["Hair", "Beard"], hours: "10:00 - 20:00", active: true },
    { id: "st_hamza", name: "Hamza Iqbal", role: "Barber", skills: ["Hair", "Beard", "Wellness"], hours: "12:00 - 22:00", active: true },
    { id: "st_sana", name: "Sana Ahmed", role: "Stylist", skills: ["Colour", "Skin", "Occasion"], hours: "11:00 - 19:00", active: true }
  ],
  haircutStyles: [],
  customers: [], bookings: [], visits: [], walletTransactions: [], reminders: [], deposits: [], withdrawals: [], referrals: [], currentCustomerId: null, audit: [],
  paymentMethods: [
    { provider: "Easypaisa", displayName: "Easypaisa", accountTitle: "", accountNumber: "", qrCode: "", instructions: "Send the exact amount, then submit the reference ID. Owner verifies it manually.", enabled: true, mode: "MANUAL", sortOrder: 1 },
    { provider: "JazzCash", displayName: "JazzCash", accountTitle: "", accountNumber: "", qrCode: "", instructions: "Use the salon account and keep your transaction reference.", enabled: true, mode: "MANUAL", sortOrder: 2 },
    { provider: "NayaPay", displayName: "NayaPay", accountTitle: "", accountNumber: "", qrCode: "", instructions: "Manual verification is required before wallet credit is released.", enabled: true, mode: "MANUAL", sortOrder: 3 },
    { provider: "SadaPay", displayName: "SadaPay", accountTitle: "", accountNumber: "", qrCode: "", instructions: "Never share your password. Submit only the payment reference.", enabled: true, mode: "MANUAL", sortOrder: 4 }
  ]
});

const seedState = () => {
  const base = baseState();
  const qaSeed = typeof window !== "undefined" ? window.AYAN_QA_SEED : null;
  return typeof qaSeed === "function" ? qaSeed(base, { baseToday: iso(today), addDays, now: () => new Date().toISOString() }) : base;
};

function releaseState() {
  const seeded = baseState();
  // A fresh install must never display a salon name, address, phone or payment
  // account title that the owner did not type himself. Everything stays blank
  // until the owner saves his own business profile in Settings.
  return {
    ...seeded,
    schemaVersion: APP_SCHEMA_VERSION,
    releaseMode: true,
    salon: { ...seeded.salon, name: "", tagline: "", address: "", phone: "" },
    paymentMethods: (seeded.paymentMethods || []).map((method) => ({
      ...method, accountTitle: "", accountNumber: "",
      instructions: "Ask the salon owner for the correct receiving account and reference before sending money."
    })),
    customers: [],
    bookings: [],
    visits: [],
    walletTransactions: [],
    reminders: [],
    deposits: [],
    withdrawals: [],
    referrals: [],
    currentCustomerId: null,
    audit: []
  };
}

let state = loadState();
let ownerTapCount = 0;
let ownerTapWindowStartedAt = 0;
let ownerFailedAttempts = 0;
let ownerLockoutUntil = 0;
let ownerLockoutTimer = null;
let ownerSessionStartedAt = 0;
let ownerSessionTimer = null;
let ui = { role: "customer", ownerAuthenticated: false, customerScreen: "home", ownerScreen: "dashboard", modal: START_LOOKUP ? { type: "lookup", phone: "", searched: false, stage: apiModeEnabled() ? "phone" : undefined } : null, loading: true, search: "", reportTab: "overview", apiError: "", apiBusy: false, phoneIconDataUrl: "" };

function apiModeEnabled() {
  return typeof window !== "undefined" && typeof window.AyanApi?.enabled === "function" && window.AyanApi.enabled();
}

function apiSalonId() {
  const configured = typeof window !== "undefined" ? window.AyanApi?.salonId : "";
  return String(configured || state?.salon?.id || "").trim();
}

function apiErrorText(error, fallback = "The online salon service is unavailable.") {
  if (!error) return fallback;
  const message = String(error.message || "").trim();
  if (error.status === 401) return "Your session has expired. Sign in again with your mobile number and password.";
  if (error.status === 429) return "Too many attempts. Please wait a little and try again.";
  return message || fallback;
}

function apiSessionMetadata() {
  return apiModeEnabled() ? (window.AyanApi?.metadata?.() || null) : null;
}

function apiSessionRole() {
  return String(apiSessionMetadata()?.role || "").toUpperCase();
}

function apiOwnerSessionActive() {
  if (!apiModeEnabled()) return false;
  return !!window.AyanApi?.token?.() && apiSessionRole() === "OWNER";
}

function apiCustomerSessionActive() {
  return apiModeEnabled() && !!window.AyanApi?.token?.() && apiSessionRole() === "CUSTOMER";
}

function apiSessionExpired(error) {
  if (!apiModeEnabled() || error?.status !== 401 || !window.AyanApi?.token?.()) return false;
  window.AyanApi?.clearSession?.();
  clearRemoteCustomerState();
  ui.role = "customer";
  ui.ownerAuthenticated = false;
  ui.customerScreen = "home";
  return true;
}

function applyRemoteSettings(settings) {
  if (!settings || typeof settings !== "object") return;
  if (Number.isFinite(Number(settings.depositBonusMinor))) state.settings.walletBonus = minorToPkr(settings.depositBonusMinor);
  if (Number.isFinite(Number(settings.haircutReminderDays))) state.settings.reminderDefaultDays = Number(settings.haircutReminderDays);
  if (Number.isFinite(Number(settings.referralReferrerMinor))) state.settings.referralReferrerReward = minorToPkr(settings.referralReferrerMinor);
  if (Number.isFinite(Number(settings.referralNewCustomerMinor))) state.settings.referralNewCustomerDiscount = minorToPkr(settings.referralNewCustomerMinor);
  if (Number.isFinite(Number(settings.minimumWithdrawalMinor))) state.settings.withdrawalMinimum = minorToPkr(settings.minimumWithdrawalMinor);
  if (Number.isFinite(Number(settings.maximumDailyWithdrawalMinor))) state.settings.withdrawalDailyLimit = minorToPkr(settings.maximumDailyWithdrawalMinor);
  if (typeof settings.consumePromoFirst === "boolean") state.settings.consumePromoFirst = settings.consumePromoFirst;
  if (settings.primaryColor) state.salon.themeColor = safeHexColor(settings.primaryColor, state.salon.themeColor);
  if (Object.prototype.hasOwnProperty.call(settings, "logoUri") || Object.prototype.hasOwnProperty.call(settings, "logoUrl")) {
    state.salon.logoDataUrl = remoteImageSource(settings.logoUri || settings.logoUrl || "");
  }
}

function applyRemotePaymentMethods(methods, includeInactive = false) {
  if (!Array.isArray(methods)) return;
  state.paymentMethods = methods.map((method, index) => ({
    provider: String(method?.provider || `Provider${index + 1}`),
    displayName: String(method?.displayName || method?.provider || "Payment provider"),
    accountTitle: String(method?.accountTitle || "Salon account"),
    accountNumber: String(method?.accountReference || method?.accountNumber || ""),
    // The API deliberately omits secret account tokens. Keep the UI honest and
    // show only instructions supplied by the owner.
    qrCode: "",
    instructions: String(method?.instructions || "Follow the salon's payment instructions and keep your reference ID."),
    enabled: method?.enabled !== false,
    mode: String(method?.mode || "MANUAL"),
    sortOrder: Number(method?.sortOrder || index + 1)
  })).filter((method) => includeInactive || method.enabled);
}

function applyRemoteStaff(members) {
  if (!Array.isArray(members)) return;
  state.staff = members.map((member) => ({
    id: String(member?.id || ""), name: String(member?.name || "Salon team"),
    role: String(member?.role || "Staff"), phone: String(member?.phone || ""),
    skills: [], hours: "", active: member?.active !== false
  })).filter((member) => member.id);
}

function minorToPkr(value) {
  const amount = Number(value);
  return Number.isFinite(amount) ? Math.max(0, Math.round(amount / 100)) : 0;
}

function instantDate(value) {
  if (!value) return null;
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? null : parsed.toISOString().slice(0, 10);
}

function instantTime(value) {
  if (!value) return "";
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return "";
  return parsed.toLocaleTimeString("en-PK", { hour: "2-digit", minute: "2-digit", hour12: false });
}

function safeImageSource(value) {
  const local = safeImageDataUrl(value);
  if (local) return local;
  const raw = String(value || "").trim();
  if (!raw || typeof URL === "undefined") return "";
  try {
    const parsed = new URL(raw, typeof window !== "undefined" ? window.location.href : undefined);
    const localHost = ["localhost", "127.0.0.1", "10.0.2.2"].includes(parsed.hostname.toLowerCase());
    if (parsed.protocol === "https:" || (parsed.protocol === "http:" && localHost)) {
      parsed.username = ""; parsed.password = "";
      return parsed.href;
    }
  } catch (_) { /* invalid or unsafe image URI */ }
  return "";
}

function remoteStatusLabel(value) {
  const map = { PENDING: "Pending", CONFIRMED: "Confirmed", COMPLETED: "Completed", CANCELLED: "Cancelled", NO_SHOW: "No-show" };
  return map[String(value || "").toUpperCase()] || String(value || "Pending");
}

function remotePaymentLabel(value) {
  const map = { WALLET: "Salon credit", CASH: "Cash", MANUAL_PROVIDER: "Provider" };
  return map[String(value || "").toUpperCase()] || String(value || "Pay at salon");
}

function remoteTransactionType(value) {
  const map = {
    DEPOSIT_CASH: "Paid credit", DEPOSIT_BONUS: "Bonus credit", WITHDRAWAL_RESERVE: "Withdrawal reserved",
    WITHDRAWAL_RELEASE: "Withdrawal reversal", WITHDRAWAL_COMPLETE: "Withdrawal", SERVICE_PAYMENT: "Service payment",
    REFERRAL_BONUS: "Referral bonus", REFUND: "Refund", ADMIN_ADJUSTMENT: "Manual adjustment", REVERSAL: "Reversal"
  };
  return map[String(value || "").toUpperCase()] || String(value || "Wallet entry");
}

function applyRemoteCatalog(catalog) {
  if (!catalog || typeof catalog !== "object") throw new Error("The salon catalogue response was invalid.");
  const branding = catalog.branding || {};
  const services = Array.isArray(catalog.services) ? catalog.services : [];
  const serviceIds = services.map((item) => String(item.id));
  state.salon = {
    ...state.salon,
    id: apiSalonId() || state.salon.id,
    name: String(branding.name || state.salon.name),
    address: String(branding.address || state.salon.address),
    phone: String(branding.phone || state.salon.phone),
    themeColor: safeHexColor(branding.primaryColor, state.salon.themeColor),
    logoDataUrl: remoteImageSource(branding.logoUri || branding.logoUrl || "")
  };
  state.services = services.map((item) => ({
    id: String(item.id), name: String(item.name || "Service"), category: String(item.category || "General"),
    price: minorToPkr(item.priceMinor), duration: Math.max(1, Number(item.durationMinutes || 0)),
    repeatDays: Number(state.settings.reminderDefaultDays || 25), active: item.active !== false
  }));
  state.addons = (Array.isArray(catalog.addOns) ? catalog.addOns : []).map((item) => ({
    id: String(item.id), name: String(item.name || "Add-on"), price: minorToPkr(item.priceMinor),
    duration: Math.max(1, Number(item.durationMinutes || 0)),
    serviceIds: item.serviceId ? [String(item.serviceId)] : serviceIds.slice(), staffIds: [], active: item.active !== false
  }));
  state.haircutStyles = (Array.isArray(catalog.haircutStyles) ? catalog.haircutStyles : []).map((item) => ({
    id: String(item.id), name: String(item.name || "Haircut style"),
    photoDataUrl: remoteImageSource(item.photoUri || item.photoUrl || ""), price: minorToPkr(item.priceMinor),
    description: String(item.description || ""), serviceId: item.serviceId ? String(item.serviceId) : null, active: item.active !== false,
    displayOrder: Number(item.displayOrder || 0)
  }));
  // Public catalog responses are authoritative. Never retain stale local money
  // or customer records when an online workspace is selected.
  state.customers = [];
  state.bookings = [];
  state.visits = [];
  state.walletTransactions = [];
  state.reminders = [];
  state.deposits = [];
  state.withdrawals = [];
  state.referrals = [];
  state.currentCustomerId = null;
}

function mapRemoteTransaction(item) {
  const cash = minorToPkr(item?.cashAmountMinor);
  const bonus = minorToPkr(item?.promoAmountMinor);
  const direction = String(item?.direction || "CREDIT").toUpperCase();
  const signed = ["DEBIT", "RESERVE"].includes(direction) ? -1 : 1;
  return {
    id: String(item?.id || item?.referenceId || uid("wt")), customerId: String(item?.customerId || ""),
    type: remoteTransactionType(item?.type), paidCredit: signed * cash, bonusCredit: signed * bonus,
    debit: signed < 0 ? cash + bonus : 0, amount: signed * (cash + bonus), reason: String(item?.reason || ""),
    status: String(item?.status || "Credited"), createdAt: item?.occurredAt || new Date().toISOString(),
    reference: String(item?.referenceId || "")
  };
}

function mapRemoteBooking(item) {
  const startsAt = item?.startsAt || new Date().toISOString();
  return {
    id: String(item?.id || uid("bk")), customerId: String(item?.customerId || ""), serviceId: String(item?.serviceId || ""),
    staffId: item?.staffId ? String(item.staffId) : null, date: instantDate(startsAt) || iso(today), time: instantTime(startsAt),
    duration: Math.max(1, Math.round((new Date(item?.endsAt || startsAt) - new Date(startsAt)) / 60000)),
    total: minorToPkr(item?.totalMinor), addonIds: [], addonSnapshots: [], addonRevenue: minorToPkr(item?.addOnMinor),
    paymentMethod: remotePaymentLabel(item?.paymentMethod), paymentStatus: item?.status === "COMPLETED" ? "Paid" : "Pending",
    status: remoteStatusLabel(item?.status), source: "Online", createdAt: startsAt
  };
}

function mapRemoteVisit(item) {
  const completedAt = item?.completedAt || new Date().toISOString();
  return {
    id: String(item?.id || uid("visit")), bookingId: item?.bookingId ? String(item.bookingId) : null,
    customerId: String(item?.customerId || ""), serviceId: String(item?.serviceId || ""),
    staffId: item?.staffId ? String(item.staffId) : null,
    date: instantDate(completedAt) || iso(today), completedAt,
    amount: minorToPkr(item?.totalMinor), nextDueDate: instantDate(item?.nextDueAt),
    addonIds: [], addonSnapshots: [], addonRevenue: 0,
    paymentStatus: "Paid"
  };
}

function mapRemoteDeposit(item) {
  const status = String(item?.status || "PENDING").toUpperCase();
  return {
    id: String(item?.id || uid("dep")), customerId: String(item?.customerId || ""),
    amount: minorToPkr(item?.amountMinor), provider: String(item?.providerCode || item?.provider || "Provider"),
    reference: String(item?.providerReference || ""), proof: String(item?.proofUri || ""),
    bonusAmount: minorToPkr(item?.bonusSnapshotMinor), bonusAmountApplied: minorToPkr(item?.bonusGrantedMinor),
    firstDepositBonus: Number(item?.bonusGrantedMinor || 0) > 0,
    status: status === "APPROVED" ? "Approved" : status === "REJECTED" ? "Rejected" : "Pending verification",
    submittedAt: item?.createdAt || new Date().toISOString(), reviewedAt: item?.reviewedAt || null,
    reason: String(item?.reviewReason || "")
  };
}

function mapRemoteWithdrawal(item) {
  const status = String(item?.status || "PENDING").toUpperCase();
  const provider = String(item?.providerCode || item?.provider || "Provider");
  return {
    id: String(item?.id || uid("wd")), customerId: String(item?.customerId || ""),
    amount: minorToPkr(item?.amountMinor), provider,
    destination: item?.destinationLast4 ? `**** ${String(item.destinationLast4).slice(-4)}` : "Mobile hidden",
    destinationLast4: String(item?.destinationLast4 || ""),
    status: status === "APPROVED" ? "Approved" : status === "COMPLETED" ? "Completed" : status === "REJECTED" ? "Rejected" : "Pending",
    requestedAt: item?.createdAt || new Date().toISOString(), updatedAt: item?.reviewedAt || item?.createdAt || new Date().toISOString(),
    reason: String(item?.reviewReason || ""), reservedAmount: ["PENDING", "APPROVED"].includes(status) ? minorToPkr(item?.amountMinor) : 0
  };
}

function mapRemoteReferral(item) {
  const status = String(item?.status || "REGISTERED").toUpperCase();
  const labels = { REGISTERED: "Registered", PHONE_VERIFIED: "Pending visit", QUALIFIED: "Pending visit", REWARD_PENDING: "Pending visit", REWARD_GRANTED: "Rewarded", REJECTED: "Rejected" };
  return {
    id: String(item?.id || uid("ref")), referrerId: String(item?.referrerCustomerId || ""),
    referredCustomerId: String(item?.referredCustomerId || ""), code: String(item?.code || ""),
    status: labels[status] || status, referrerReward: minorToPkr(item?.referrerRewardMinor),
    newCustomerDiscount: minorToPkr(item?.newCustomerDiscountMinor),
    firstEligibleBookingId: item?.qualifyingBookingId ? String(item.qualifyingBookingId) : null,
    rewardedAt: item?.releasedAt || null, createdAt: item?.createdAt || new Date().toISOString()
  };
}

function applyRemoteOwnerOverview(overview) {
  if (!overview || typeof overview !== "object") throw new Error("The owner overview response was invalid.");
  const wallets = Array.isArray(overview.wallets) ? overview.wallets : [];
  const walletByCustomer = new Map(wallets.map((wallet) => [String(wallet?.customerId || ""), wallet]));
  state.customers = (Array.isArray(overview.customers) ? overview.customers : []).map((item) => {
    const wallet = walletByCustomer.get(String(item?.id || ""));
    return {
      id: String(item?.id || ""), name: String(item?.name || "Customer"), phone: String(item?.phone || ""),
      consent: item?.marketingConsent === true, phoneVerified: item?.phoneVerified === true,
      createdAt: instantDate(item?.createdAt) || iso(today), lastVisit: null,
      paidCredit: minorToPkr(wallet?.cashAvailableMinor), bonusCredit: minorToPkr(wallet?.promoAvailableMinor),
      reservedCash: minorToPkr(wallet?.cashReservedMinor), firstDepositBonusClaimed: wallet?.firstDepositBonusClaimed === true,
      darkMode: false, remote: true
    };
  }).filter((item) => item.id);
  state.currentCustomerId = null;
  state.bookings = (Array.isArray(overview.bookings) ? overview.bookings : []).map(mapRemoteBooking);
  state.visits = (Array.isArray(overview.visits) ? overview.visits : []).map(mapRemoteVisit);
  state.visits.forEach((visit) => {
    const customer = state.customers.find((item) => item.id === visit.customerId);
    if (customer && (!customer.lastVisit || visit.date > customer.lastVisit)) customer.lastVisit = visit.date;
  });
  state.walletTransactions = (Array.isArray(overview.walletTransactions) ? overview.walletTransactions : []).map(mapRemoteTransaction);
  state.reminders = (Array.isArray(overview.reminders) ? overview.reminders : []).map((item) => ({
    id: String(item?.id || uid("rem")), customerId: String(item?.customerId || ""), serviceId: String(item?.serviceId || ""),
    scheduledDate: instantDate(item?.dueAt) || iso(today), status: String(item?.status || "SCHEDULED").replace(/_/g, " "),
    optOut: item?.optedOut === true, bookingId: null
  }));
  state.deposits = (Array.isArray(overview.deposits) ? overview.deposits : []).map(mapRemoteDeposit);
  state.withdrawals = (Array.isArray(overview.withdrawals) ? overview.withdrawals : []).map(mapRemoteWithdrawal);
  state.referrals = (Array.isArray(overview.referrals) ? overview.referrals : []).map(mapRemoteReferral);
  return overview.summary || null;
}

function clearRemoteCustomerState() {
  state.customers = [];
  state.bookings = [];
  state.visits = [];
  state.walletTransactions = [];
  state.reminders = [];
  state.currentCustomerId = null;
}

async function hydrateRemoteCustomer() {
  if (!apiModeEnabled() || !window.AyanApi.token()) return false;
  const [profile, wallet, transactions, bookings, reminders, methods, members] = await Promise.all([
    window.AyanApi.me(apiSalonId()), window.AyanApi.wallet(apiSalonId()),
    window.AyanApi.walletTransactions(apiSalonId()), window.AyanApi.bookings(apiSalonId()),
    window.AyanApi.reminders(apiSalonId()), window.AyanApi.paymentMethods(apiSalonId()),
    window.AyanApi.staff(apiSalonId())
  ]);
  applyRemotePaymentMethods(methods, false);
  applyRemoteStaff(members);
  const customerId = String(profile?.id || window.AyanApi.metadata()?.actorId || "");
  if (!customerId) throw new Error("The customer profile response was invalid.");
  const customerRecord = {
    id: customerId, name: String(profile?.name || "Customer"), phone: String(profile?.phone || ""),
    consent: profile?.marketingConsent === true, phoneVerified: profile?.phoneVerified === true,
    lastVisit: instantDate(profile?.visits?.[0]?.completedAt), paidCredit: minorToPkr(wallet?.cashAvailableMinor),
    bonusCredit: minorToPkr(wallet?.promoAvailableMinor), firstDepositBonusClaimed: wallet?.firstDepositBonusClaimed === true,
    darkMode: customerDarkMode(state.customers.find((item) => item.id === customerId)), remote: true
  };
  state.customers = [customerRecord];
  state.currentCustomerId = customerId;
  state.visits = (Array.isArray(profile?.visits) ? profile.visits : []).map((item) => ({
    id: String(item?.id || uid("visit")), customerId, serviceId: String(item?.serviceId || ""),
    date: instantDate(item?.completedAt) || iso(today), completedAt: item?.completedAt || new Date().toISOString(),
    amount: minorToPkr(item?.totalMinor), nextDueDate: instantDate(item?.nextDueAt), addonIds: [], addonSnapshots: [], addonRevenue: 0
  }));
  state.walletTransactions = (Array.isArray(transactions) ? transactions : []).map(mapRemoteTransaction);
  state.bookings = (Array.isArray(bookings) ? bookings : []).map(mapRemoteBooking);
  state.reminders = (Array.isArray(reminders) ? reminders : []).map((item) => ({
    id: String(item?.id || uid("rem")), customerId, serviceId: String(item?.serviceId || ""),
    scheduledDate: instantDate(item?.dueAt) || iso(today), status: String(item?.status || "SCHEDULED").replace(/_/g, " "),
    optOut: item?.optedOut === true, bookingId: null
  }));
  return true;
}

async function hydrateRemoteOwner() {
  if (!apiModeEnabled() || !window.AyanApi.token() || !apiOwnerSessionActive()) return false;
  // Owner sessions use the owner overview for operational and financial data;
  // customer-only endpoints are intentionally never called with an owner token.
  const [settings, services, members, addOns, styles, methods, overview] = await Promise.all([
    window.AyanApi.settings(apiSalonId()), window.AyanApi.services(apiSalonId(), true),
    window.AyanApi.staff(apiSalonId(), true), window.AyanApi.addOns(null, apiSalonId(), true),
    window.AyanApi.haircutStyles(apiSalonId(), true), window.AyanApi.paymentMethods(apiSalonId(), true),
    window.AyanApi.ownerOverview(apiSalonId())
  ]);
  applyRemoteSettings(settings);
  applyRemotePaymentMethods(methods, true);
  applyRemoteStaff(members);
  if (Array.isArray(services)) {
    state.services = services.map((item) => ({
      id: String(item?.id || ""), name: String(item?.name || "Service"),
      category: String(item?.category || "General"), price: minorToPkr(item?.priceMinor),
      duration: Math.max(1, Number(item?.durationMinutes || 0)),
      repeatDays: Number(state.settings.reminderDefaultDays || 25), active: item?.active !== false
    })).filter((item) => item.id);
  }
  if (Array.isArray(addOns)) {
    state.addons = addOns.map((item) => ({
      id: String(item?.id || ""), name: String(item?.name || "Add-on"),
      price: minorToPkr(item?.priceMinor), duration: Math.max(1, Number(item?.durationMinutes || 0)),
      serviceIds: item?.serviceId ? [String(item.serviceId)] : state.services.map((s) => s.id),
      staffIds: [], active: item?.active !== false, description: String(item?.description || "")
    })).filter((item) => item.id);
  }
  if (Array.isArray(styles)) {
    state.haircutStyles = styles.map((item) => ({
      id: String(item?.id || ""), name: String(item?.name || "Haircut style"),
      photoDataUrl: remoteImageSource(item?.photoUri || item?.photoUrl || ""),
      price: minorToPkr(item?.priceMinor), description: String(item?.description || ""),
      serviceId: item?.serviceId ? String(item.serviceId) : null,
      active: item?.active !== false, displayOrder: Number(item?.displayOrder || 0)
    })).filter((item) => item.id);
  }
  applyRemoteOwnerOverview(overview);
  return true;
}

async function bootstrapApi() {
  if (!apiModeEnabled()) {
    ui.loading = false;
    render();
    return;
  }
  ui.apiBusy = true;
  ui.apiError = "";
  try {
    // Once the injected API configuration becomes available, discard any
    // offline release residue before the first network response is rendered.
    state = releaseState();
    const catalog = await window.AyanApi.publicCatalog(apiSalonId());
    applyRemoteCatalog(catalog);
    if (window.AyanApi.token()) {
      try {
        if (apiSessionRole() === "CUSTOMER") {
          ui.role = "customer";
          await hydrateRemoteCustomer();
        } else if (apiOwnerSessionActive()) {
          ui.role = "owner";
          ui.ownerAuthenticated = true;
          await hydrateRemoteOwner();
        } else {
          window.AyanApi.clearSession();
        }
      }
      catch (error) {
        if (error?.status === 401) {
          window.AyanApi.clearSession();
          clearRemoteCustomerState();
          ui.role = "customer";
          ui.ownerAuthenticated = false;
        }
        else throw error;
      }
    }
  } catch (error) {
    ui.apiError = apiErrorText(error, "Could not load the salon catalogue.");
  } finally {
    ui.apiBusy = false;
    ui.loading = false;
    render();
  }
}

function ownerSessionActive() {
  if (apiModeEnabled()) {
    const active = apiOwnerSessionActive();
    if (!active) {
      ui.ownerAuthenticated = false;
      if (ui.role === "owner") ui.role = "customer";
    }
    return active && ui.ownerAuthenticated === true;
  }
  if (ui.ownerAuthenticated !== true || ui.role !== "owner") return false;
  if (ownerSessionStartedAt && Date.now() - ownerSessionStartedAt > OWNER_SESSION_MAX_MS) {
    ui.ownerAuthenticated = false;
    ui.role = "customer";
    ui.ownerScreen = "dashboard";
    state.currentCustomerId = null;
    saveState();
    if (ownerSessionTimer) { clearTimeout(ownerSessionTimer); ownerSessionTimer = null; }
    return false;
  }
  return true;
}

function ownerCodeDigest() {
  return state.settings?.ownerAccessCodeHash || hashOwnerAccessCode(DEFAULT_OWNER_ACCESS_CODE);
}

function inferFirstDepositBonusClaimed(customerRecord, deposits, transactions) {
  if (customerRecord?.firstDepositBonusClaimed === true) return true;
  const customerId = customerRecord?.id;
  const receivedLedgerBonus = (transactions || []).some((transaction) => transaction.customerId === customerId
    && transaction.type === "Bonus credit"
    && Number(transaction.bonusCredit || 0) > 0
    && /deposit/i.test(String(transaction.reason || "")));
  const approvedDepositBonus = (deposits || []).some((deposit) => deposit.customerId === customerId
    && deposit.status === "Approved"
    && Number(deposit.bonusAmount || 0) > 0);
  return receivedLedgerBonus || approvedDepositBonus;
}

function customerDarkMode(customerRecord = currentCustomer()) {
  if (customerRecord && Object.prototype.hasOwnProperty.call(customerRecord, "darkMode")) return customerRecord.darkMode === true;
  try { return localStorage.getItem(DARK_MODE_STORAGE_KEY) === "dark"; } catch { return false; }
}

function applyVisualPreferences() {
  if (typeof document === "undefined") return;
  const root = document.documentElement;
  const themeColor = safeHexColor(state?.salon?.themeColor, DEFAULT_THEME_COLOR);
  root.style.setProperty("--accent", themeColor);
  root.style.setProperty("--accent-soft", rgbaColor(themeColor, 0.15));
  root.style.setProperty("--accent-ink", mixHexColor(themeColor, "#111111", 0.48));
  root.style.setProperty("--accent-hover", mixHexColor(themeColor, "#000000", 0.12));
  root.dataset.theme = customerDarkMode() ? "dark" : "light";
  const themeMeta = document.querySelector('meta[name="theme-color"]');
  if (themeMeta) themeMeta.setAttribute("content", root.dataset.theme === "dark" ? "#121314" : themeColor);
}

function setCustomerDarkMode(enabled) {
  const c = currentCustomer();
  if (c) c.darkMode = !!enabled;
  try { localStorage.setItem(DARK_MODE_STORAGE_KEY, enabled ? "dark" : "light"); } catch { /* storage may be unavailable in private mode */ }
  saveState();
  applyVisualPreferences();
}

function loadState() {
  try {
    // An online workspace starts from an empty in-memory shell. Catalog, profile,
    // wallet and ledger data are fetched after authentication; localStorage must
    // never become the authority for server-owned money.
    if (apiModeEnabled()) return releaseState();
    const saved = JSON.parse(localStorage.getItem(ACTIVE_STORAGE_KEY));
    if (!saved) return RELEASE_MODE ? releaseState() : seedState();
    if (RELEASE_MODE && saved.releaseMode !== true) return releaseState();
    const previousSchemaVersion = Number(saved.schemaVersion || 1);
    const legacy = saved.salon?.id === "salon_corner_chair" || saved.salon?.name === "The Corner Chair";
    const defaults = RELEASE_MODE ? releaseState() : seedState();
    saved.salon = { ...defaults.salon, ...(saved.salon || {}) };
    saved.salon.themeColor = safeHexColor(saved.salon.themeColor, DEFAULT_THEME_COLOR);
    saved.salon.logoDataUrl = safeImageSource(saved.salon.logoDataUrl);
    if (legacy) saved.salon = { ...defaults.salon };
    saved.settings = { ...defaults.settings, ...(saved.settings || {}) };
    if (!validOwnerAccessCode(saved.settings.ownerAccessCode)) {
      saved.settings.ownerAccessCodeHash = validOwnerAccessCodeHash(saved.settings.ownerAccessCodeHash) ? String(saved.settings.ownerAccessCodeHash).toLowerCase() : hashOwnerAccessCode(DEFAULT_OWNER_ACCESS_CODE);
    } else {
      // Drop any legacy plain-text value after migrating it to the local digest.
      saved.settings.ownerAccessCodeHash = hashOwnerAccessCode(saved.settings.ownerAccessCode);
      delete saved.settings.ownerAccessCode;
    }
    if (legacy) saved.settings = { ...defaults.settings, ...(saved.settings || {}), referralReferrerReward: 100, referralNewCustomerDiscount: 100 };
    if (previousSchemaVersion < 3 && Number(saved.settings.referralReferrerReward) === 50 && Number(saved.settings.referralNewCustomerDiscount) === 100) {
      const demoReferral = (Array.isArray(saved.referrals) ? saved.referrals : []).find((r) => r.id === "ref_001" && normalizeReferralCode(r.code) === "AYAN50" && Number(r.referrerReward) === 50);
      if (demoReferral) {
        saved.settings.referralReferrerReward = 100;
        demoReferral.code = "AYAN100";
        demoReferral.referrerReward = 100;
      }
    }
    ["paymentMethods", "deposits", "withdrawals", "audit", "walletTransactions", "referrals", "haircutStyles"].forEach((key) => {
      if (!Array.isArray(saved[key])) saved[key] = defaults[key] || [];
    });
    if (!Array.isArray(saved.addons)) saved.addons = defaults.addons;
    if (!saved.paymentMethods.length) saved.paymentMethods = defaults.paymentMethods;
    saved.paymentMethods = saved.paymentMethods.map((method, index) => ({
      ...(defaults.paymentMethods[index] || {}), ...method,
      displayName: method.displayName || method.provider,
      qrCode: method.qrCode || "",
      sortOrder: Number(method.sortOrder || index + 1)
    }));
    saved.haircutStyles = (saved.haircutStyles || []).map((style) => ({
      ...style,
      id: style.id || uid("style"),
      name: String(style.name || "").trim(),
      photoDataUrl: safeImageSource(style.photoDataUrl || style.photo || ""),
      price: Number.isSafeInteger(Number(style.price)) ? Number(style.price) : 0,
      description: String(style.description || ""),
      serviceId: style.serviceId || null,
      active: style.active !== false
    }));
    saved.customers = (saved.customers || []).map((c) => ({
      ...c,
      phone: c.phone || "",
      paidCredit: Number(c.paidCredit || 0),
      bonusCredit: Number(c.bonusCredit || 0),
      firstDepositBonusClaimed: inferFirstDepositBonusClaimed(c, saved.deposits, saved.walletTransactions),
      darkMode: c.darkMode === true
    }));
    saved.addons = saved.addons.map((a) => ({ ...a, serviceIds: Array.isArray(a.serviceIds) ? a.serviceIds : [], staffIds: Array.isArray(a.staffIds) ? a.staffIds : [] }));
    saved.bookings = (saved.bookings || defaults.bookings || []).map((b) => ({ ...b, addonIds: Array.isArray(b.addonIds) ? b.addonIds : [], addonSnapshots: Array.isArray(b.addonSnapshots) ? b.addonSnapshots : null }));
    saved.visits = (saved.visits || defaults.visits || []).map((v) => ({ ...v, addonIds: Array.isArray(v.addonIds) ? v.addonIds : [], addonSnapshots: Array.isArray(v.addonSnapshots) ? v.addonSnapshots : null, addonRevenue: Number.isFinite(Number(v.addonRevenue)) ? Number(v.addonRevenue) : null }));
    saved.referrals = (saved.referrals || []).map((r) => ({ ...r, referrerReward: Number(r.referrerReward ?? saved.settings.referralReferrerReward), newCustomerDiscount: Number(r.newCustomerDiscount ?? saved.settings.referralNewCustomerDiscount), rewardedAt: r.rewardedAt || null }));
    saved.withdrawals = (saved.withdrawals || []).map((w) => ({ ...w, destination: maskPayoutDestination(w.destination || w.destinationLast4 || ""), destinationLast4: String(w.destinationLast4 || String(w.destination || "").replace(/\D/g, "").slice(-4)) }));
    saved.schemaVersion = RELEASE_MODE ? APP_SCHEMA_VERSION : APP_SCHEMA_VERSION;
    if (RELEASE_MODE) {
      // A release/PWA install must never resurrect customer, booking or
      // financial fixtures left by an earlier QA session on this device.
      saved.releaseMode = true;
      saved.customers = [];
      saved.bookings = [];
      saved.visits = [];
      saved.walletTransactions = [];
      saved.reminders = [];
      saved.deposits = [];
      saved.withdrawals = [];
      saved.referrals = [];
      saved.currentCustomerId = null;
      saved.audit = [];
    }
    return saved;
  } catch { return RELEASE_MODE ? releaseState() : seedState(); }
}
function saveState() {
  if (apiModeEnabled()) return;
  localStorage.setItem(ACTIVE_STORAGE_KEY, JSON.stringify(state));
}
function resetData() {
  if (apiModeEnabled()) clearRemoteCustomerState();
  else state = RELEASE_MODE ? releaseState() : seedState();
  saveState(); toast(RELEASE_MODE ? "Salon workspace reset" : "Test data reset", "success"); render();
}
function currentCustomer() { return state.customers.find(c => c.id === state.currentCustomerId) || null; }
function customer() { return currentCustomer() || { id: null, name: "New customer", phone: "", consent: false, lastVisit: null, paidCredit: 0, bonusCredit: 0, firstDepositBonusClaimed: false, darkMode: false }; }
function service(id) { return state.services.find(s => s.id === id); }
function staff(id) { return state.staff.find(s => s.id === id); }
function addon(id) { return (state.addons || []).find(a => a.id === id); }
function addonSnapshot(ids) {
  return (ids || []).map((id) => {
    const a = addon(id);
    return a ? { id: a.id, name: a.name, price: Number(a.price || 0), duration: Number(a.duration || 0) } : null;
  }).filter(Boolean);
}
function addonItemsForRecord(record) {
  if (Array.isArray(record?.addonSnapshots)) return record.addonSnapshots;
  return addonSnapshot(record?.addonIds || []);
}
function addonRevenueForRecord(record) {
  if (Number.isFinite(Number(record?.addonRevenue))) return Number(record.addonRevenue);
  return addonItemsForRecord(record).reduce((sum, item) => sum + Number(item.price || 0), 0);
}
function customerName(id) { return state.customers.find(c => c.id === id)?.name || "Unknown customer"; }
function totalWallet(c) { return Number(c.paidCredit || 0) + Number(c.bonusCredit || 0); }
function reservedWithdrawalCash(c) { return state.withdrawals.filter(w => w.customerId === c.id && ["Pending", "Approved"].includes(w.status)).reduce((sum, w) => sum + Number(w.reservedAmount || w.amount || 0), 0); }
// A wallet booking is not debited until the visit is completed, but its amount must
// still be reserved so two confirmed bookings cannot spend the same credit.
function reservedWalletBookings(c, excludeBookingId = null) {
  return state.bookings
    .filter((b) => b.customerId === c?.id && b.id !== excludeBookingId && b.paymentMethod === "Salon credit" && ["Pending", "Confirmed"].includes(b.status))
    .reduce((sum, b) => sum + Math.max(0, Number(b.total || 0)), 0);
}
function reservedCash(c) { return reservedWithdrawalCash(c); }
function withdrawableCash(c) { return Math.max(0, Number(c?.paidCredit || 0) - reservedWithdrawalCash(c) - reservedWalletBookings(c)); }
function availableWallet(c, excludeBookingId = null) { return Math.max(0, effectiveWallet(c) - reservedWithdrawalCash(c) - reservedWalletBookings(c, excludeBookingId)); }
function walletExpired(c) { return !!(c?.creditExpiresAt && c.creditExpiresAt < iso(today)); }
function effectiveWallet(c) { return walletExpired(c) ? 0 : totalWallet(c); }
function normalizePhone(value) { return String(value || "").replace(/\D/g, ""); }
// Keep lookup identity matching strict and country-aware. The display value may use spaces,
// +92, or 0092, but a customer lookup must resolve to one complete Pakistani mobile number.
function canonicalPakistaniPhone(value) {
  let digits = normalizePhone(value);
  if (/^3\d{9}$/.test(digits)) digits = `0${digits}`;
  else if (digits.startsWith("0092")) digits = `0${digits.slice(4)}`;
  else if (digits.startsWith("92")) digits = `0${digits.slice(2)}`;
  else if (digits.length === 10 && digits.startsWith("3")) digits = `0${digits}`;
  return digits;
}
function isValidPakistaniMobile(value) { return /^03\d{9}$/.test(canonicalPakistaniPhone(value)); }
function samePakistaniMobile(left, right) {
  const a = canonicalPakistaniPhone(left); const b = canonicalPakistaniPhone(right);
  return isValidPakistaniMobile(a) && isValidPakistaniMobile(b) && a === b;
}
function formatPakistaniMobile(value) {
  const normalized = canonicalPakistaniPhone(value);
  return isValidPakistaniMobile(normalized) ? `${normalized.slice(0, 4)} ${normalized.slice(4, 7)} ${normalized.slice(7)}` : String(value || "").trim();
}
function maskPakistaniMobile(value) {
  const normalized = canonicalPakistaniPhone(value);
  return isValidPakistaniMobile(normalized) ? `${normalized.slice(0, 4)} **** ${normalized.slice(-2)}` : "Mobile hidden";
}
function normalizeProviderReference(value) { return String(value || "").trim().replace(/\s+/g, " ").toUpperCase(); }
function normalizeReferralCode(value) { return String(value || "").trim().replace(/[\s-]+/g, "").toUpperCase(); }
function referralCodeTaken(code, customerId) {
  const normalized = normalizeReferralCode(code); if (!normalized) return false;
  return (state.customers || []).some((c) => c.id !== customerId && normalizeReferralCode(c.referralCode) === normalized)
    || (state.referrals || []).some((r) => r.referrerId !== customerId && normalizeReferralCode(r.code) === normalized);
}
function referrerCodeFor(c) {
  if (!c) return "";
  const linked = (state.referrals || []).find((r) => r.referrerId === c.id && r.code);
  let code = normalizeReferralCode(c.referralCode || linked?.code);
  if (!code || referralCodeTaken(code, c.id)) {
    const suffix = normalizePhone(c.phone).slice(-4) || String(c.id || "").replace(/\D/g, "").slice(-4) || "50";
    const base = `AYAN${suffix}`; code = base; let attempt = 2;
    while (referralCodeTaken(code, c.id)) code = `${base}${attempt++}`;
  }
  if (c.referralCode !== code) { c.referralCode = code; saveState(); }
  return code;
}
function referralTemplateForCode(rawCode) {
  const code = normalizeReferralCode(rawCode); if (!code) return null;
  const template = (state.referrals || []).find((r) => normalizeReferralCode(r.code) === code);
  const referrerId = template?.referrerId;
  const referrer = (state.customers || []).find((c) => c.id === referrerId || normalizeReferralCode(c.referralCode) === code);
  return referrer ? { code, referrer, template } : null;
}
function claimReferralForCustomer(c, rawCode) {
  const code = normalizeReferralCode(rawCode);
  if (!c || !code) return { ok: false, message: "Enter a referral code first." };
  if (state.settings.referralEnabled === false) return { ok: false, message: "Referral claims are currently paused by the salon." };
  if (customerVisitCount(c.id) > 0) return { ok: false, message: "Referral codes are only available before the first visit." };
  if ((state.referrals || []).some((r) => r.referredCustomerId === c.id)) return { ok: false, message: "This customer already has a referral history." };
  const match = referralTemplateForCode(code);
  if (!match) return { ok: false, message: "That referral code was not found for this salon." };
  if (match.referrer.id === c.id || samePakistaniMobile(match.referrer.phone, c.phone)) {
    return { ok: false, message: "You cannot use your own referral code." };
  }
  const referral = {
    id: uid("ref"), referrerId: match.referrer.id, referredCustomerId: c.id, code,
    status: "Registered",
    referrerReward: Number(match.template?.referrerReward ?? state.settings.referralReferrerReward ?? 0),
    newCustomerDiscount: Number(match.template?.newCustomerDiscount ?? state.settings.referralNewCustomerDiscount ?? 0),
    firstEligibleBookingId: null, completedVisitId: null, rewardedAt: null, createdAt: new Date().toISOString()
  };
  state.referrals.push(referral);
  state.audit.push({ id: uid("audit"), action: "Referral claimed", actor: c.id, reason: `${code} from ${match.referrer.id}`, createdAt: new Date().toISOString() });
  saveState();
  return { ok: true, referral };
}
function setCustomerConsent(c, consent, at = new Date().toISOString()) {
  if (!c) return;
  c.consent = !!consent;
  if (c.consent) return;
  state.reminders.filter((r) => r.customerId === c.id && !r.bookingId && ["Scheduled", "Sent"].includes(r.status)).forEach((r) => {
    r.status = "Opted out";
    r.optOut = true;
    r.optedOutAt = at;
  });
}
function withdrawalActionLabel(status) { return status === "Pending" ? "Approve payout" : "Mark paid"; }
function parseClock(value) {
  const match = String(value || "").trim().match(/^(\d{1,2}):(\d{2})$/);
  if (!match) return null;
  const hour = Number(match[1]); const minute = Number(match[2]);
  return hour >= 0 && hour < 24 && minute >= 0 && minute < 60 ? hour * 60 + minute : null;
}
function parseStaffHours(value) {
  const match = String(value || "").trim().match(/^(\d{1,2}:\d{2})\s*-\s*(\d{1,2}:\d{2})$/);
  const start = parseClock(match?.[1]); const end = parseClock(match?.[2]);
  return start !== null && end !== null && end > start ? { start, end } : null;
}
function staffWindow(st) {
  return parseStaffHours(st?.hours) || { start: 0, end: 0 };
}
function staffCanPerform(st, s) {
  if (!st || !st.active || !s) return false;
  const skills = Array.isArray(st.skills) ? st.skills : [];
  return !skills.length || skills.includes("All") || skills.includes(s.category);
}
function activeServices() { return (state.services || []).filter((s) => s.active); }
function activeStaff() { return (state.staff || []).filter((s) => s.active); }
function addonEligibleForStaff(a, st) {
  if (!a || !st || !st.active) return false;
  const staffIds = Array.isArray(a.staffIds) ? a.staffIds : [];
  return !staffIds.length || staffIds.includes(st.id);
}
function bookingStartMinutes(b) { return parseClock(b?.time) ?? 0; }
function bookingInterval(b) {
  const start = bookingStartMinutes(b);
  const duration = Math.max(1, Number(b?.duration || 0));
  return { start, end: Math.min(24 * 60, start + duration) };
}
function slotBusy(date, staffId, time, duration) {
  const start = parseClock(time); if (start === null) return true;
  const end = start + Number(duration || 0); const st = staff(staffId); const window = staffWindow(st);
  // The authenticated API catalog may not expose staff working-hour details to
  // customers. In that case the server remains the authority for availability;
  // the client only prevents overlaps from already hydrated bookings.
  if (!(apiModeEnabled() && !String(st?.hours || "").trim()) && (start < window.start || end > window.end)) return true;
  return activeBookingsFor(date, staffId).some((b) => {
    const current = bookingInterval(b);
    return start < current.end && current.start < end;
  });
}
function openReferralFor(c, bookingId = null) {
  if (!c || state.settings.referralEnabled === false || state.visits.some((v) => v.customerId === c.id)) return null;
  const ref = state.referrals.find((r) => r.referredCustomerId === c.id && ["Registered", "Pending visit"].includes(r.status)) || null;
  if (!ref || !ref.firstEligibleBookingId || ref.firstEligibleBookingId === bookingId) return ref;
  const assigned = state.bookings.find((b) => b.id === ref.firstEligibleBookingId);
  return !assigned || ["Cancelled", "No-show"].includes(assigned.status) ? ref : null;
}
function bookingPaymentConfirmed(b) {
  if (!b) return false;
  if (b.paymentMethod === "Salon credit" || b.paymentMethod === "Cash") return true;
  return ["Paid", "Confirmed"].includes(String(b.paymentStatus || ""));
}
function eligibleReferralDiscount(c) {
  const ref = openReferralFor(c);
  return ref ? Number(ref.newCustomerDiscount ?? state.settings.referralNewCustomerDiscount ?? 0) : 0;
}
function monthKey(value) { return String(value || "").slice(0, 7); }
function maskPayoutDestination(value) {
  const raw = String(value || "").trim(); const digits = raw.replace(/\D/g, "");
  if (digits.length <= 4) return "****";
  return `${"*".repeat(Math.max(4, digits.length - 4))}${digits.slice(-4)}`;
}
function customerVisitCount(customerId) { return state.visits.filter((visit) => visit.customerId === customerId).length; }
function applyWalletExpiry() {
  let changed = false;
  state.customers.forEach((c) => {
    if (!walletExpired(c) || c.walletExpiryRecorded || totalWallet(c) <= 0) return;
    const paid = Number(c.paidCredit || 0); const bonus = Number(c.bonusCredit || 0); c.paidCredit = 0; c.bonusCredit = 0; c.walletExpiryRecorded = true;
    const now = new Date().toISOString();
    if (paid > 0) state.walletTransactions.push({ id: uid("wt"), customerId: c.id, type: "Expired paid credit", paidCredit: -paid, bonusCredit: 0, debit: paid, amount: -paid, reason: `Paid credit expired on ${c.creditExpiresAt}`, status: "Reversed", createdAt: now, reference: `expiry:${c.id}:paid` });
    if (bonus > 0) state.walletTransactions.push({ id: uid("wt"), customerId: c.id, type: "Expired bonus credit", paidCredit: 0, bonusCredit: -bonus, debit: bonus, amount: -bonus, reason: `Bonus credit expired on ${c.creditExpiresAt}`, status: "Reversed", createdAt: now, reference: `expiry:${c.id}:bonus` });
    changed = true;
  });
  if (changed) saveState();
}
function activeBookingsFor(date, staffId) { return state.bookings.filter(b => b.date === date && b.staffId === staffId && ["Pending", "Confirmed"].includes(b.status)); }
// A fresh install must never show a salon name, address or payment account that
// the owner did not type himself, so every customer-facing label falls back to a
// neutral placeholder until the owner saves his own business profile.
function salonDisplayName() {
  const name = String((typeof state !== "undefined" && state?.salon?.name) || "").trim();
  return name || "Your salon";
}

// No brand letters are baked into the app. Until the owner uploads a logo we
// show the first letter of the salon name the owner typed, or a neutral
// scissors mark when the name is still empty.
function salonMarkGlyph() {
  const name = String((typeof state !== "undefined" && state?.salon?.name) || "").trim();
  const letter = name.replace(/[^A-Za-z0-9]/g, "").charAt(0);
  return letter ? letter.toUpperCase() : "\u2702";
}

function statusTag(status) {
  const map = { Completed: "success", Approved: "success", Confirmed: "success", Credited: "success", Rewarded: "success", Sent: "success", Registered: "pink", Published: "success", Hidden: "neutral", Pending: "warning", "Pending verification": "warning", "Pending visit": "warning", Scheduled: "warning", Invited: "neutral", Cancelled: "danger", "No-show": "danger", Rejected: "danger", Failed: "danger", "Opted out": "neutral", Debited: "neutral", Refunded: "danger", Reversed: "danger", Withdrawal: "neutral" };
  return `<span class="tag tag-${map[status] || "neutral"}">${escapeHtml(status)}</span>`;
}
function escapeHtml(value) { return String(value ?? "").replace(/[&<>'"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" }[c])); }
function toast(message, kind = "success") {
  const node = document.createElement("div"); node.className = `toast ${kind === "error" ? "error" : ""}`; node.textContent = message; document.getElementById("toast-region").appendChild(node); setTimeout(() => node.remove(), 3600);
}
function htmlesc(value) { return escapeHtml(value); }
function brandMarkMarkup(className = "brand-mark") {
  const logo = safeImageSource(state?.salon?.logoDataUrl);
  return logo
    ? `<img class="${className} brand-image" src="${logo}" alt="${htmlesc(salonDisplayName())} logo">`
    : `<div class="${className}" aria-hidden="true">${htmlesc(salonMarkGlyph())}</div>`;
}

function render() {
  const app = document.getElementById("app");
  // The browser tab, the installed web app and the phone task switcher show the
  // owner's own salon name as soon as it is known - never a hard-coded name.
  const brandName = String(state?.salon?.name || "").trim();
  const wantedTitle = brandName ? `${brandName} | Salon` : "Salon";
  if (document.title !== wantedTitle) document.title = wantedTitle;
  applyWalletExpiry();
  applyVisualPreferences();
  if (ui.role === "owner" && !ownerSessionActive()) {
    ui.role = "customer";
    ui.ownerScreen = "dashboard";
    ui.modal = null;
  }
  if (ui.loading) {
    app.innerHTML = `<div class="layout"><div class="loading-state" aria-label="Loading"></div>${apiModeEnabled() ? `<div class="notice notice-info">Connecting to the salon service...</div>` : ""}</div>`;
    return;
  }
  const apiNotice = apiModeEnabled() && ui.apiError
    ? `<div class="layout" style="padding-top:12px"><div class="notice notice-warning" role="alert">${htmlesc(ui.apiError)} <button class="btn btn-soft btn-small" data-action="retry-api">Retry</button></div></div>`
    : "";
  app.innerHTML = `<div class="app-shell">${header()}${apiNotice}<main class="layout">${ui.role === "customer" ? customerApp() : ownerApp()}</main>${ui.role === "customer" ? customerNav() : ""}${ui.modal ? modal() : ""}</div>`;
  refreshWithdrawalLabels();
  bindEvents();
  applyStyleCardMotion();
}

/**
 * Haircut photos animate into view as the customer scrolls. Only transforms and
 * opacity are animated, and the reveal is skipped entirely when the device asks
 * for reduced motion.
 */
function applyStyleCardMotion() {
  const cards = Array.from(document.querySelectorAll(".style-card"));
  if (!cards.length) return;
  const reduced = typeof window.matchMedia === "function"
    && window.matchMedia("(prefers-reduced-motion: reduce)").matches === true;
  if (reduced || typeof IntersectionObserver !== "function") {
    cards.forEach((card) => card.classList.add("is-visible"));
    return;
  }
  const observer = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (!entry.isIntersecting) return;
      entry.target.classList.add("is-visible");
      observer.unobserve(entry.target);
    });
  }, { threshold: 0.18, rootMargin: "0px 0px -6% 0px" });
  cards.forEach((card) => observer.observe(card));
}

function refreshWithdrawalLabels() {
  document.querySelectorAll('[data-action="complete-withdrawal"][data-withdrawal-id]').forEach((button) => {
    const withdrawal = state.withdrawals.find((item) => item.id === button.dataset.withdrawalId);
    if (withdrawal) button.textContent = withdrawalActionLabel(withdrawal.status);
  });
}

function header() {
  const signedInCustomer = ui.role === "customer" ? currentCustomer() : null;
  const customerControls = signedInCustomer ? `<button class="btn btn-secondary btn-small" data-action="customer-logout" aria-label="Switch customer">Switch</button>` : "";
  const appearanceControl = signedInCustomer ? `<button class="btn btn-secondary btn-small" data-action="toggle-dark-mode" aria-label="Toggle dark mode">${customerDarkMode() ? "Light" : "Dark"}</button>` : "";
  const ownerControls = ownerSessionActive() ? `${apiModeEnabled() ? "" : `<button class="btn btn-secondary btn-small" data-action="change-owner-code">Change code</button>`}<button class="btn btn-secondary btn-small" data-action="owner-logout" aria-label="Lock owner workspace">Lock</button>` : "";
  const lookupLabel = apiModeEnabled() ? (signedInCustomer ? "Switch" : "Sign in") : "Lookup";
  const lookupButton = ui.role === "owner" && apiModeEnabled() ? "" : `<button class="btn btn-secondary btn-small" data-action="customer-lookup" aria-label="${lookupLabel}">${lookupLabel}</button>`;
  // The owner entry point is deliberately discreet.  Server authorization is
  // still mandatory; the gesture only opens the sign-in sheet and never grants
  // owner access by itself.
  const brandGesture = `<div data-owner-gesture role="button" tabindex="0" aria-label="Salon home">${brandMarkMarkup()}</div>`;
  return `<header class="app-header"><div class="header-inner"><div class="brand-lockup">${brandGesture}<div><div class="brand-name">${htmlesc(salonDisplayName())}</div><div class="brand-sub">Salon OS</div></div></div><div class="header-actions">${lookupButton}${appearanceControl}${customerControls}${ownerControls}</div></div></header>`;
}
function customerNav() {
  const items = [["home", "Home"], ["book", "Book"], ["wallet", "Wallet"], ["more", "More"]];
  return `<nav class="bottom-nav">${items.map(([id, label]) => `<button data-customer-screen="${id}" class="${ui.customerScreen === id ? "active" : ""}"><span class="nav-mark">${id === "home" ? "⌂" : id === "book" ? "+" : id === "wallet" ? "▣" : "•••"}</span>${label}</button>`).join("")}</nav>`;
}

function customerOnboarding() {
  return `<div class="page-head"><div class="page-title"><div class="eyebrow">Welcome</div><h1>Your salon account starts here.</h1><p>Find your profile with your Pakistani mobile number, or create a new profile in a few steps.</p></div></div><div class="surface panel onboarding-panel"><div class="onboarding-mark">${brandMarkMarkup("onboarding-logo")}</div><h2>Welcome${state.salon.name ? " to " + htmlesc(state.salon.name) : ""}</h2><p class="list-meta">Your profile keeps bookings, visit history, salon credit and reminders together for this salon.</p><div class="onboarding-actions"><button class="btn btn-primary" data-action="customer-lookup">Find my profile</button><button class="btn btn-secondary" data-action="start-customer-signup">Create new profile</button></div><div class="notice notice-info">We use your mobile number only to find the right salon profile. Promotional messages are sent only when you consent.</div></div>`;
}

function customerApp() {
  const c = currentCustomer();
  if (!c) return customerOnboarding();
  if (ui.customerScreen === "book") return customerBookingScreen();
  if (ui.customerScreen === "wallet") return customerWalletScreen();
  if (ui.customerScreen === "more") return customerMoreScreen();
  const upcoming = state.bookings.filter(b => b.customerId === c.id && ["Pending", "Confirmed"].includes(b.status)).sort((a,b) => `${a.date}${a.time}`.localeCompare(`${b.date}${b.time}`))[0];
  const lastVisit = state.visits.filter(v => v.customerId === c.id).sort((a,b) => b.date.localeCompare(a.date))[0];
  const reminder = state.reminders.find(r => r.customerId === c.id && ["Scheduled", "Sent"].includes(r.status) && !r.bookingId && !r.optOut);
  return `<div class="page-head"><div class="page-title"><div class="eyebrow">Good morning</div><h1>${htmlesc(c.name.split(" ")[0])}, welcome back.</h1><p>Your next good hair day is easier to plan when your salon keeps the details for you.</p></div><div class="head-actions"><button class="btn btn-primary" data-action="open-booking">Book an appointment</button></div></div>
    <div class="customer-hero"><div class="hero-row"><div class="hero-copy"><div class="hero-kicker">${htmlesc(state.salon.tagline)}</div><h2>Look sharp. Stay connected.</h2><p>Book with your preferred barber, use your salon credit, and get a reminder when your next service is due.</p></div><div class="hero-stat"><span>Salon credit</span><strong>${money(effectiveWallet(c))}</strong></div></div></div>
    ${haircutStylesSection()}
    <div class="customer-grid"><div><section class="section"><div class="surface wallet-card">${walletCard(c)}</div></section><section class="section">${reminder ? reminderCard(reminder) : emptyReminder()}</section><section class="section"><div class="section-head"><h2>Recent visits</h2><span class="hint">Your history, at a glance</span></div>${recentVisits(c)}</section></div><aside><section class="section"><div class="section-head"><h2>Quick actions</h2></div><div class="action-grid"><button class="action-tile" data-action="open-booking"><span class="action-icon">+</span><span class="action-label">Book a visit</span><span class="action-hint">Choose service & slot</span></button><button class="action-tile" data-customer-screen="wallet"><span class="action-icon">▣</span><span class="action-label">My wallet</span><span class="action-hint">${money(effectiveWallet(c))} available</span></button><button class="action-tile" data-customer-screen="more" data-more-tab="referral"><span class="action-icon">↗</span><span class="action-label">Refer & earn</span><span class="action-hint">Share your code</span></button><button class="action-tile" data-customer-screen="more" data-more-tab="profile"><span class="action-icon">○</span><span class="action-label">My profile</span><span class="action-hint">Consent & details</span></button></div></section><section class="section">${upcoming ? upcomingCard(upcoming) : emptyUpcoming()}</section></aside></div>`;
}

function walletCard(c) {
  const expired = walletExpired(c); const paid = expired ? 0 : Number(c.paidCredit || 0); const bonus = expired ? 0 : Number(c.bonusCredit || 0); const reserved = expired ? 0 : reservedCash(c); const withdrawable = Math.max(0, paid - reserved);
  return `<div class="wallet-top"><div><div class="eyebrow">My salon wallet</div><div class="wallet-balance">${money(paid + bonus)}</div></div>${statusTag(expired ? "Reversed" : "Credited")}</div><div class="wallet-detail-grid"><div class="wallet-detail"><span>Cash balance</span><strong>${money(paid)}</strong><small>Withdrawable: ${money(withdrawable)}</small></div><div class="wallet-detail"><span>Referral / bonus</span><strong>${money(bonus)}</strong><small>Promotional · non-withdrawable</small></div></div>${reserved ? `<div class="notice notice-info" style="margin-bottom:12px">${money(reserved)} is reserved for a pending withdrawal.</div>` : ""}${expired ? `<div class="notice notice-warning" style="margin-bottom:12px">Your previous credit expired on ${dateLabel(c.creditExpiresAt)}. Add a new top-up to continue.</div>` : c.creditExpiresAt ? `<div class="list-meta" style="margin-bottom:12px">Current credit expires ${dateLabel(c.creditExpiresAt)}.</div>` : ""}<div class="wallet-actions"><button class="btn btn-primary btn-small" data-action="wallet-topup">Add money</button><button class="btn btn-secondary btn-small" data-action="withdraw-money" ${withdrawable < Number(state.settings.withdrawalMinimum || 100) ? "disabled" : ""}>Withdraw</button><button class="btn btn-secondary btn-small" data-customer-screen="wallet">View ledger</button></div>`;
}
function reminderCard(reminder) {
  const s = service(reminder.serviceId); const cycle = Number(s?.repeatDays || state.settings.reminderDefaultDays || 25);
  return `<div class="surface reminder-card"><div class="inline"><div class="reminder-mark">${cycle}</div><div class="reminder-copy"><h3>Your ${htmlesc(s?.name || "service")} is due</h3><p>A suitable slot is available around ${dateLabel(reminder.scheduledDate)}. One tap and it is sorted.</p></div></div><button class="btn btn-soft btn-small" data-action="open-booking" data-service-id="${reminder.serviceId}" data-source="Reminder">Book now</button></div>`;
}
function emptyReminder() { return `<div class="empty-state"><strong>Reminders are quiet</strong>Your next service-based reminder will appear here when it is due.</div>`; }
function upcomingCard(b) { const s = service(b.serviceId); const st = staff(b.staffId); return `<div class="surface panel"><div class="panel-head"><div><h3>Next appointment</h3><p>We have saved your spot.</p></div>${statusTag(b.status)}</div><div class="list-row" style="padding:0;border:0;background:transparent"><div class="list-row-main"><div class="list-title">${htmlesc(s?.name)}</div><div class="list-meta">${dateLabel(b.date, { weekday: "short", day: "numeric", month: "short" })} at ${timeLabel(b.time)} with ${htmlesc(st?.name || "Any barber")}</div></div><div class="list-amount">${money(b.total)}</div></div><div style="display:flex;gap:8px;margin-top:15px"><button class="btn btn-secondary btn-small" data-action="manage-booking" data-booking-id="${b.id}">Manage</button></div></div>`; }
function emptyUpcoming() { return `<div class="surface panel"><div class="empty-state"><strong>No upcoming booking</strong>Ready for a fresh look? Pick a service and a time that suits you.</div></div>`; }
function recentVisits(c) { const visits = state.visits.filter(v => v.customerId === c.id).sort((a,b) => b.date.localeCompare(a.date)).slice(0, 4); if (!visits.length) return `<div class="empty-state"><strong>Your first visit is waiting</strong>Once a service is completed, it will show here.</div>`; return `<div class="list">${visits.map(v => { const s = service(v.serviceId); const st = staff(v.staffId); return `<div class="list-row"><div class="list-row-main"><div class="list-title">${htmlesc(s?.name)}</div><div class="list-meta">${dateLabel(v.date)} with ${htmlesc(st?.name || "Salon team")} · ${v.paymentStatus}</div></div><div class="list-amount">${money(v.amount)}</div></div>`; }).join("")}</div>`; }

function haircutStylesSection() {
  const styles = (state.haircutStyles || []).filter((style) => style.active !== false && style.name).slice(0, 12);
  if (!styles.length) return `<section class="section style-showcase"><div class="section-head"><div><h2>Haircut styles</h2><span class="hint">Fresh looks from the salon</span></div></div><div class="empty-state style-empty"><strong>Styles are being curated</strong>The salon will add haircut photos and prices here soon.</div></section>`;
  const cards = styles.map((style, index) => {
    const photo = safeImageSource(style.photoDataUrl || style.photo);
    const linkedService = style.serviceId ? service(style.serviceId) : null;
    const price = Number.isSafeInteger(Number(style.price)) ? Number(style.price) : Number(linkedService?.price || 0);
    const media = photo
      ? `<img src="${photo}" alt="${htmlesc(style.name)} haircut style" loading="lazy">`
      : `<div class="style-card-placeholder" aria-hidden="true"><span>${htmlesc(salonMarkGlyph())}</span></div>`;
    const action = linkedService
      ? `<button class="btn btn-soft btn-small" data-action="book-haircut-style" data-style-id="${htmlesc(style.id)}">Book this look</button>`
      : `<span class="tag tag-neutral">Ask at salon</span>`;
    return `<article class="style-card" style="--style-index:${index}"><button type="button" class="style-card-media" data-action="open-style-photo" data-style-id="${htmlesc(style.id)}" aria-label="View the ${htmlesc(style.name)} haircut style"><span class="style-card-media-frame">${media}</span><span class="style-card-play" aria-hidden="true">View look</span></button><div class="style-card-body"><div class="style-card-top"><h3>${htmlesc(style.name)}</h3><strong>${money(price)}</strong></div>${style.description ? `<p>${htmlesc(style.description)}</p>` : ""}<div class="style-card-footer">${linkedService ? `<span>${linkedService.duration} mins</span>` : `<span>Custom look</span>`}${action}</div></div></article>`;
  }).join("");
  return `<section class="section style-showcase"><div class="section-head"><div><h2>Haircut styles</h2><span class="hint">Choose a look, then pick your slot</span></div><span class="tag tag-pink">New looks</span></div><div class="style-scroller">${cards}</div></section>`;
}

function customerBookingScreen() {
  return `<div class="page-head"><div class="page-title"><div class="eyebrow">Quick booking</div><h1>Pick your next visit.</h1><p>Choose a service, then make the slot yours. Add-ons stay optional and the total updates as you go.</p></div><div class="head-actions"><button class="btn btn-secondary" data-customer-screen="home">Back home</button></div></div><div class="surface panel"><div class="empty-state"><strong>Ready when you are</strong>Start with a service and we will show the right add-ons, barbers and available slots.<br><button class="btn btn-primary" style="margin-top:14px" data-action="open-booking">Choose a service</button></div></div>`;
}
function customerWalletScreen() {
  const c = customer(); const txs = state.walletTransactions.filter(t => t.customerId === c.id).sort((a,b) => b.createdAt.localeCompare(a.createdAt)); const requests = state.withdrawals.filter(w => w.customerId === c.id).sort((a,b) => b.requestedAt.localeCompare(a.requestedAt)); return `<div class="page-head"><div class="page-title"><div class="eyebrow">Store credit</div><h1>My salon wallet.</h1><p>Cash and promotional credit stay separate. Referral credit is usable only at ${htmlesc(salonDisplayName())} and cannot be withdrawn or transferred.</p></div><div class="head-actions"><button class="btn btn-primary" data-action="wallet-topup">Add money</button><button class="btn btn-secondary" data-action="withdraw-money">Withdraw cash</button></div></div><div class="customer-grid"><div><div class="surface wallet-card">${walletCard(c)}</div><div class="section surface panel"><div class="section-head"><h2>Wallet terms</h2><span class="tag tag-neutral">Owner configurable</span></div><div class="settings-line"><span>Cash balance</span><strong>${money(Math.max(0, Number(c.paidCredit || 0)))}</strong></div><div class="settings-line"><span>Referral / bonus balance</span><strong>${money(Math.max(0, Number(c.bonusCredit || 0)))}</strong></div><div class="settings-line"><span>Minimum withdrawal</span><strong>${money(state.settings.withdrawalMinimum || 100)}</strong></div><div class="settings-line"><span>Usage</span><strong>Salon services only for promotional credit</strong></div></div>${requests.length ? `<div class="section surface panel"><div class="section-head"><h2>Withdrawal requests</h2><span class="hint">Owner verification required</span></div><div class="list">${requests.map(w => `<div class="list-row"><div class="list-row-main"><div class="list-title">${money(w.amount)} via ${htmlesc(w.provider)}</div><div class="list-meta">${dateLabel(w.requestedAt.slice(0,10))} · ${htmlesc(w.destination || "")}</div></div>${statusTag(w.status)}</div>`).join("")}</div></div>` : ""}</div><div><div class="section-head"><h2>Transaction history</h2><span class="hint">Immutable ledger</span></div><div class="list">${txs.length ? txs.map(t => `<div class="list-row"><div class="list-row-main"><div class="list-title">${htmlesc(t.type)}</div><div class="list-meta">${dateLabel(t.createdAt.slice(0,10))} · ${htmlesc(t.reason)}</div></div><div class="list-amount" style="color:${t.amount < 0 ? "var(--danger)" : "var(--success)"}">${t.amount > 0 ? "+" : ""}${money(t.amount)}</div></div>`).join("") : `<div class="empty-state"><strong>No wallet activity yet</strong>Your confirmed top-ups and visit debits will appear here.</div>`}</div></div></div>`;
}
function ownerApp() {
  const screen = ui.ownerScreen; if (screen === "bookings") return ownerBookings(); if (screen === "customers") return ownerCustomers(); if (screen === "catalogue") return ownerCatalogueProduction(); if (screen === "reports") return ownerReports(); if (screen === "settings") return ownerSettingsProduction(); return ownerDashboard();
}
function ownerHeader(title, subtitle, action = "") { return `<div class="page-head"><div class="page-title"><div class="eyebrow">Owner workspace</div><h1>${title}</h1><p>${subtitle}</p></div><div class="head-actions">${action}</div></div>`; }
function ownerNav() { const items = [["dashboard", "Today"], ["bookings", "Bookings"], ["customers", "Customers"], ["catalogue", "Services & staff"], ["reports", "Reports"], ["settings", "Settings"]]; return `<nav class="owner-nav">${items.map(([id,label]) => `<button data-owner-screen="${id}" class="${ui.ownerScreen === id ? "active" : ""}">${label}</button>`).join("")}</nav>`; }
function ownerDashboard() {
  const completedToday = state.visits.filter((v) => v.date === iso(today));
  const completedRevenue = completedToday.reduce((sum, v) => sum + Number(v.amount || 0), 0);
  const todayBookings = state.bookings.filter((b) => b.date === iso(today));
  const completed = state.visits.length;
  const repeat = state.customers.filter((c) => state.visits.filter((v) => v.customerId === c.id).length > 1).length;
  const newCustomers = state.customers.filter((c) => c.createdAt >= addDays(iso(today), -30)).length;
  const walletCollected = state.walletTransactions.filter((t) => t.type === "Paid credit" && Number(t.amount || 0) > 0).reduce((sum, t) => sum + Number(t.amount || 0), 0);
  const outstanding = state.customers.reduce((sum, c) => sum + effectiveWallet(c), 0);
  const reminderBookings = state.bookings.filter((b) => b.source === "Reminder").length;
  const addonRevenue = state.visits.reduce((sum, v) => sum + addonRevenueForRecord(v), 0);
  const tomorrow = addDays(iso(today), 1);
  const tomorrowCount = state.bookings.filter((b) => b.date === tomorrow && ["Pending", "Confirmed"].includes(b.status)).length;
  const pendingDeposits = state.deposits.filter((d) => d.status === "Pending verification").length;
  const pendingWithdrawals = state.withdrawals.filter((w) => ["Pending", "Approved"].includes(w.status)).length;
  const cards = [
    ["Today's completed revenue", money(completedRevenue), `${completedToday.length} completed visit${completedToday.length === 1 ? "" : "s"}`],
    ["Today's bookings", todayBookings.length, `${tomorrowCount} tomorrow`],
    ["Average bill value", money(completed ? state.visits.reduce((sum, v) => sum + Number(v.amount || 0), 0) / completed : 0), `${money(addonRevenue)} add-ons`],
    ["Repeat customers", repeat, "2 or more completed visits"],
    ["New customers", newCustomers, "Created in the last 30 days"],
    ["Wallet credit collected", money(walletCollected), "Confirmed paid credit"],
    ["Outstanding salon credit", money(outstanding), `Across ${state.customers.length} customers`],
    ["Reminder-generated bookings", reminderBookings, "Trackable reminder conversions"],
    ["Pending deposits", pendingDeposits, "Manual verification required"],
    ["Pending withdrawals", pendingWithdrawals, "Cash reservations active"]
  ];
  return `${ownerNav()}${ownerHeader("Good morning, owner.", "The numbers that need a decision today, in one calm view.", `<button class="btn btn-primary" data-action="open-walkin">+ Walk-in booking</button>`)}<div class="metric-grid">${cards.map(([label, value, foot]) => `<div class="surface metric-card"><div class="metric-label">${label}</div><div class="metric-value">${value}</div><div class="metric-foot neutral">${foot}</div></div>`).join("")}</div><div class="section dashboard-grid"><div class="dashboard-main"><div class="surface panel wide">${ownerBookingsPanel()}</div><div class="surface panel">${barberPerformance()}</div><div class="surface panel">${popularServices()}</div></div><aside class="dashboard-main"><div class="surface panel">${ownerFinancePanel()}</div><div class="surface panel">${dueRemindersPanel()}</div><div class="surface panel">${referralsPanel()}</div><div class="surface panel">${dailySummaryPanel()}</div></aside></div>`;
}
function ownerFinancePanel() {
  const deposits = state.deposits.filter((d) => d.status === "Pending verification"); const withdrawals = state.withdrawals.filter((w) => ["Pending", "Approved"].includes(w.status));
  const withdrawalAction = (w) => withdrawalActionLabel(w.status);
  return `<div class="panel-head"><div><h2>Money needing review</h2><p>Manual provider checks before any financial state changes.</p></div><span class="tag tag-warning">${deposits.length + withdrawals.length} pending</span></div>${deposits.length ? `<div class="list">${deposits.slice(0,3).map(d => `<div class="list-row"><div class="list-row-main"><div class="list-title">Deposit · ${money(d.amount)}</div><div class="list-meta">${customerName(d.customerId)} · ${htmlesc(d.provider)} · ${htmlesc(d.reference || "No reference")}</div></div><div class="table-actions"><button class="btn btn-soft btn-small" data-action="approve-deposit" data-deposit-id="${d.id}">Approve</button><button class="btn btn-secondary btn-small" data-action="reject-deposit" data-deposit-id="${d.id}">Reject</button></div></div>`).join("")}</div>` : `<div class="empty-state"><strong>No deposits awaiting review</strong>Customer claims never credit automatically.</div>`}${withdrawals.length ? `<div class="list" style="margin-top:10px">${withdrawals.slice(0,3).map(w => `<div class="list-row"><div class="list-row-main"><div class="list-title">Withdrawal · ${money(w.amount)}</div><div class="list-meta">${customerName(w.customerId)} · ${htmlesc(w.provider)} · ${htmlesc(w.destination || "")}</div></div><div class="table-actions"><button class="btn btn-soft btn-small" data-action="complete-withdrawal" data-withdrawal-id="${w.id}">${withdrawalAction(w)}</button><button class="btn btn-secondary btn-small" data-action="reject-withdrawal" data-withdrawal-id="${w.id}">Reject</button></div></div>`).join("")}</div>` : ""}<button class="btn btn-secondary btn-small" style="margin-top:13px" data-owner-screen="settings">Payment methods & rules</button>`;
}
function ownerBookingsPanel() { const bs = state.bookings.filter(b => b.date === iso(today)).sort((a,b)=>a.time.localeCompare(b.time)); return `<div class="panel-head"><div><h2>Today's flow</h2><p>Confirm, complete or resolve every chair.</p></div><button class="btn btn-secondary btn-small" data-owner-screen="bookings">View all</button></div>${bs.length ? `<div class="data-table-wrap"><table class="data-table"><thead><tr><th>Time</th><th>Customer</th><th>Service</th><th>Staff</th><th>Status</th><th></th></tr></thead><tbody>${bs.map(b=>bookingRow(b)).join("")}</tbody></table></div>` : `<div class="empty-state"><strong>No bookings today</strong>Use walk-in booking when someone arrives.</div>`}`; }
function bookingRow(b) { const s=service(b.serviceId), st=staff(b.staffId); return `<tr><td>${timeLabel(b.time)}</td><td><div class="customer-cell"><strong>${htmlesc(customerName(b.customerId))}</strong><span>${htmlesc(state.customers.find(c=>c.id===b.customerId)?.phone||"")}</span></div></td><td>${htmlesc(s?.name||"")}<br><span class="list-meta">${money(b.total)}</span></td><td>${htmlesc(st?.name||"")}</td><td>${statusTag(b.status)}</td><td><div class="table-actions">${b.status === "Confirmed" || b.status === "Pending" ? `<button class="btn btn-soft btn-small" data-action="complete-booking" data-booking-id="${b.id}">Complete</button>` : ""}<button class="btn btn-secondary btn-small" data-action="manage-booking" data-booking-id="${b.id}">View</button></div></td></tr>`; }
function barberPerformance() { const rows = state.staff.map(st => { const vs=state.visits.filter(v=>v.staffId===st.id); return {st, count:vs.length, revenue:vs.reduce((s,v)=>s+v.amount,0)}; }).sort((a,b)=>b.revenue-a.revenue); const max=Math.max(...rows.map(r=>r.revenue),1); return `<div class="panel-head"><div><h2>Barber performance</h2><p>Completed revenue by team member.</p></div><span class="tag tag-neutral">This period</span></div><div class="bar-list">${rows.map(r=>`<div class="bar-item"><div class="bar-label">${htmlesc(r.st.name.split(" ")[0])}</div><div class="bar-track"><div class="bar-fill" style="width:${(r.revenue/max)*100}%"></div></div><div class="bar-value">${money(r.revenue)}</div></div>`).join("")}</div>`; }
function popularServices() { const rows=state.services.map(s=>({s,count:state.visits.filter(v=>v.serviceId===s.id).length})).sort((a,b)=>b.count-a.count).slice(0,5); return `<div class="panel-head"><div><h2>Popular services</h2><p>What customers are choosing most.</p></div><button class="btn btn-secondary btn-small" data-owner-screen="reports">Reports</button></div><div class="list">${rows.map(r=>`<div class="list-row" style="padding:11px 0;border:0;border-bottom:1px solid #ecebe7;border-radius:0"><div class="list-row-main"><div class="list-title">${htmlesc(r.s.name)}</div><div class="list-meta">${r.s.duration} mins · ${money(r.s.price)}</div></div><div class="list-amount">${r.count} visits</div></div>`).join("")}</div>`; }
function dueRemindersPanel() { const due=state.reminders.filter(r=>["Scheduled","Sent"].includes(r.status)&&!r.bookingId&&!r.optOut).slice(0,5); return `<div class="panel-head"><div><h2>Due reminders</h2><p>Service-based, permission-aware.</p></div><span class="tag tag-warning">${due.length} due</span></div>${due.length?`<div class="list">${due.map(r=>`<div class="list-row" style="padding:11px 0;border:0;border-bottom:1px solid #ecebe7;border-radius:0"><div class="list-row-main"><div class="list-title">${htmlesc(customerName(r.customerId))}</div><div class="list-meta">${htmlesc(service(r.serviceId)?.name||"")} · ${dateLabel(r.scheduledDate)}</div></div><button class="btn btn-soft btn-small" data-action="open-booking" data-service-id="${r.serviceId}" data-customer-id="${r.customerId}" data-source="Reminder">Send</button></div>`).join("")}</div>`:`<div class="empty-state"><strong>Queue is clear</strong>No approved reminders need attention.</div>`}`; }
function referralsPanel() { const pending=state.referrals.filter(r=>r.status!=="Rewarded").length, rewarded=state.referrals.filter(r=>r.status==="Rewarded").length; return `<div class="panel-head"><div><h2>Referrals</h2><p>Reward after paid first visit.</p></div><span class="tag tag-pink">${rewarded} successful</span></div><div class="metric-grid" style="grid-template-columns:repeat(2,minmax(0,1fr));"><div style="padding:0"><div class="metric-label">Pending validation</div><div class="metric-value" style="font-size:25px">${pending}</div></div><div style="padding:0"><div class="metric-label">Rewarded</div><div class="metric-value" style="font-size:25px">${rewarded}</div></div></div><button class="btn btn-secondary btn-small" style="margin-top:14px" data-owner-screen="reports">Open referral report</button>`; }
function dailySummaryPanel() {
  const todayKey = iso(today); const todayVisits = state.visits.filter((v) => v.date === todayKey); const missed = state.bookings.filter((b) => b.date === todayKey && ["Cancelled", "No-show"].includes(b.status));
  const newCompleted = todayVisits.filter((v) => state.visits.filter((item) => item.customerId === v.customerId && item.completedAt < v.completedAt).length === 0).length;
  const repeatCompleted = todayVisits.length - newCompleted;
  const addonRevenue = todayVisits.reduce((sum, v) => sum + addonRevenueForRecord(v), 0);
  const tomorrowBookings = state.bookings.filter((b) => b.date === addDays(todayKey, 1) && ["Pending", "Confirmed"].includes(b.status)).length;
  return `<div class="panel-head"><div><h2>Daily owner summary</h2><p>Ready to share at closing.</p></div><span class="tag tag-success">Live</span></div><div class="settings-line"><span>Revenue</span><strong>${money(todayVisits.reduce((sum, v) => sum + Number(v.amount || 0), 0))}</strong></div><div class="settings-line"><span>Completed / missed</span><strong>${todayVisits.length} / ${missed.length}</strong></div><div class="settings-line"><span>New / repeat customers</span><strong>${newCompleted} / ${repeatCompleted}</strong></div><div class="settings-line"><span>Add-on revenue</span><strong>${money(addonRevenue)}</strong></div><div class="settings-line"><span>Tomorrow's bookings</span><strong>${tomorrowBookings}</strong></div><button class="btn btn-primary btn-block" style="margin-top:14px" data-action="export-report">Export summary</button>`;
}

function ownerBookings() { return `${ownerNav()}${ownerHeader("Bookings", "Every slot, including walk-ins and missed appointments.", `<button class="btn btn-primary" data-action="open-walkin">+ Create booking</button>`)}<div class="surface panel"><div class="filters"><input data-filter-bookings placeholder="Search customer or phone" value="${htmlesc(ui.search)}"><select data-filter-status><option value="">All statuses</option>${["Pending","Confirmed","Completed","Cancelled","No-show"].map(s=>`<option>${s}</option>`).join("")}</select><input type="date" data-filter-date value="${iso(today)}"></div><div class="data-table-wrap"><table class="data-table"><thead><tr><th>Date / time</th><th>Customer</th><th>Service</th><th>Staff</th><th>Total</th><th>Status</th><th>Actions</th></tr></thead><tbody>${filteredBookings().length ? filteredBookings().map(b=>bookingRowFull(b)).join("") : `<tr><td colspan="7"><div class="empty-state"><strong>No bookings match</strong>Try another date or create a walk-in booking.</div></td></tr>`}</tbody></table></div></div>`; }
function filteredBookings() { const search=ui.search.toLowerCase(); const date=document.querySelector("[data-filter-date]")?.value || ""; const status=document.querySelector("[data-filter-status]")?.value || ""; return state.bookings.filter(b => (!date || b.date===date) && (!status || b.status===status) && (!search || customerName(b.customerId).toLowerCase().includes(search) || (state.customers.find(c=>c.id===b.customerId)?.phone||"").includes(search))).sort((a,b)=>`${a.date}${a.time}`.localeCompare(`${b.date}${b.time}`)); }
function bookingRowFull(b) { const s=service(b.serviceId), st=staff(b.staffId); return `<tr><td><strong>${dateLabel(b.date)}</strong><br><span class="list-meta">${timeLabel(b.time)}</span></td><td><div class="customer-cell"><strong>${htmlesc(customerName(b.customerId))}</strong><span>${htmlesc(state.customers.find(c=>c.id===b.customerId)?.phone||"")}</span></div></td><td>${htmlesc(s?.name||"")}<br><span class="list-meta">${b.duration} mins</span></td><td>${htmlesc(st?.name||"")}</td><td>${money(b.total)}</td><td>${statusTag(b.status)}</td><td><div class="table-actions">${["Confirmed","Pending"].includes(b.status)?`<button class="btn btn-soft btn-small" data-action="complete-booking" data-booking-id="${b.id}">Complete</button>`:""}<button class="btn btn-secondary btn-small" data-action="manage-booking" data-booking-id="${b.id}">View</button></div></td></tr>`; }
function ownerCustomers() { const search=ui.search.toLowerCase(); const rows=state.customers.filter(c=>!search||c.name.toLowerCase().includes(search)||c.phone.includes(search)); return `${ownerNav()}${ownerHeader("Customers", "Keep customer history useful, consent clear and adjustments auditable.", `<button class="btn btn-primary" data-action="add-customer">+ Add customer</button>`)}<div class="surface panel"><div class="filters"><input data-customer-search placeholder="Search name or mobile" value="${htmlesc(ui.search)}"><button class="btn btn-secondary btn-small" data-action="clear-search">Clear</button></div><div class="data-table-wrap"><table class="data-table"><thead><tr><th>Customer</th><th>Last visit</th><th>Wallet</th><th>Consent</th><th>Visits</th><th></th></tr></thead><tbody>${rows.length?rows.map(c=>`<tr><td><div class="customer-cell"><strong>${htmlesc(c.name)}</strong><span>${htmlesc(c.phone)}</span></div></td><td>${dateLabel(c.lastVisit)}</td><td>${money(effectiveWallet(c))}</td><td>${c.consent?statusTag("Credited"):statusTag("Opted out")}</td><td>${state.visits.filter(v=>v.customerId===c.id).length}</td><td><div class="table-actions"><button class="btn btn-secondary btn-small" data-action="edit-customer" data-customer-id="${c.id}">Edit</button><button class="btn btn-soft btn-small" data-action="adjust-wallet" data-customer-id="${c.id}">Wallet</button></div></td></tr>`).join(""):`<tr><td colspan="6"><div class="empty-state"><strong>No customers found</strong>Add the first profile or change the search.</div></td></tr>`}</tbody></table></div></div>`; }
function ownerCatalogue() { return `${ownerNav()}${ownerHeader("Services & staff", "Edit prices and durations whenever the salon changes its menu.", `<button class="btn btn-secondary" data-action="add-staff">+ Add staff</button><button class="btn btn-primary" data-action="add-service">+ Add service</button>`)}<div class="settings-grid"><div class="surface panel"><div class="panel-head"><div><h2>Service catalogue</h2><p>All values are PKR and owner editable.</p></div><span class="tag tag-success">${state.services.filter(s=>s.active).length} active</span></div><div class="list">${state.services.map(s=>`<div class="list-row"><div class="list-row-main"><div class="list-title">${htmlesc(s.name)}</div><div class="list-meta">${htmlesc(s.category)} · ${s.duration} mins · remind after ${s.repeatDays} days</div></div><div style="text-align:right"><div class="list-amount">${money(s.price)}</div><button class="btn btn-secondary btn-small" style="margin-top:6px" data-action="edit-service" data-service-id="${s.id}">Edit</button></div></div>`).join("")}</div></div><div class="surface panel"><div class="panel-head"><div><h2>Staff & hours</h2><p>Slot availability follows active team members.</p></div><span class="tag tag-neutral">${state.staff.filter(s=>s.active).length} active</span></div><div class="list">${state.staff.map(st=>`<div class="list-row"><div class="list-row-main"><div class="list-title">${htmlesc(st.name)}</div><div class="list-meta">${htmlesc(st.role)} · ${htmlesc(st.skills.join(", "))}</div></div><div style="text-align:right"><div class="list-amount">${htmlesc(st.hours)}</div><button class="btn btn-secondary btn-small" style="margin-top:6px" data-action="edit-staff" data-staff-id="${st.id}">Edit</button></div></div>`).join("")}</div></div></div>`; }
function ownerReports() { const tabs=[["overview","Overview"],["barbers","Barbers"],["customers","Top customers"],["services","Popular services"],["addons","Add-ons"],["referrals","Referrals"],["reminders","Due reminders"],["attendance","Cancellations & no-shows"]]; return `${ownerNav()}${ownerHeader("Reports", "A practical read on repeat visits, bigger bills and referrals.", `<button class="btn btn-secondary" data-action="export-report">Export CSV</button>`)}<div class="filters">${tabs.map(([id,label])=>`<button class="btn ${ui.reportTab===id?"btn-primary":"btn-secondary"} btn-small" data-report-tab="${id}">${label}</button>`).join("")}</div><div class="surface panel">${reportContent()}</div>`; }
function reportContent() {
  if (ui.reportTab === "barbers") return `<div class="panel-head"><div><h2>Barber performance</h2><p>Revenue, completed services and average bill.</p></div></div><div class="data-table-wrap"><table class="data-table"><thead><tr><th>Staff</th><th>Completed</th><th>Revenue</th><th>Avg bill</th></tr></thead><tbody>${state.staff.map((st) => { const visits = state.visits.filter((v) => v.staffId === st.id); const revenue = visits.reduce((sum, v) => sum + Number(v.amount || 0), 0); return `<tr><td><strong>${htmlesc(st.name)}</strong></td><td>${visits.length}</td><td>${money(revenue)}</td><td>${money(visits.length ? revenue / visits.length : 0)}</td></tr>`; }).join("")}</tbody></table></div>`;
  if (ui.reportTab === "customers") {
    const rows = state.customers.map((c) => { const visits = state.visits.filter((v) => v.customerId === c.id); return { c, visits, spend: visits.reduce((sum, v) => sum + Number(v.amount || 0), 0) }; }).sort((a, b) => b.spend - a.spend).slice(0, 10);
    return `<div class="panel-head"><div><h2>Top customers</h2><p>Based on completed visit spend.</p></div></div><div class="data-table-wrap"><table class="data-table"><thead><tr><th>Customer</th><th>Visits</th><th>Total spend</th><th>Wallet</th></tr></thead><tbody>${rows.map((r) => `<tr><td><strong>${htmlesc(r.c.name)}</strong></td><td>${r.visits.length}</td><td>${money(r.spend)}</td><td>${money(effectiveWallet(r.c))}</td></tr>`).join("")}</tbody></table></div>`;
  }
  if (ui.reportTab === "services") {
    const rows = state.services.map((s) => { const visits = state.visits.filter((v) => v.serviceId === s.id); return { s, count: visits.length, revenue: visits.reduce((sum, v) => sum + Number(v.amount || 0), 0) }; }).sort((a, b) => b.count - a.count || b.revenue - a.revenue);
    return `<div class="panel-head"><div><h2>Popular services</h2><p>Completed visits and revenue by service.</p></div></div><div class="data-table-wrap"><table class="data-table"><thead><tr><th>Service</th><th>Completed</th><th>Revenue</th><th>Current price</th></tr></thead><tbody>${rows.map((r) => `<tr><td><strong>${htmlesc(r.s.name)}</strong></td><td>${r.count}</td><td>${money(r.revenue)}</td><td>${money(r.s.price)}</td></tr>`).join("")}</tbody></table></div>`;
  }
  if (ui.reportTab === "addons") {
    const totals = new Map();
    state.visits.forEach((v) => addonItemsForRecord(v).forEach((item) => { const row = totals.get(item.id) || { id: item.id, name: item.name, count: 0, revenue: 0 }; row.count += 1; row.revenue += Number(item.price || 0); totals.set(item.id, row); }));
    state.addons.forEach((a) => { if (!totals.has(a.id)) totals.set(a.id, { id: a.id, name: a.name, count: 0, revenue: 0 }); });
    const rows = [...totals.values()].sort((a, b) => b.revenue - a.revenue || b.count - a.count);
    return `<div class="panel-head"><div><h2>Add-on revenue</h2><p>Historical totals use the price captured at booking time.</p></div></div><div class="data-table-wrap"><table class="data-table"><thead><tr><th>Add-on</th><th>Accepted</th><th>Revenue</th><th>Current price</th></tr></thead><tbody>${rows.map((r) => `<tr><td><strong>${htmlesc(r.name)}</strong></td><td>${r.count}</td><td>${money(r.revenue)}</td><td>${money(addon(r.id)?.price || 0)}</td></tr>`).join("")}</tbody></table></div>`;
  }
  if (ui.reportTab === "referrals") return `<div class="panel-head"><div><h2>Referral funnel</h2><p>Rewards release only after the paid first visit.</p></div></div><div class="metric-grid"><div class="metric-card" style="padding:0"><div class="metric-label">Sign-ups</div><div class="metric-value">${state.referrals.length}</div></div><div class="metric-card" style="padding:0"><div class="metric-label">Pending</div><div class="metric-value">${state.referrals.filter((r) => r.status !== "Rewarded").length}</div></div><div class="metric-card" style="padding:0"><div class="metric-label">Successful</div><div class="metric-value">${state.referrals.filter((r) => r.status === "Rewarded").length}</div></div><div class="metric-card" style="padding:0"><div class="metric-label">Reward exposure</div><div class="metric-value">${money(state.referrals.reduce((sum, r) => sum + Number(r.referrerReward || 0), 0))}</div></div></div><div class="list" style="margin-top:18px">${state.referrals.length ? state.referrals.map((r) => `<div class="list-row"><div class="list-row-main"><div class="list-title">${htmlesc(r.code)}</div><div class="list-meta">${customerName(r.referrerId)} referred ${r.referredCustomerId ? customerName(r.referredCustomerId) : "-"}</div></div>${statusTag(r.status)}</div>`).join("") : `<div class="empty-state"><strong>No referrals yet</strong>Claims and rewards will appear here.</div>`}</div>`;
  if (ui.reportTab === "reminders") {
    const rows = state.reminders.slice().sort((a, b) => String(a.scheduledDate).localeCompare(String(b.scheduledDate)));
    return `<div class="panel-head"><div><h2>Due reminders</h2><p>Consent-aware service cycles and booking conversions.</p></div></div><div class="data-table-wrap"><table class="data-table"><thead><tr><th>Customer</th><th>Service</th><th>Due date</th><th>Status</th><th>Booking</th></tr></thead><tbody>${rows.length ? rows.map((r) => `<tr><td><strong>${htmlesc(customerName(r.customerId))}</strong></td><td>${htmlesc(service(r.serviceId)?.name || "Unknown service")}</td><td>${dateLabel(r.scheduledDate)}</td><td>${statusTag(r.status)}</td><td>${r.bookingId ? htmlesc(r.bookingId) : "-"}</td></tr>`).join("") : `<tr><td colspan="5"><div class="empty-state"><strong>No reminders</strong>Completed services will create the next due cycle.</div></td></tr>`}</tbody></table></div>`;
  }
  if (ui.reportTab === "attendance") return `<div class="panel-head"><div><h2>Attendance health</h2><p>Resolve cancellations and no-shows without deleting history.</p></div></div><div class="metric-grid"><div class="metric-card" style="padding:0"><div class="metric-label">Cancelled</div><div class="metric-value">${state.bookings.filter((b) => b.status === "Cancelled").length}</div></div><div class="metric-card" style="padding:0"><div class="metric-label">No-show</div><div class="metric-value">${state.bookings.filter((b) => b.status === "No-show").length}</div></div><div class="metric-card" style="padding:0"><div class="metric-label">Confirmed</div><div class="metric-value">${state.bookings.filter((b) => b.status === "Confirmed").length}</div></div><div class="metric-card" style="padding:0"><div class="metric-label">Reminder conversion</div><div class="metric-value">${state.reminders.filter((r) => r.bookingId).length}/${state.reminders.length}</div></div></div>`;
  const completedRevenue = state.visits.reduce((sum, v) => sum + Number(v.amount || 0), 0); const addonRevenue = state.visits.reduce((sum, v) => sum + addonRevenueForRecord(v), 0);
  return `<div class="panel-head"><div><h2>Business loop</h2><p>Every number connects back to the customer journey.</p></div></div><div class="settings-line"><span>Completed revenue</span><strong>${money(completedRevenue)}</strong></div><div class="settings-line"><span>Average bill value</span><strong>${money(state.visits.length ? completedRevenue / state.visits.length : 0)}</strong></div><div class="settings-line"><span>Add-on revenue</span><strong>${money(addonRevenue)}</strong></div><div class="settings-line"><span>Add-on acceptance</span><strong>${state.visits.length ? Math.round(state.visits.filter((v) => addonItemsForRecord(v).length).length / state.visits.length * 100) : 0}%</strong></div><div class="settings-line"><span>Reminder bookings</span><strong>${state.bookings.filter((b) => b.source === "Reminder").length}</strong></div><div class="settings-line"><span>Outstanding credit</span><strong>${money(state.customers.reduce((sum, c) => sum + effectiveWallet(c), 0))}</strong></div>`;
}
function ownerSettings() { return `${ownerNav()}${ownerHeader("Settings", "Keep rules, consent and financial history visible to the people who run the salon.", `<button class="btn btn-primary" data-action="save-settings">Save settings</button>`)}<div class="settings-grid"><div class="surface panel"><div class="panel-head"><div><h2>Wallet rules</h2><p>Amounts can change later without rewriting transactions.</p></div></div><div class="form-grid two"><div class="form-field"><label>Top-up amount (PKR)</label><input type="number" data-setting="walletTopUp" value="${state.settings.walletTopUp}"></div><div class="form-field"><label>Bonus credit (PKR)</label><input type="number" data-setting="walletBonus" value="${state.settings.walletBonus}"></div><div class="form-field"><label>Expiry after top-up (days)</label><input type="number" data-setting="walletExpiryDays" value="${state.settings.walletExpiryDays}"></div><div class="form-field"><label>Cancellation notice (hours)</label><input type="number" data-setting="cancellationHours" value="${state.settings.cancellationHours}"></div></div><div class="notice notice-info" style="margin-top:14px">Wallet entries are immutable. Refunds and owner corrections create new reversal records with a reason.</div></div><div class="surface panel"><div class="panel-head"><div><h2>Reminder & referral rules</h2><p>Useful timing, clear consent and fraud checks.</p></div></div><div class="form-grid two"><div class="form-field"><label>Default reminder cycle (days)</label><input type="number" data-setting="reminderDefaultDays" value="${state.settings.reminderDefaultDays}"></div><div class="form-field"><label>Referrer reward (PKR)</label><input type="number" data-setting="referralReferrerReward" value="${state.settings.referralReferrerReward}"></div><div class="form-field"><label>New customer discount (PKR)</label><input type="number" data-setting="referralNewCustomerDiscount" value="${state.settings.referralNewCustomerDiscount}"></div><div class="form-field"><label>Monthly referral limit</label><input type="number" data-setting="monthlyReferralLimit" value="${state.settings.monthlyReferralLimit}"></div></div><label class="check-row" style="margin-top:14px"><input type="checkbox" data-setting-bool="promotionsEnabled" ${state.settings.promotionsEnabled?"checked":""}> Allow owner-approved promotional messages. Customer consent still required.</label></div><div class="surface panel"><div class="panel-head"><div><h2>System coverage</h2><p>Collections and automations from the blueprint.</p></div><span class="tag tag-success">Operational</span></div><div class="list">${["Customers","Services","Staff","Bookings","Visits","WalletTransactions","Referrals","Reminders","Settings"].map(x=>`<div class="settings-line"><span>${x}</span><strong>Connected</strong></div>`).join("")}</div><div class="notice notice-success" style="margin-top:14px">Automations active: visit completion, wallet receipt, slot reservation, referral validation and daily owner summary.</div></div><div class="surface panel"><div class="panel-head"><div><h2>Data controls</h2><p>Export test data for review or reset the local workspace.</p></div></div><div style="display:flex;gap:8px;flex-wrap:wrap"><button class="btn btn-secondary" data-action="export-json">Export JSON</button><button class="btn btn-danger" data-action="reset-data">Reset test data</button></div></div></div>`; }

function ownerSettingsV2() { const methods = state.paymentMethods || []; const deposits = state.deposits || []; const withdrawals = state.withdrawals || []; return `${ownerNav()}${ownerHeader("Settings", "Configure your salon rules and review simulated money workflows.", `<button class="btn btn-primary" data-action="save-settings">Save settings</button>`)}<div class="settings-grid"><div class="surface panel"><div class="panel-head"><div><h2>Business profile</h2><p>${htmlesc(state.salon.address)} · ${htmlesc(state.salon.phone)} · ${htmlesc(state.salon.hours)}</p></div><span class="tag tag-neutral">PKR</span></div><div class="notice notice-info">Manual deposit methods are configurable below. No provider API or customer PIN is stored in this demo.</div></div><div class="surface panel"><div class="panel-head"><div><h2>Wallet & withdrawal rules</h2><p>Cash is withdrawable; referral credit is promotional only.</p></div></div><div class="form-grid two"><div class="form-field"><label>Top-up amount (PKR)</label><input type="number" data-setting="walletTopUp" value="${state.settings.walletTopUp}"></div><div class="form-field"><label>Bonus credit (PKR)</label><input type="number" data-setting="walletBonus" value="${state.settings.walletBonus}"></div><div class="form-field"><label>Expiry after top-up (days)</label><input type="number" data-setting="walletExpiryDays" value="${state.settings.walletExpiryDays}"></div><div class="form-field"><label>Minimum withdrawal (PKR)</label><input type="number" data-setting="withdrawalMinimum" value="${state.settings.withdrawalMinimum || 100}"></div><div class="form-field"><label>Daily withdrawal limit (PKR)</label><input type="number" data-setting="withdrawalDailyLimit" value="${state.settings.withdrawalDailyLimit || 10000}"></div><div class="form-field"><label>Cancellation notice (hours)</label><input type="number" data-setting="cancellationHours" value="${state.settings.cancellationHours}"></div></div><div class="notice notice-warning" style="margin-top:14px">A customer deposit claim never credits the wallet until an owner approves it. Every approval, rejection and withdrawal action is auditable.</div></div><div class="surface panel"><div class="panel-head"><div><h2>Payment methods</h2><p>Owner-editable receiving instructions. Keep provider secrets outside the app.</p></div><span class="tag tag-success">${methods.filter(m => m.enabled).length} enabled</span></div><div class="list">${methods.map(m => `<div class="list-row"><div class="list-row-main"><div class="list-title">${htmlesc(m.provider)} ${m.enabled ? statusTag("Credited") : statusTag("Opted out")}</div><div class="list-meta">${htmlesc(m.accountTitle)} · ${htmlesc(m.accountNumber)} · ${htmlesc(m.mode)}</div></div><button class="btn btn-secondary btn-small" data-action="toggle-payment-method" data-provider="${htmlesc(m.provider)}">${m.enabled ? "Disable" : "Enable"}</button></div>`).join("")}</div><div class="notice notice-info" style="margin-top:14px">Customer-facing instructions show the configured account and reference guidance. The demo never calls Easypaisa, JazzCash, NayaPay or SadaPay.</div></div><div class="surface panel"><div class="panel-head"><div><h2>Pending financial reviews</h2><p>Approve only after checking the relevant provider account.</p></div><span class="tag tag-warning">${deposits.filter(d => d.status === "Pending verification").length + withdrawals.filter(w => ["Pending", "Approved"].includes(w.status)).length} pending</span></div>${deposits.length ? `<div class="list">${deposits.map(d => `<div class="list-row"><div class="list-row-main"><div class="list-title">Deposit · ${money(d.amount)} · ${htmlesc(d.provider)}</div><div class="list-meta">${customerName(d.customerId)} · ${htmlesc(d.reference || "No reference")}</div></div>${d.status === "Pending verification" ? `<div class="table-actions"><button class="btn btn-soft btn-small" data-action="approve-deposit" data-deposit-id="${d.id}">Approve</button><button class="btn btn-secondary btn-small" data-action="reject-deposit" data-deposit-id="${d.id}">Reject</button></div>` : statusTag(d.status)}</div>`).join("")}</div>` : `<div class="empty-state"><strong>No deposit claims</strong>New manual claims appear here after customer submission.</div>`}${withdrawals.length ? `<div class="list" style="margin-top:10px">${withdrawals.map(w => `<div class="list-row"><div class="list-row-main"><div class="list-title">Withdrawal · ${money(w.amount)} · ${htmlesc(w.provider)}</div><div class="list-meta">${customerName(w.customerId)} · ${htmlesc(w.destination || "")}</div></div>${["Pending", "Approved"].includes(w.status) ? `<div class="table-actions"><button class="btn btn-soft btn-small" data-action="complete-withdrawal" data-withdrawal-id="${w.id}">Mark paid</button><button class="btn btn-secondary btn-small" data-action="reject-withdrawal" data-withdrawal-id="${w.id}">Reject</button></div>` : statusTag(w.status)}</div>`).join("")}</div>` : `<div class="empty-state"><strong>No withdrawal requests</strong>Customer cash requests appear here.</div>`}<div class="notice notice-warning" style="margin-top:14px">Demo only: marking paid records the owner action locally; it does not send money externally.</div></div><div class="surface panel"><div class="panel-head"><div><h2>Referral & reminders</h2><p>Rewards release only after a completed paid first visit.</p></div></div><div class="form-grid two"><div class="form-field"><label>Default reminder cycle (days)</label><input type="number" data-setting="reminderDefaultDays" value="${state.settings.reminderDefaultDays}"></div><div class="form-field"><label>Referral reward (PKR)</label><input type="number" data-setting="referralReferrerReward" value="${state.settings.referralReferrerReward}"></div><div class="form-field"><label>New customer discount (PKR)</label><input type="number" data-setting="referralNewCustomerDiscount" value="${state.settings.referralNewCustomerDiscount}"></div><div class="form-field"><label>Monthly referral limit</label><input type="number" data-setting="monthlyReferralLimit" value="${state.settings.monthlyReferralLimit}"></div></div><label class="check-row" style="margin-top:14px"><input type="checkbox" data-setting-bool="promotionsEnabled" ${state.settings.promotionsEnabled ? "checked" : ""}> Allow owner-approved promotional messages. Customer consent still required.</label></div><div class="surface panel"><div class="panel-head"><div><h2>Data controls</h2><p>Export test data for review or reset this local workspace.</p></div></div><div style="display:flex;gap:8px;flex-wrap:wrap"><button class="btn btn-secondary" data-action="export-json">Export JSON</button><button class="btn btn-danger" data-action="reset-data">Reset test data</button></div></div></div>`; }

function ownerBrandingPanel() {
  const logo = safeImageSource(state?.salon?.logoDataUrl);
  const preview = logo ? `<div class="logo-preview"><img src="${logo}" alt="Current salon logo"><button class="btn btn-ghost btn-small" data-action="remove-logo">Remove logo</button></div>` : `<div class="logo-placeholder">${htmlesc(salonMarkGlyph())}</div>`;
  const logoEditor = apiModeEnabled()
    ? `<input type="file" accept="image/*" data-logo-input><small>Choose a square JPG, PNG or WEBP up to 2 MB. The app compresses it and uploads it through the authenticated salon API.</small><label class="form-field" style="margin-top:8px"><span>Or use an existing HTTPS image URL</span><input type="url" data-salon-logo-url value="${htmlesc(logo && !logo.startsWith("data:") ? logo : "")}" placeholder="https://images.example.com/ayan-logo.webp"></label>`
    : `<input type="file" accept="image/*" data-logo-input><small>Choose a square JPG, PNG or WEBP up to 2 MB. It is compressed before saving on this device.</small>`;
  const scope = apiModeEnabled() ? "Changes are shared through the salon server." : "Changes apply to customer screens and the owner workspace on this device.";
  return `<div class="surface panel branding-panel"><div class="panel-head"><div><h2>Branding & appearance</h2><p>${scope}</p></div><span class="tag tag-pink">Owner only</span></div><div class="branding-grid"><div class="form-field"><label>Primary colour</label><div class="color-input-row"><input type="color" data-salon-theme-color value="${safeHexColor(state?.salon?.themeColor, DEFAULT_THEME_COLOR)}"><span class="list-meta">Buttons, highlights and navigation</span></div></div><div class="form-field"><label>Salon logo</label>${logoEditor}${preview}</div></div><div class="notice notice-info" style="margin-top:13px">Customer dark mode is controlled from More and stays saved per customer account.</div></div>`;
}

function ownerSettingsProduction() {
  const html = ownerSettingsProductionBase();
  const enhanced = html.replace('<div class="settings-grid">', `<div class="settings-grid">${ownerBrandingPanel()}${phoneAppPanel()}${serverAddressPanel()}`);
  if (!RELEASE_MODE) return enhanced;
  return enhanced
    .replace(/Export test data for review or reset (?:the|this) local workspace\.?/g, "Export salon workspace data or reset this workspace.")
    .replace(/No provider API or customer PIN is stored in this demo\./g, "No provider API or customer PIN is stored in the app.")
    .replace(/Demo only: marking paid records the owner action locally; it does not send money externally\./g, "Marking paid records the owner action locally; external payout is handled by the salon.")
    .replace(/Export JSON/g, "Export workspace data")
    .replace(/Reset test data/g, "Reset workspace");
}

function ownerSettingsProductionBase() {
  const methods = state.paymentMethods || []; const deposits = state.deposits || []; const withdrawals = state.withdrawals || [];
  const providerEditors = methods.slice().sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0)).map((method) => {
    const key = String(method.provider).toLowerCase();
    return `<div class="provider-editor"><div class="section-head"><div><h3>${htmlesc(method.displayName || method.provider)}</h3><span class="list-meta">Manual mode · no provider PINs or API secrets</span></div><label class="check-row"><input type="checkbox" data-provider-enabled="${key}" ${method.enabled ? "checked" : ""}> Enabled</label></div><div class="form-grid two"><div class="form-field"><label>Account title</label><input data-provider-title="${key}" value="${htmlesc(method.accountTitle || "")}"></div><div class="form-field"><label>Account / mobile</label><input type="tel" data-provider-number="${key}" value="${htmlesc(method.accountNumber || "")}"></div><div class="form-field"><label>QR code URI (optional)</label><input data-provider-qr="${key}" value="${htmlesc(method.qrCode || "")}" placeholder="https://..."></div><div class="form-field"><label>Sort order</label><input type="number" min="0" data-provider-order="${key}" value="${Number(method.sortOrder || 0)}"></div></div><div class="form-field"><label>Customer instructions</label><textarea rows="2" data-provider-instructions-input="${key}">${htmlesc(method.instructions || "")}</textarea></div></div>`;
  }).join("");
  return `${ownerNav()}${ownerHeader("Settings", "Configure your salon rules, business details and manual payment workflows.", `<button class="btn btn-primary" data-action="save-settings">Save settings</button>`)}<div class="settings-grid"><div class="surface panel"><div class="panel-head"><div><h2>Business profile</h2><p>These details are shown to customers and on receipts.</p></div><span class="tag tag-neutral">PKR</span></div><div class="form-grid two"><div class="form-field"><label>Salon name</label><input data-salon-name value="${htmlesc(state.salon.name)}"></div><div class="form-field"><label>Tagline</label><input data-salon-tagline value="${htmlesc(state.salon.tagline || "")}"></div><div class="form-field"><label>Phone</label><input type="tel" data-salon-phone value="${htmlesc(state.salon.phone || "")}"></div><div class="form-field"><label>Timezone</label><input data-salon-timezone value="${htmlesc(state.salon.timezone || "Asia/Karachi")}"></div><div class="form-field"><label>Opening hours</label><input data-salon-hours value="${htmlesc(state.salon.hours || "")}"></div><div class="form-field"><label>Currency</label><input value="PKR" disabled></div></div><div class="form-field"><label>Address</label><textarea rows="2" data-salon-address>${htmlesc(state.salon.address || "")}</textarea></div></div><div class="surface panel"><div class="panel-head"><div><h2>Wallet & withdrawal rules</h2><p>Cash is withdrawable; referral credit is promotional only.</p></div></div><div class="form-grid two"><div class="form-field"><label>Top-up amount (PKR)</label><input type="number" min="1" data-setting="walletTopUp" value="${state.settings.walletTopUp}"></div><div class="form-field"><label>Bonus credit (PKR)</label><input type="number" min="0" data-setting="walletBonus" value="${state.settings.walletBonus}"></div><div class="form-field"><label>Expiry after top-up (days)</label><input type="number" min="0" data-setting="walletExpiryDays" value="${state.settings.walletExpiryDays}"></div><div class="form-field"><label>Minimum withdrawal (PKR)</label><input type="number" min="0" data-setting="withdrawalMinimum" value="${state.settings.withdrawalMinimum || 100}"></div><div class="form-field"><label>Daily withdrawal limit (PKR)</label><input type="number" min="0" data-setting="withdrawalDailyLimit" value="${state.settings.withdrawalDailyLimit || 10000}"></div><div class="form-field"><label>Cancellation notice (hours)</label><input type="number" min="0" data-setting="cancellationHours" value="${state.settings.cancellationHours}"></div></div><div class="notice notice-warning" style="margin-top:14px">A customer deposit claim never credits the wallet until an owner approves it. Every approval, rejection and withdrawal action is auditable.</div></div><div class="surface panel"><div class="panel-head"><div><h2>Payment methods</h2><p>Update the receiving account and instructions for each supported provider.</p></div><span class="tag tag-success">${methods.filter(m => m.enabled).length} enabled</span></div><div class="form-grid">${providerEditors}</div><div class="notice notice-info" style="margin-top:14px">Only public receiving details belong here. Never enter a provider password, PIN, API secret or customer credential.</div></div><div class="surface panel"><div class="panel-head"><div><h2>Referral & reminders</h2><p>Rewards release only after a completed paid first visit.</p></div></div><div class="form-grid two"><div class="form-field"><label>Default reminder cycle (days)</label><input type="number" min="0" data-setting="reminderDefaultDays" value="${state.settings.reminderDefaultDays}"></div><div class="form-field"><label>Referral enabled</label><label class="check-row"><input type="checkbox" data-setting-bool="referralEnabled" ${state.settings.referralEnabled !== false ? "checked" : ""}> Allow new referral claims</label></div><div class="form-field"><label>Referrer reward (PKR)</label><input type="number" min="0" data-setting="referralReferrerReward" value="${state.settings.referralReferrerReward}"></div><div class="form-field"><label>New customer discount (PKR)</label><input type="number" min="0" data-setting="referralNewCustomerDiscount" value="${state.settings.referralNewCustomerDiscount}"></div><div class="form-field"><label>Monthly referral limit</label><input type="number" min="0" data-setting="monthlyReferralLimit" value="${state.settings.monthlyReferralLimit}"></div></div><label class="check-row" style="margin-top:14px"><input type="checkbox" data-setting-bool="promotionsEnabled" ${state.settings.promotionsEnabled ? "checked" : ""}> Allow owner-approved promotional messages. Customer consent still required.</label></div><div class="surface panel"><div class="panel-head"><div><h2>Pending financial reviews</h2><p>Approve only after checking the relevant provider account.</p></div><span class="tag tag-warning">${deposits.filter(d => d.status === "Pending verification").length + withdrawals.filter(w => ["Pending", "Approved"].includes(w.status)).length} pending</span></div>${deposits.length ? `<div class="list">${deposits.map(d => `<div class="list-row"><div class="list-row-main"><div class="list-title">Deposit · ${money(d.amount)} · ${htmlesc(d.provider)}</div><div class="list-meta">${customerName(d.customerId)} · ${htmlesc(d.reference || "No reference")}</div></div>${d.status === "Pending verification" ? `<div class="table-actions"><button class="btn btn-soft btn-small" data-action="approve-deposit" data-deposit-id="${d.id}">Approve</button><button class="btn btn-secondary btn-small" data-action="reject-deposit" data-deposit-id="${d.id}">Reject</button></div>` : statusTag(d.status)}</div>`).join("")}</div>` : `<div class="empty-state"><strong>No deposit claims</strong>New manual claims appear here after customer submission.</div>`}${withdrawals.length ? `<div class="list" style="margin-top:10px">${withdrawals.map(w => `<div class="list-row"><div class="list-row-main"><div class="list-title">Withdrawal · ${money(w.amount)} · ${htmlesc(w.provider)}</div><div class="list-meta">${customerName(w.customerId)} · ${htmlesc(w.destination || "")}</div></div>${["Pending", "Approved"].includes(w.status) ? `<div class="table-actions"><button class="btn btn-soft btn-small" data-action="complete-withdrawal" data-withdrawal-id="${w.id}">Mark paid</button><button class="btn btn-secondary btn-small" data-action="reject-withdrawal" data-withdrawal-id="${w.id}">Reject</button></div>` : statusTag(w.status)}</div>`).join("")}</div>` : `<div class="empty-state"><strong>No withdrawal requests</strong>Customer cash requests appear here.</div>`}<div class="notice notice-warning" style="margin-top:14px">Payouts are recorded only after the owner verifies the external payment. Financial records remain append-only.</div></div><div class="surface panel"><div class="panel-head"><div><h2>Data controls</h2><p>Export salon workspace data or reset this workspace.</p></div></div><div style="display:flex;gap:8px;flex-wrap:wrap"><button class="btn btn-secondary" data-action="export-json">Export workspace data</button><button class="btn btn-danger" data-action="reset-data">Reset workspace</button></div></div></div>`;
}

function modal() {
  if (ui.modal.type === "owner-login") return ownerLoginModal(); if (ui.modal.type === "owner-lookup") return ownerLookupModal(); if (ui.modal.type === "owner-code") return ownerCodeModal(); if (ui.modal.type === "booking") return bookingModal(); if (ui.modal.type === "booking-empty") return bookingUnavailableModal(ui.modal.reason); if (ui.modal.type === "customer-required") return customerRequiredModal(); if (ui.modal.type === "wallet") return walletModal(); if (ui.modal.type === "withdrawal") return withdrawalModal(); if (ui.modal.type === "customer") return customerModal(); if (ui.modal.type === "service") return serviceModal(); if (ui.modal.type === "staff") return staffModal(); if (ui.modal.type === "addon") return addonModal(); if (ui.modal.type === "haircut-style") return haircutStyleModal(); if (ui.modal.type === "style-photo") return stylePhotoModal(); if (ui.modal.type === "booking-detail") return bookingDetailModal(); if (ui.modal.type === "lookup") return lookupModal(); if (ui.modal.type === "otp") return otpModal(); if (ui.modal.type === "wallet-adjust") return walletAdjustModal(); return "";
}
function modalShell(title, desc, content, footer = "") { return `<div class="modal-backdrop" data-action="close-modal"><div class="modal" role="dialog" aria-modal="true" data-modal-content><div class="modal-head"><div>${state.salon.name ? '<div class="eyebrow">' + htmlesc(state.salon.name) + '</div>' : ""}<h2>${title}</h2>${desc?`<p>${desc}</p>`:""}</div><button class="icon-button" data-action="close-modal" aria-label="Close">×</button></div>${content}${footer?`<div class="modal-footer">${footer}</div>`:""}</div></div>`; }
function customerRequiredModal() {
  return modalShell("Create a customer profile first", "A booking must belong to a verified salon customer.", `<div class="empty-state"><strong>No active customer profile is selected</strong>Find an existing profile by mobile number or create a new one before choosing a service and slot.</div>`, `<button class="btn btn-secondary" data-action="customer-lookup">Find profile</button><button class="btn btn-primary" data-action="start-customer-signup">Create profile</button>`);
}
function ownerLoginModal() {
  const message = ui.modal?.error ? `<div class="notice notice-warning" role="alert">${htmlesc(ui.modal.error)}</div>` : "";
  const locked = Date.now() < ownerLockoutUntil;
  const lockMessage = locked ? `<div class="notice notice-warning" role="alert">Too many attempts. Try again in a moment.</div>` : "";
  return modalShell("Owner sign in", "Enter the salon access code to open the private owner workspace.", `<div class="form-grid"><div class="form-field"><label for="owner-access-code">Owner access code</label><input id="owner-access-code" type="password" inputmode="text" autocomplete="current-password" data-owner-access-code placeholder="Enter access code" ${locked ? "disabled" : ""}><small>Customer accounts never expose owner controls.</small></div>${message}${lockMessage}</div>`, `<button class="btn btn-secondary" data-action="close-modal">Cancel</button><button class="btn btn-primary" data-action="owner-login" ${locked ? "disabled" : ""}>Unlock owner workspace</button>`);
}

function ownerLookupModal() {
  const m = ui.modal || {};
  const busy = ui.apiBusy ? "disabled" : "";
  const message = m.error ? `<div class="notice notice-warning" role="alert">${htmlesc(m.error)}</div>` : "";
  return modalShell("Owner sign in", "Type the owner password. Customers never see this workspace.", `<div class="form-grid"><div class="form-field"><label for="owner-secret">Owner password</label><input id="owner-secret" data-owner-secret type="password" inputmode="text" autocomplete="current-password" maxlength="20" placeholder="10 to 20 characters" ${busy}><small>On the salon laptop run ops\\show-owner-password.cmd if you forgot it. Five wrong tries pause sign-in for 15 minutes.</small></div><div class="form-field"><label for="owner-phone">Owner mobile (optional)</label><input id="owner-phone" data-owner-phone type="tel" inputmode="tel" autocomplete="tel" placeholder="Leave empty to use the password alone" value="${htmlesc(m.phone || "")}" ${busy}><small>Add the owner mobile number when you want a second check. The password alone also works.</small></div>${message}<div class="notice notice-info">No SMS is used anywhere in this app. The password is stored only as a salted digest on your own laptop.</div></div>`, `<button class="btn btn-secondary" data-action="close-modal">Cancel</button><button class="btn btn-primary" data-action="owner-password-login" ${busy}>Sign in</button>`);
}
function ownerCodeModal() {
  return modalShell("Change owner code", "Choose a new code for the private owner workspace.", `<div class="form-grid"><div class="form-field"><label for="new-owner-code">New access code</label><input id="new-owner-code" type="password" inputmode="text" autocomplete="new-password" data-new-owner-code placeholder="6 to 32 letters or numbers"></div><div class="form-field"><label for="confirm-owner-code">Confirm access code</label><input id="confirm-owner-code" type="password" inputmode="text" autocomplete="new-password" data-confirm-owner-code placeholder="Repeat the new code"></div><small>The code is stored locally as a digest. Keep it private and replace the initial code before distribution.</small></div>`, `<button class="btn btn-secondary" data-action="close-modal">Cancel</button><button class="btn btn-primary" data-action="save-owner-code">Save new code</button>`);
}
function walletAdjustModal() {
  const m = ui.modal; const c = state.customers.find(x => x.id === m.customerId); if (!c) return "";
  const transactions = (state.walletTransactions || []).filter((t) => t.customerId === c.id);
  const sourceOptions = transactions.length ? transactions.map((t) => `<option value="${htmlesc(t.id)}">${htmlesc(t.type)} · ${t.amount >= 0 ? "+" : ""}${money(t.amount)} · ${dateLabel(String(t.createdAt || "").slice(0, 10))}</option>`).join("") : `<option value="">No prior wallet entries</option>`;
  return modalShell("Adjust salon wallet", `${htmlesc(c.name)} · current balance ${money(totalWallet(c))}`, `<div class="form-grid"><div class="form-field"><label>Entry type</label><select data-adjust-type><option value="Credit">Credit</option><option value="Debit">Debit</option><option value="Refund">Refund</option><option value="Reversal">Reversal</option></select></div><div class="form-field"><label>Credit bucket</label><select data-adjust-bucket><option value="paidCredit">Paid credit</option><option value="bonusCredit">Bonus credit</option></select></div><div class="form-field"><label>Source transaction (required for refund/reversal)</label><select data-adjust-reference><option value="">Choose a source transaction</option>${sourceOptions}</select></div><div class="form-field"><label>Amount (PKR)</label><input type="number" min="1" data-adjust-amount placeholder="e.g. 100"></div><div class="form-field"><label>Reason (required)</label><textarea data-adjust-reason rows="3" placeholder="Why is this adjustment needed?"></textarea></div><div class="notice notice-warning">Every manual change creates a new immutable ledger entry. The original financial record remains visible; reversals point back to that entry.</div></div>`, `<button class="btn btn-secondary" data-action="close-modal">Cancel</button><button class="btn btn-primary" data-action="save-wallet-adjustment" data-customer-id="${c.id}">Save adjustment</button>`);
}
function bookingModal() {
  /*
  const m=ui.modal; const selectedService=service(m.serviceId)||state.services[0]; const selectedAddons=m.addonIds||[]; const selectedStaff=staff(m.staffId)||state.staff[0]; const total=selectedService.price+selectedAddons.reduce((s,id)=>s+(addon(id)?.price||0),0); const duration=selectedService.duration+selectedAddons.reduce((s,id)=>s+(addon(id)?.duration||0),0); const date=m.date||addDays(iso(today),1); const slots=["10:00","11:30","13:00","15:00","16:00","17:30","18:00","19:30","20:30"]; const eligibleAddons=state.addons.filter(a=>a.active&&a.serviceIds.includes(selectedService.id)); const busy=(time)=>activeBookingsFor(date, selectedStaff.id).some(b=>b.time===time); return modalShell("Book a visit", "Transparent total, optional add-ons and a slot reserved only after confirmation.", `<div class="form-grid"><div class="form-field"><label>Service</label><div class="service-picker">${state.services.filter(s=>s.active).map(s=>`<button class="service-option ${s.id===selectedService.id?"selected":""}" data-action="select-booking-service" data-service-id="${s.id}"><span><strong>${htmlesc(s.name)}</strong><small>${htmlesc(s.category)} · ${s.duration} mins</small></span><span class="service-price">${money(s.price)}</span></button>`).join("")}</div></div>${eligibleAddons.length?`<div class="form-field"><label>Optional add-ons</label>${eligibleAddons.map(a=>`<label class="addon-option ${selectedAddons.includes(a.id)?"selected":""}"><span class="addon-copy"><input type="checkbox" data-booking-addon="${a.id}" ${selectedAddons.includes(a.id)?"checked":""}><span><strong>${htmlesc(a.name)}</strong><small>+${a.duration} mins · +${money(a.price)}</small></span></span></label>`).join("")}</div>`:"<div class="notice notice-info">No add-ons are suggested for this service. You can still update the menu in Owner mode.</div>"}<div class="form-grid two"><div class="form-field"><label>Date</label><input type="date" data-booking-date value="${date}" min="${iso(today)}"></div><div class="form-field"><label>Preferred barber</label><select data-booking-staff>${state.staff.filter(s=>s.active).map(st=>`<option value="${st.id}" ${st.id===selectedStaff.id?"selected":""}>${htmlesc(st.name)}</option>`).join("")}</select></div></div><div class="form-field"><label>Available slots</label><div class="slot-grid">${slots.map(slot=>`<button class="slot ${m.time===slot?"selected":""} ${busy(slot)?"unavailable":""}" data-action="select-booking-slot" data-slot="${slot}" ${busy(slot)?"disabled":""}>${slot}</button>`).join("")}</div><small>Full slots are unavailable for the selected barber. Choose another barber or time.</small></div>${m.customerId&&m.customerId!==customer().id?`<div class="notice notice-info">Booking for ${htmlesc(customerName(m.customerId))}</div>`:""}<label class="check-row"><input type="checkbox" data-booking-wallet ${m.useWallet?"checked":""}> Use available salon credit at checkout (balance: ${money(totalWallet(state.customers.find(c=>c.id===m.customerId)||customer()))})</label><div class="summary-box"><div class="summary-line"><span>${htmlesc(selectedService.name)}</span><strong>${money(selectedService.price)}</strong></div>${selectedAddons.map(id=>`<div class="summary-line"><span>${htmlesc(addon(id)?.name||"")} add-on</span><strong>+${money(addon(id)?.price||0)}</strong></div>`).join("")}<div class="summary-line"><span>Estimated duration</span><strong>${duration} mins</strong></div><div class="summary-line total"><span>Final total</span><strong>${money(total)}</strong></div></div></div>`, `<button class="btn btn-secondary" data-action="close-modal">Cancel</button><button class="btn btn-primary" data-action="confirm-booking" ${m.time?"":"disabled"}>Confirm booking</button>`); }
  */
  return bookingModalSafe();
}
function bookingUnavailableModal(reason) {
  const title = reason === "service" ? "Booking is temporarily unavailable" : "No barber is available";
  const message = reason === "service"
    ? "The salon has no active services yet. Ask the owner to activate a service before booking."
    : "No active team member can take this service right now. Ask the owner to activate a barber or update their skills and hours.";
  return modalShell(title, message, `<div class="empty-state"><strong>${htmlesc(title)}</strong>${htmlesc(message)}</div>`, `<button class="btn btn-primary" data-action="close-modal">Close</button>`);
}
function bookingModalSafe() {
  const m = ui.modal;
  const services = activeServices();
  const selectedService = services.find((candidate) => candidate.id === m.serviceId) || services[0];
  if (!selectedService) return bookingUnavailableModal("service");
  const selectedAddonIds = (m.addonIds || []).filter((id) => addon(id)?.active && addon(id).serviceIds.includes(selectedService.id));
  const eligibleStaff = activeStaff().filter((candidate) => staffCanPerform(candidate, selectedService));
  const selectedStaff = eligibleStaff.find((candidate) => candidate.id === m.staffId) || eligibleStaff[0] || null;
  if (selectedStaff) m.staffId = selectedStaff.id; else m.staffId = null;
  const selectedAddons = selectedAddonIds.filter((id) => !selectedStaff || addonEligibleForStaff(addon(id), selectedStaff));
  m.addonIds = selectedAddons;
  const bookingCustomer = state.customers.find((c) => c.id === m.customerId) || customer();
  const configuredReferralDiscount = eligibleReferralDiscount(bookingCustomer);
  const subtotal = selectedService.price + selectedAddons.reduce((sum, id) => sum + (addon(id)?.price || 0), 0);
  const referralDiscount = Math.min(subtotal, Math.max(0, Number.isFinite(configuredReferralDiscount) ? configuredReferralDiscount : 0));
  const total = subtotal - referralDiscount;
  const duration = selectedService.duration + selectedAddons.reduce((sum, id) => sum + (addon(id)?.duration || 0), 0);
  const date = m.date || addDays(iso(today), 1);
  const slots = ["10:00", "11:30", "13:00", "15:00", "16:00", "17:30", "18:00", "19:30", "20:30"];
  const eligibleAddons = (state.addons || []).filter((a) => a.active && a.serviceIds.includes(selectedService.id) && (!selectedStaff || addonEligibleForStaff(a, selectedStaff)));
  const busy = (time) => slotBusy(date, selectedStaff?.id || "", time, duration) || !staffCanPerform(selectedStaff, selectedService);
  const serviceOptions = services.map((s) => {
    const selected = s.id === selectedService.id ? "selected" : "";
    return `<button class="service-option ${selected}" data-action="select-booking-service" data-service-id="${s.id}"><span><strong>${htmlesc(s.name)}</strong><small>${htmlesc(s.category)} · ${s.duration} mins</small></span><span class="service-price">${money(s.price)}</span></button>`;
  }).join("");
  const addonOptions = eligibleAddons.length ? eligibleAddons.map((a) => {
    const selected = selectedAddons.includes(a.id);
    return `<label class="addon-option ${selected ? "selected" : ""}"><span class="addon-copy"><input type="checkbox" data-booking-addon="${a.id}" ${selected ? "checked" : ""}><span><strong>${htmlesc(a.name)}</strong><small>+${a.duration} mins · +${money(a.price)}</small></span></span></label>`;
  }).join("") : `<div class="notice notice-info">No add-ons are suggested for this service. You can update the menu in Owner mode.</div>`;
  const staffOptions = eligibleStaff.map((s) => `<option value="${s.id}" ${s.id === selectedStaff?.id ? "selected" : ""}>${htmlesc(s.name)}</option>`).join("");
  const slotOptions = slots.map((slot) => `<button class="slot ${m.time === slot ? "selected" : ""} ${busy(slot) ? "unavailable" : ""}" data-action="select-booking-slot" data-slot="${slot}" ${busy(slot) ? "disabled" : ""}>${slot}</button>`).join("");
  const addonSummary = selectedAddons.map((id) => `<div class="summary-line"><span>${htmlesc(addon(id)?.name || "")} add-on</span><strong>+${money(addon(id)?.price || 0)}</strong></div>`).join("");
  const customerNotice = m.customerId && m.customerId !== customer().id ? `<div class="notice notice-info">Booking for ${htmlesc(customerName(m.customerId))}</div>` : "";
  const checkoutCustomer = bookingCustomer;
  const discountLine = referralDiscount ? `<div class="summary-line"><span>Referral welcome discount</span><strong>-${money(referralDiscount)}</strong></div>` : "";
  const totalWarning = total <= 0 ? `<div class="notice notice-warning">The configured referral discount must leave a positive paid total. Ask the owner to adjust the discount or service price.</div>` : "";
  const noStaffNotice = staffOptions ? "" : `<div class="notice notice-warning">No active team member is assigned to this service. Ask the owner to update staff skills.</div>`;
  const apiBookingError = m.error ? `<div class="notice notice-warning" role="alert">${htmlesc(m.error)}</div>` : "";
  const content = `<div class="form-grid">${apiBookingError}<div class="form-field"><label>Service</label><div class="service-picker">${serviceOptions}</div></div><div class="form-field"><label>Optional add-ons</label>${addonOptions}</div><div class="form-grid two"><div class="form-field"><label>Date</label><input type="date" data-booking-date value="${date}" min="${iso(today)}"></div><div class="form-field"><label>Preferred barber</label><select data-booking-staff ${staffOptions ? "" : "disabled"}>${staffOptions}</select></div></div>${noStaffNotice}<div class="form-field"><label>Available slots</label><div class="slot-grid">${slotOptions}</div><small>Slots respect staff hours, service duration and overlapping bookings.</small></div>${customerNotice}${referralDiscount ? `<div class="notice notice-success">Referral code applied: ${money(referralDiscount)} off this first paid visit.</div>` : ""}${totalWarning}<label class="check-row"><input type="checkbox" data-booking-wallet ${m.useWallet ? "checked" : ""} ${walletExpired(checkoutCustomer) ? "disabled" : ""}> Use available salon credit at checkout (unreserved balance: ${money(availableWallet(checkoutCustomer))})</label>${walletExpired(checkoutCustomer) ? `<div class="notice notice-warning">This customer's wallet credit has expired and cannot be used for checkout.</div>` : ""}<div class="summary-box"><div class="summary-line"><span>${htmlesc(selectedService.name)}</span><strong>${money(selectedService.price)}</strong></div>${addonSummary}${discountLine}<div class="summary-line"><span>Estimated duration</span><strong>${duration} mins</strong></div><div class="summary-line total"><span>Final total</span><strong>${money(total)}</strong></div></div></div>`;
  const footer = `<button class="btn btn-secondary" data-action="close-modal">Cancel</button><button class="btn btn-primary" data-action="confirm-booking" ${m.time && selectedStaff && total > 0 && !ui.apiBusy ? "" : "disabled"}>${ui.apiBusy ? "Confirming..." : "Confirm booking"}</button>`;
  return modalShell("Book a visit", "Transparent total, optional add-ons and a slot reserved only after confirmation.", content, footer);
}
function apiWalletModal() {
  const methods = (state.paymentMethods || []).filter((m) => m.enabled).sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0));
  const c = customer();
  const bonus = c.firstDepositBonusClaimed === true ? 0 : Number(state.settings.walletBonus || 0);
  const error = ui.modal?.error ? `<div class="notice notice-warning" role="alert">${htmlesc(ui.modal.error)}</div>` : "";
  const busy = ui.apiBusy ? "disabled" : "";
  const options = methods.length ? methods.map((m) => `<option value="${htmlesc(m.provider)}">${htmlesc(m.displayName || m.provider)}</option>`).join("") : `<option value="">No payment methods enabled</option>`;
  return modalShell("Add money", "Submit a payment reference for owner verification. The server credits your wallet only after approval.", `<div class="form-grid">${error}<div class="form-field"><label>Deposit amount (PKR)</label><input type="number" min="1" step="1" data-deposit-amount value="${Number(state.settings.walletTopUp || 500)}" ${busy}><small>Enter the exact amount you sent. The salon may change this guidance later.</small></div><div class="notice notice-info">${bonus ? `Your first approved deposit may include a one-time ${money(bonus)} bonus. Later deposits receive paid credit only.` : "Your one-time new-customer deposit bonus has already been used."}</div><div class="form-field"><label>Payment method</label><select data-deposit-provider ${busy}>${options}</select></div>${methods.length ? `<div class="notice notice-info" data-provider-instructions>${htmlesc(methods[0].instructions || "Follow the salon's payment instructions.")}</div>` : ""}<div class="form-field"><label>Transaction / reference ID</label><input data-deposit-reference placeholder="e.g. EP-ABC-123" ${busy}><small>Required so the owner can match the payment.</small></div><div class="form-field"><label>Proof note (optional)</label><input data-deposit-proof placeholder="Optional note" ${busy}></div></div>`, `<button class="btn btn-secondary" data-action="close-modal">Cancel</button><button class="btn btn-primary" data-action="confirm-wallet" ${methods.length && !ui.apiBusy ? "" : "disabled"}>${ui.apiBusy ? "Submitting..." : "Submit for verification"}</button>`);
}

function walletModal() {
  if (apiModeEnabled()) return apiWalletModal();
  const methods = (state.paymentMethods || []).filter(m => m.enabled).sort((a,b) => (a.sortOrder || 0) - (b.sortOrder || 0));
  const description = RELEASE_MODE ? "Submit a payment reference for owner verification. Credit is added after the salon confirms the payment." : "Local QA: submit a manual deposit claim. Wallet credit is added only after the owner verifies the provider account.";
  const stateControl = RELEASE_MODE ? "" : `<div class="form-field"><label>Submission state</label><select data-deposit-state><option value="pending">Submit as pending verification</option><option value="failed">Test a failed submission</option></select></div>`;
  const c = customer(); const bonusAvailable = c.firstDepositBonusClaimed === true ? 0 : Number(state.settings.walletBonus || 0);
  return modalShell("Add money", description, `<div class="form-grid"><div class="summary-box"><div class="summary-line"><span>Deposit amount</span><strong>${money(state.settings.walletTopUp)}</strong></div><div class="summary-line"><span>First approved deposit bonus</span><strong>+${money(bonusAvailable)}</strong></div><div class="summary-line total"><span>After approval</span><strong>${money(state.settings.walletTopUp+bonusAvailable)}</strong></div></div><div class="notice notice-warning">Never send a PIN or password. The salon checks the provider payment before crediting your wallet.</div>${bonusAvailable ? `<div class="notice notice-info">The PKR ${Number(bonusAvailable).toLocaleString("en-PK")} bonus is a one-time new-customer benefit. Later deposits receive paid credit only.</div>` : `<div class="notice notice-info">Your one-time new-customer deposit bonus has already been used. Later deposits receive paid credit only.</div>`}<div class="form-field"><label>Payment method</label><select data-deposit-provider>${methods.length ? methods.map(m => `<option value="${htmlesc(m.provider)}">${htmlesc(m.provider)} · ${htmlesc(m.accountNumber)}</option>`).join("") : `<option value="">No methods enabled</option>`}</select></div>${methods.length ? `<div class="notice notice-info" data-provider-instructions>${htmlesc(methods[0].instructions || "Send the exact amount, then submit the reference.")}</div>` : ""}<div class="form-field"><label>Transaction / reference ID</label><input data-deposit-reference placeholder="e.g. EP-ABC-123"><small>Required so the owner can match the payment in the provider account.</small></div><div class="form-field"><label>Proof note (optional)</label><input data-deposit-proof placeholder="e.g. payment reference noted by the salon"></div>${stateControl}</div>`, `<button class="btn btn-secondary" data-action="close-modal">Cancel</button><button class="btn btn-primary" data-action="confirm-wallet" ${methods.length ? "" : "disabled"}>Submit for verification</button>`);
}
function serviceModal() { const m=ui.modal; const s=state.services.find(x=>x.id===m.serviceId)||{name:"",category:"Hair",price:0,duration:30,repeatDays:25,active:true}; return modalShell(m.serviceId?"Edit service":"Add service", "Prices and timing remain editable by the salon owner.", `<div class="form-grid two"><div class="form-field"><label>Service name</label><input data-service-name value="${htmlesc(s.name)}"></div><div class="form-field"><label>Category</label><input data-service-category value="${htmlesc(s.category)}"></div><div class="form-field"><label>Price (PKR)</label><input type="number" data-service-price value="${s.price}"></div><div class="form-field"><label>Duration (minutes)</label><input type="number" data-service-duration value="${s.duration}"></div><div class="form-field"><label>Repeat cycle (days)</label><input type="number" data-service-repeat value="${s.repeatDays}"></div></div><label class="check-row"><input type="checkbox" data-service-active ${s.active ? "checked" : ""}> Available for new bookings</label>`, `<button class="btn btn-secondary" data-action="close-modal">Cancel</button><button class="btn btn-primary" data-action="save-service" data-service-id="${m.serviceId||""}">Save service</button>`); }
function staffModal() { const m=ui.modal; const st=state.staff.find(x=>x.id===m.staffId)||{name:"",role:"Barber",skills:["Hair"],hours:"10:00 - 20:00",active:true}; return modalShell(m.staffId?"Edit staff member":"Add staff member", "Working hours are used to guide available booking slots.", `<div class="form-grid"><div class="form-grid two"><div class="form-field"><label>Name</label><input data-staff-name value="${htmlesc(st.name)}"></div><div class="form-field"><label>Role</label><input data-staff-role value="${htmlesc(st.role)}"></div></div><div class="form-field"><label>Working hours</label><input data-staff-hours value="${htmlesc(st.hours)}"></div><div class="form-field"><label>Skills</label><input data-staff-skills value="${htmlesc(st.skills.join(", "))}"><small>Comma-separated service categories.</small></div><label class="check-row"><input type="checkbox" data-staff-active ${st.active ? "checked" : ""}> Active for new bookings</label></div>`, `<button class="btn btn-secondary" data-action="close-modal">Cancel</button><button class="btn btn-primary" data-action="save-staff" data-staff-id="${m.staffId||""}">Save staff member</button>`); }
function bookingDetailModal() {
  const b = state.bookings.find((x) => x.id === ui.modal.bookingId); if (!b) return "";
  const s = service(b.serviceId); const c = state.customers.find((x) => x.id === b.customerId);
  const paymentState = b.paymentMethod === "Salon credit" ? "Wallet debit on completion" : (b.paymentStatus || (b.status === "Completed" || b.paymentMethod === "Cash" ? "Paid" : "Pending"));
  const paymentControl = b.paymentMethod === "Salon credit"
    ? `<div class="settings-line"><span>Payment</span><strong>Salon credit · debited on completion</strong></div>`
    : `<div class="form-field" style="margin-top:15px"><label>Payment status</label><select data-detail-payment ${b.status === "Completed" ? "disabled" : ""}><option value="Pending" ${paymentState === "Pending" ? "selected" : ""}>Pending · collect at salon</option><option value="Paid" ${paymentState === "Paid" ? "selected" : ""}>Paid / confirmed</option></select><small>Mark paid only after cash or an approved provider payment is actually received.</small></div>`;
  return modalShell("Booking details", "Booking history stays intact even when a status changes.", `<div class="settings-line"><span>Customer</span><strong>${htmlesc(c?.name)}</strong></div><div class="settings-line"><span>Appointment</span><strong>${dateLabel(b.date)} at ${b.time}</strong></div><div class="settings-line"><span>Service</span><strong>${htmlesc(s?.name)} · ${money(b.total)}</strong></div><div class="settings-line"><span>Payment method</span><strong>${htmlesc(b.paymentMethod || "Pay at salon")}</strong></div>${paymentControl}<div class="settings-line"><span>Source</span><strong>${htmlesc(b.source)}</strong></div><div class="settings-line"><span>Status</span><strong>${statusTag(b.status)}</strong></div><div class="form-field" style="margin-top:15px"><label>Update status</label><select data-detail-status>${["Pending","Confirmed","Completed","Cancelled","No-show"].map(x=>`<option ${x===b.status?"selected":""}>${x}</option>`).join("")}</select></div><div class="notice notice-info" style="margin-top:14px">For wallet corrections, use a separate adjustment with a mandatory reason. Financial records are never deleted.</div>`, `<button class="btn btn-secondary" data-action="close-modal">Close</button><button class="btn btn-primary" data-action="save-booking-status" data-booking-id="${b.id}">Save status</button>`);
}
function withdrawalModal() {
  const c = customer(); const max = withdrawableCash(c); const methods = (state.paymentMethods || []).filter(m => m.enabled).sort((a,b) => (a.sortOrder || 0) - (b.sortOrder || 0));
  const description = RELEASE_MODE ? "Cash requests are reviewed and paid by the salon owner. Promotional/referral credit is never eligible." : "Local QA: the owner verifies and pays this request manually. Promotional/referral credit is never eligible.";
  const note = RELEASE_MODE ? "Your cash is reserved immediately and becomes available again if the owner rejects the request." : "Your cash is reserved immediately and becomes available again if the owner rejects the request. The owner records the payout after verification.";
  return modalShell("Withdraw cash", description, `<div class="form-grid"><div class="summary-box"><div class="summary-line"><span>Withdrawable cash</span><strong>${money(max)}</strong></div><div class="summary-line"><span>Promotional balance</span><strong>${money(c.bonusCredit || 0)} · not withdrawable</strong></div><div class="summary-line"><span>Minimum</span><strong>${money(state.settings.withdrawalMinimum || 100)}</strong></div></div><div class="form-field"><label>Amount (PKR)</label><input type="number" min="${state.settings.withdrawalMinimum || 100}" max="${max}" data-withdrawal-amount placeholder="e.g. 200"></div><div class="form-field"><label>Payout method</label><select data-withdrawal-provider>${methods.map(m => `<option value="${htmlesc(m.provider)}">${htmlesc(m.provider)}</option>`).join("")}</select></div><div class="form-field"><label>Your payout account / mobile</label><input data-withdrawal-destination placeholder="03xx xxx xxxx"></div><div class="notice notice-warning">${note}</div></div>`, `<button class="btn btn-secondary" data-action="close-modal">Cancel</button><button class="btn btn-primary" data-action="submit-withdrawal" ${max < Number(state.settings.withdrawalMinimum || 100) ? "disabled" : ""}>Submit request</button>`);
}

function openBooking(extra = {}) {
  const requestedCustomer = state.customers.find((candidate) => candidate.id === extra.customerId) || currentCustomer();
  if (!requestedCustomer) {
    ui.modal = { type: "customer-required" };
    render();
    return;
  }
  const services = activeServices();
  if (!services.length) { ui.modal = { type: "booking-empty", reason: "service" }; render(); return; }
  if (!activeStaff().length) { ui.modal = { type: "booking-empty", reason: "staff" }; render(); return; }
  const requested = services.find((candidate) => candidate.id === extra.serviceId);
  const selectedService = requested || services[0];
  const eligible = activeStaff().filter((candidate) => staffCanPerform(candidate, selectedService));
  ui.modal = { type: "booking", serviceId: selectedService.id, addonIds: [], staffId: eligible.find((candidate) => candidate.id === extra.staffId)?.id || eligible[0]?.id || null, date: addDays(iso(today), 1), time: null, useWallet: false, customerId: requestedCustomer.id, source: extra.source || (extra.customerId && extra.customerId !== state.currentCustomerId ? "Walk-in" : "Direct") };
  render();
}

function openOwnerGate() {
  if (ownerSessionActive()) return;
  const now = Date.now();
  if (now - ownerTapWindowStartedAt > 1800) {
    ownerTapWindowStartedAt = now;
    ownerTapCount = 0;
  }
  ownerTapCount += 1;
  if (ownerTapCount < 5) return;
  ownerTapCount = 0;
  ownerTapWindowStartedAt = 0;
  ui.modal = apiModeEnabled()
    ? { type: "owner-lookup", stage: "password", phone: "", error: "" }
    : { type: "owner-login", error: "" };
  render();
  setTimeout(() => {
    // The password is the primary owner key, so focus it first.
    const selector = apiModeEnabled() ? "[data-owner-secret]" : "[data-owner-access-code]";
    document.querySelector(selector)?.focus();
  }, 0);
}

function unlockOwner() {
  if (apiModeEnabled()) { toast("Open the owner workspace from the salon logo, or use the Owner sign in button.", "error"); return; }
  if (Date.now() < ownerLockoutUntil) { render(); return; }
  const input = document.querySelector("[data-owner-access-code]");
  const code = String(input?.value || "").trim();
  if (!validOwnerAccessCode(code) || hashOwnerAccessCode(code) !== ownerCodeDigest()) {
    ownerFailedAttempts += 1;
    if (ownerFailedAttempts >= 5) {
      ownerLockoutUntil = Date.now() + 30000;
      ownerFailedAttempts = 0;
      ui.modal = { type: "owner-login", error: "Too many incorrect codes. Try again in 30 seconds." };
      if (ownerLockoutTimer) clearTimeout(ownerLockoutTimer);
      ownerLockoutTimer = setTimeout(() => { ownerLockoutTimer = null; if (ui.modal?.type === "owner-login") render(); }, 30100);
    } else {
      ui.modal = { type: "owner-login", error: "That access code is not valid." };
    }
    render();
    setTimeout(() => document.querySelector("[data-owner-access-code]")?.focus(), 0);
    return;
  }
  ownerFailedAttempts = 0;
  ownerLockoutUntil = 0;
  if (ownerLockoutTimer) { clearTimeout(ownerLockoutTimer); ownerLockoutTimer = null; }
  ownerSessionStartedAt = Date.now();
  if (ownerSessionTimer) clearTimeout(ownerSessionTimer);
  ownerSessionTimer = setTimeout(() => {
    ownerSessionTimer = null;
    if (!ui.ownerAuthenticated) return;
    ui.ownerAuthenticated = false;
    ui.role = "customer";
    ui.ownerScreen = "dashboard";
    ui.modal = null;
    state.currentCustomerId = null;
    saveState();
    ownerSessionStartedAt = 0;
    toast("Owner workspace locked after 15 minutes");
    render();
  }, OWNER_SESSION_MAX_MS + 50);
  ui.ownerAuthenticated = true;
  ui.role = "owner";
  ui.ownerScreen = "dashboard";
  ui.modal = null;
  state.audit.push({ id: uid("audit"), action: "Owner workspace unlocked", actor: "Owner", reason: "Local owner access code accepted", createdAt: new Date().toISOString() });
  saveState();
  toast("Owner workspace unlocked");
  render();
}

function saveOwnerCode() {
  if (!requireOwnerSession()) return;
  const code = String(document.querySelector("[data-new-owner-code]")?.value || "").trim();
  const confirmation = String(document.querySelector("[data-confirm-owner-code]")?.value || "").trim();
  if (!validOwnerAccessCode(code)) { toast("Use 6 to 32 letters or numbers for the owner code.", "error"); return; }
  if (code !== confirmation) { toast("The two owner codes do not match.", "error"); return; }
  state.settings.ownerAccessCodeHash = hashOwnerAccessCode(code);
  state.audit.push({ id: uid("audit"), action: "Owner access code changed", actor: "Owner", reason: "Local owner access code rotated", createdAt: new Date().toISOString() });
  saveState();
  ui.modal = null;
  toast("Owner access code changed");
  render();
}

function lockOwner() {
  if (apiModeEnabled()) {
    (async () => {
      try { await window.AyanApi.logout(); } catch (_) { window.AyanApi.clearSession(); }
      clearRemoteCustomerState();
      ui.ownerAuthenticated = false;
      ui.role = "customer";
      ui.modal = null;
      ui.customerScreen = "home";
      render();
    })();
    return;
  }
  ui.ownerAuthenticated = false;
  ui.role = "customer";
  ui.ownerScreen = "dashboard";
  ui.modal = null;
  state.currentCustomerId = null;
  saveState();
  ownerSessionStartedAt = 0;
  if (ownerLockoutTimer) { clearTimeout(ownerLockoutTimer); ownerLockoutTimer = null; }
  if (ownerSessionTimer) { clearTimeout(ownerSessionTimer); ownerSessionTimer = null; }
  ownerTapCount = 0;
  toast("Owner workspace locked");
  render();
}

function requireOwnerSession() {
  if (ownerSessionActive()) return true;
  if (apiModeEnabled()) {
    toast("Owner verification is required for this action.", "error");
    return false;
  }
  ui.role = "customer";
  ui.modal = { type: "owner-login", error: "Unlock the owner workspace first." };
  render();
  return false;
}

function bindEvents() {
  document.querySelectorAll("[data-owner-gesture]").forEach((el) => {
    el.onclick = openOwnerGate;
    el.onkeydown = (event) => { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); openOwnerGate(); } };
  });
  document.querySelectorAll("[data-customer-screen]").forEach(b=>b.onclick=()=>{ui.customerScreen=b.dataset.customerScreen;render();});
  document.querySelectorAll("[data-owner-screen]").forEach(b=>b.onclick=()=>{if (!requireOwnerSession()) return; ui.ownerScreen=b.dataset.ownerScreen;ui.search="";render();});
  document.querySelectorAll("[data-report-tab]").forEach(b=>b.onclick=()=>{ui.reportTab=b.dataset.reportTab;render();});
  document.querySelectorAll("[data-action]").forEach(el=>el.addEventListener("click", handleAction));
  const search=document.querySelector("[data-customer-search]"); if(search) search.oninput=e=>{ui.search=e.target.value;render();};
  const bsearch=document.querySelector("[data-filter-bookings]"); if(bsearch) bsearch.oninput=e=>{ui.search=e.target.value;render();};
  ["[data-filter-date]","[data-filter-status]"].forEach(sel=>{const el=document.querySelector(sel);if(el)el.onchange=()=>render();});
  document.querySelectorAll("[data-booking-addon]").forEach(el=>el.onchange=()=>{const id=el.dataset.bookingAddon;ui.modal.addonIds=el.checked?[...ui.modal.addonIds,id]:ui.modal.addonIds.filter(x=>x!==id);render();});
  const dateEl=document.querySelector("[data-booking-date]"); if(dateEl)dateEl.onchange=e=>{ui.modal.date=e.target.value;ui.modal.time=null;render();};
  const staffEl=document.querySelector("[data-booking-staff]"); if(staffEl)staffEl.onchange=e=>{ui.modal.staffId=e.target.value;ui.modal.time=null;render();};
  const walletEl=document.querySelector("[data-booking-wallet]"); if(walletEl)walletEl.onchange=e=>{ui.modal.useWallet=e.target.checked;};
  const providerEl=document.querySelector("[data-deposit-provider]"); if(providerEl)providerEl.onchange=e=>{const method=(state.paymentMethods||[]).find(m=>m.provider===e.target.value);const box=document.querySelector("[data-provider-instructions]");if(box&&method)box.textContent=method.instructions||"Send the exact amount, then submit the reference.";};
  const logoInput = document.querySelector("[data-logo-input]");
  if (logoInput) logoInput.onchange = async (event) => {
    const file = event.target.files?.[0]; if (!file) return;
    if (apiModeEnabled()) {
      if (!apiOwnerSessionActive()) { toast("Owner verification is required before uploading a logo.", "error"); return; }
      ui.apiBusy = true;
      render();
      try {
        const compressed = await compressImageFile(file, 512);
        const blob = dataUrlToBlob(compressed);
        if (!blob) throw new Error("The compressed logo could not be prepared.");
        const upload = await window.AyanApi.uploadMedia(blob, "LOGO", apiSalonId());
        const uri = apiAssetUri(upload?.uri);
        if (!uri) throw new Error("The uploaded logo URL was invalid.");
        const saved = await window.AyanApi.ownerUpdateBranding({
          primaryColor: state.salon.themeColor,
          logoUri: uri
        }, apiSalonId());
        applyRemoteSettings(saved);
        state.salon.logoDataUrl = uri;
        ui.apiBusy = false;
        toast("Salon logo uploaded and published across the salon app.");
      } catch (error) {
        ui.apiBusy = false;
        toast(apiErrorText(error, "The salon logo could not be uploaded."), "error");
      }
      render();
      return;
    }
    try {
      state.salon.logoDataUrl = await compressImageFile(file, 512);
      state.audit.push({ id: uid("audit"), action: "Salon logo changed", actor: "Owner", reason: "Owner uploaded a new salon logo", createdAt: new Date().toISOString() });
      saveState(); toast("Salon logo updated across the app"); render();
    } catch (error) { toast(error?.message || "The logo could not be saved.", "error"); }
  };
  // The home-screen icon is prepared on the phone itself, so it works offline
  // and never has to fetch the salon logo back from the server.
  const phoneIconInput = document.querySelector("[data-phone-icon-input]");
  if (phoneIconInput) phoneIconInput.onchange = async (event) => {
    const file = event.target.files?.[0]; if (!file) return;
    try {
      ui.phoneIconDataUrl = await compressImageFile(file, 512);
      toast("Icon ready. Tap Apply to this phone."); render();
    } catch (error) { toast(error?.message || "The icon could not be prepared.", "error"); }
  };
  const themeInput = document.querySelector("[data-salon-theme-color]");
  if (themeInput) themeInput.oninput = (event) => {
    state.salon.themeColor = safeHexColor(event.target.value, DEFAULT_THEME_COLOR);
    saveState();
    applyVisualPreferences();
  };
  const styleInput = document.querySelector("[data-style-photo-input]");
  if (styleInput) styleInput.onchange = async (event) => {
    const file = event.target.files?.[0]; if (!file || !ui.modal) return;
    const modalState = ui.modal;
    try {
      const compressed = await compressImageFile(file, 720);
      if (apiModeEnabled()) {
        if (!apiOwnerSessionActive()) throw new Error("Owner verification is required before uploading a haircut photo.");
        const blob = dataUrlToBlob(compressed);
        if (!blob) throw new Error("The compressed haircut photo could not be prepared.");
        ui.apiBusy = true;
        render();
        const upload = await window.AyanApi.uploadMedia(blob, "HAIRCUT_STYLE", apiSalonId());
        const uri = apiAssetUri(upload?.uri);
        if (!uri) throw new Error("The uploaded haircut photo URL was invalid.");
        modalState.photoDataUrl = uri;
        ui.apiBusy = false;
        toast("Haircut photo uploaded. Save the style to publish its name and price.");
      } else {
        modalState.photoDataUrl = compressed;
      }
      render();
    } catch (error) {
      ui.apiBusy = false;
      toast(apiErrorText(error, "The haircut photo could not be uploaded."), "error");
      render();
    }
  };
}

function apiPhoneInput(selector = "[data-lookup-phone]") {
  const value = document.querySelector(selector)?.value || ui.modal?.phone || "";
  try { return canonicalPakistaniPhone(value); } catch (_) { return ""; }
}

async function finishApiSession(session, context = {}) {
  if (!session || !session.accessToken) throw new Error("The session response was invalid.");
  const role = String(session.role || "").toUpperCase();
  ui.apiBusy = true;
  ui.apiError = "";
  if (role === "CUSTOMER") {
    ui.role = "customer";
    ui.ownerAuthenticated = false;
    await hydrateRemoteCustomer();
  } else if (role === "OWNER") {
    ui.role = "owner";
    ui.ownerAuthenticated = true;
    await hydrateRemoteOwner();
  } else {
    window.AyanApi.clearSession();
    throw new Error("Only the salon owner can open the Owner workspace.");
  }
  ui.apiBusy = false;
  ui.modal = null;
  ui.customerScreen = "home";
  ui.ownerScreen = "dashboard";
  toast(role === "CUSTOMER" ? `Signed in as ${customerName(state.currentCustomerId)}` : "Owner workspace unlocked");
  render();
  if (context.returnToBooking && role === "CUSTOMER") openBooking({ customerId: state.currentCustomerId, source: "Direct" });
}




/**
 * Owner sign-in with the owner password alone. SMS codes are switched off, so
 * this is the only owner path and it works on a laptop that has no SMS account
 * connected at all.
 */
async function submitOwnerPassword() {
  const modalState = ui.modal || { type: "owner-lookup" };
  // The mobile number is optional: with the long owner password the owner can
  // sign in on any phone without first registering that number.
  const typedPhone = String(document.querySelector("[data-owner-phone]")?.value || modalState.phone || "").trim();
  let phone = "";
  if (typedPhone) {
    try { phone = canonicalPakistaniPhone(typedPhone); } catch (_) { phone = ""; }
  }
  const secret = String(document.querySelector("[data-owner-secret]")?.value || "").trim();
  if (typedPhone && !isValidPakistaniMobile(phone)) {
    modalState.error = "That mobile number looks incomplete. Fix it, or clear the box and sign in with the password alone.";
    modalState.stage = "password";
    ui.modal = modalState;
    render();
    return;
  }
  if (!validSignInPin(secret)) {
    modalState.error = `Enter the owner password. ${SIGN_IN_PASSWORD_RULE}`;
    modalState.stage = "password";
    ui.modal = modalState;
    render();
    return;
  }
  ui.apiBusy = true;
  modalState.error = "";
  modalState.phone = phone;
  render();
  let handled = false;
  try {
    const session = await window.AyanApi.verifyPin(phone, secret, apiSalonId());
    if (String(session?.role || "").toUpperCase() !== "OWNER") {
      window.AyanApi.clearSession();
      throw new Error("That account is not the salon owner.");
    }
    await finishApiSession(session, {});
    handled = true;
    return;
  } catch (error) {
    modalState.error = apiErrorText(error, "That mobile number and password were not accepted.");
    modalState.stage = "password";
  } finally {
    if (!handled) {
      ui.apiBusy = false;
      ui.modal = modalState;
      render();
    }
  }
}


function handleAction(e) {
  const el=e.currentTarget, action=el.dataset.action;
  if (action === "owner-login") { unlockOwner(); return; }
  if (action === "change-owner-code") { if (requireOwnerSession()) { ui.modal = { type: "owner-code" }; render(); } return; }
  if (action === "save-owner-code") { saveOwnerCode(); return; }
  if (action === "owner-logout") { lockOwner(); return; }
  if (action === "customer-logout") {
    if (apiModeEnabled()) {
      (async () => {
        ui.apiBusy = true;
        try { await window.AyanApi.logout(); }
        catch (_) { window.AyanApi.clearSession(); }
        clearRemoteCustomerState();
        ui.apiBusy = false;
        ui.role = "customer";
        ui.ownerAuthenticated = false;
        ui.customerScreen = "home";
        ui.modal = null;
        toast("Signed out");
        render();
      })();
      return;
    }
    state.currentCustomerId = null;
    saveState();
    ui.customerScreen = "home";
    ui.modal = null;
    toast("Customer signed out");
    render();
    return;
  }
  const ownerOnlyActions = new Set(["complete-booking", "open-walkin", "add-customer", "edit-customer", "add-service", "add-staff", "add-addon", "add-haircut-style", "edit-service", "save-service", "edit-staff", "save-staff", "edit-addon", "save-addon", "edit-haircut-style", "save-haircut-style", "toggle-haircut-style", "remove-haircut-style", "remove-haircut-photo", "save-booking-status", "adjust-wallet", "save-wallet-adjustment", "approve-deposit", "reject-deposit", "complete-withdrawal", "reject-withdrawal", "toggle-payment-method", "save-settings", "remove-logo", "export-json", "export-report", "reset-data", "save-server-url", "clear-server-url", "apply-phone-branding"]);
  if (ownerOnlyActions.has(action) && !requireOwnerSession()) return;
  if(action === "close-modal") { if(e.target.closest("[data-modal-content]") && e.target !== el) return; ui.modal=null; render(); return; }
  if (action === "retry-api") { bootstrapApi(); return; }
  if (action === "begin-registration") {
    if (ui.modal) {
      // Carry the number the customer already typed into the registration form.
      const typed = String(document.querySelector("[data-lookup-phone]")?.value || ui.modal.phone || "").trim();
      if (typed) ui.modal.phone = typed;
      ui.modal.stage = "register";
      ui.modal.error = "";
      render();
    }
    return;
  }
  if (action === "back-to-phone") { if (ui.modal) { ui.modal.stage = "phone"; ui.modal.error = ""; render(); } return; }
  if (action === "submit-registration") { submitCustomerRegistration(); return; }
  if (action === "owner-password-login") { submitOwnerPassword(); return; }
  if(action === "open-booking") { openBooking({serviceId:el.dataset.serviceId,customerId:el.dataset.customerId,source:el.dataset.source}); return; }
  if(action === "book-haircut-style") { const style = (state.haircutStyles || []).find((item) => item.id === el.dataset.styleId); if (style?.serviceId && service(style.serviceId)?.active) openBooking({ serviceId: style.serviceId, source: "Style card" }); else toast("Ask the salon owner to link this style to a bookable service.", "error"); return; }
  if(action === "customer-lookup") { ui.modal={type:"lookup",phone:"",searched:false,stage:apiModeEnabled()?"phone":undefined};render();return; }
  if(action === "search-customer") {
    const input=document.querySelector("[data-lookup-phone]");ui.modal.phone=input?.value||"";ui.modal.searched=true;ui.modal.error="";
    // A single exact mobile match moves straight to the password gate; the mobile
    // number alone must never open customer data.
    const normalized = canonicalPakistaniPhone(ui.modal.phone);
    const found = isValidPakistaniMobile(normalized)
      ? (state.customers || []).filter((c) => samePakistaniMobile(c.phone, normalized)) : [];
    if (found.length === 1) ui.modal.stage = customerHasPin(found[0]) ? "pin" : "create-pin";
    render();return;
  }
  if(action === "lookup-restart") { ui.modal={type:"lookup",phone:"",searched:false,stage:undefined,error:""};render();return; }
  if(action === "submit-lookup-pin") { submitOfflineLookupPin(el.dataset.customerId);return; }
  if(action === "submit-api-pin") { submitApiPin();return; }
  if(action === "save-sign-in-pin") { saveSignInPin();return; }
  if(action === "reset-customer-pin") { resetCustomerPinFromOwner(el.dataset.customerId);return; }
  if(action === "set-customer-password") { setCustomerSignInPassword(el.dataset.customerId);return; }
  if(action === "save-server-url") { saveServerAddress();return; }
  if(action === "clear-server-url") { clearServerAddress();return; }
  if(action === "apply-phone-branding") { applyPhoneBranding();return; }
  if(action === "open-style-photo") { ui.modal={type:"style-photo",styleId:el.dataset.styleId};render();return; }
  if(action === "start-customer-signup") {
    const returnToBooking = ui.modal?.type === "customer-required";
    if (apiModeEnabled()) {
      ui.modal={type:"lookup",phone:el.dataset.phone||ui.modal?.phone||"",stage:(el.dataset.phone||ui.modal?.phone)?"register":"phone",returnToBooking,error:""};
    } else {
      ui.modal={type:"customer",signup:true,returnToBooking,phone:el.dataset.phone||ui.modal?.phone||""};
    }
    render();return;
  }
  if(action === "select-customer") {
    const selected = state.customers.find((c) => c.id === el.dataset.customerId);
    const lookupPhone = ui.modal?.type === "lookup" ? ui.modal.phone : "";
    if (!selected || !isValidPakistaniMobile(lookupPhone) || !samePakistaniMobile(selected.phone, lookupPhone)) {
      toast("Please search again with the customer mobile number.", "error"); return;
    }
    state.currentCustomerId=selected.id;saveState();ui.modal=null;ui.role="customer";ui.customerScreen="home";toast(`Signed in as ${customerName(state.currentCustomerId)}`);render();return;
  }
  if(action === "select-booking-service") { ui.modal.serviceId=el.dataset.serviceId;ui.modal.addonIds=[];render(); return; }
  if(action === "select-booking-slot") { ui.modal.time=el.dataset.slot;render(); return; }
  if(action === "confirm-booking") { confirmBooking(); return; }
  if(action === "wallet-topup") { ui.modal={type:"wallet"};render();return; }
  if(action === "withdraw-money") { ui.modal={type:"withdrawal"};render();return; }
  if(action === "submit-withdrawal") { submitWithdrawal();return; }
  if(action === "adjust-wallet") { ui.modal={type:"wallet-adjust",customerId:el.dataset.customerId};render();return; }
  if(action === "save-wallet-adjustment") { saveWalletAdjustment(el.dataset.customerId);return; }
  if(action === "confirm-wallet") { confirmWallet();return; }
  if(action === "approve-deposit") { reviewDeposit(el.dataset.depositId, true);return; }
  if(action === "reject-deposit") { reviewDeposit(el.dataset.depositId, false);return; }
  if(action === "complete-withdrawal") { reviewWithdrawal(el.dataset.withdrawalId, true);return; }
  if(action === "reject-withdrawal") { reviewWithdrawal(el.dataset.withdrawalId, false);return; }
  if(action === "toggle-payment-method") { togglePaymentMethod(el.dataset.provider);return; }
  if(action === "toggle-consent") { const c=customer();setCustomerConsent(c,!c.consent);saveState();toast(c.consent?"Promotional messages enabled":"You have opted out");render();return; }
  if(action === "toggle-dark-mode") { setCustomerDarkMode(!customerDarkMode()); toast(customerDarkMode() ? "Dark mode enabled" : "Light mode enabled"); render(); return; }
  if(action === "share-referral") { const code = referrerCodeFor(customer()); const message = `${state.salon.name ? state.salon.name + ": " : ""}use referral code ${code} for PKR ${state.settings.referralNewCustomerDiscount} off your first paid visit. I earn PKR ${state.settings.referralReferrerReward} salon credit after you complete it.`; if(window.AyanSalonNative?.share) window.AyanSalonNative.share(message); else if(navigator.share) navigator.share({title: state.salon.name, text: message}).catch(() => {}); else navigator.clipboard?.writeText(message); toast(`Referral code ${code} ready to share.`);return; }
  if(action === "claim-referral") { const result = claimReferralForCustomer(customer(), document.querySelector("[data-referral-claim-code]")?.value); toast(result.ok ? "Referral code applied. Reward stays pending until the first paid visit." : result.message, result.ok ? "success" : "error"); render(); return; }
  if(action === "manage-booking") { ui.modal={type:"booking-detail",bookingId:el.dataset.bookingId};render();return; }
  if(action === "complete-booking") { completeBooking(el.dataset.bookingId);return; }
  if(action === "open-walkin") { openBooking({customerId:state.currentCustomerId});return; }
  if(action === "add-customer") { ui.modal={type:"customer"};render();return; }
  if(action === "edit-customer" || action === "edit-my-profile") { ui.modal={type:"customer",customerId:el.dataset.customerId||state.currentCustomerId};render();return; }
  if(action === "save-customer") { saveCustomer(el.dataset.customerId);return; }
  if(action === "add-service") { ui.modal={type:"service"};render();return; }
  if(action === "add-staff") { ui.modal={type:"staff"};render();return; }
  if(action === "add-addon") { ui.modal={type:"addon"};render();return; }
  if(action === "add-haircut-style") { ui.modal={type:"haircut-style"};render();return; }
  if(action === "edit-service") { ui.modal={type:"service",serviceId:el.dataset.serviceId};render();return; }
  if(action === "save-service") { saveService(el.dataset.serviceId);return; }
  if(action === "edit-staff") { ui.modal={type:"staff",staffId:el.dataset.staffId};render();return; }
  if(action === "save-staff") { saveStaff(el.dataset.staffId);return; }
  if(action === "edit-addon") { ui.modal={type:"addon",addonId:el.dataset.addonId};render();return; }
  if(action === "save-addon") { saveAddon(el.dataset.addonId);return; }
  if(action === "edit-haircut-style") { ui.modal={type:"haircut-style",styleId:el.dataset.styleId};render();return; }
  if(action === "save-haircut-style") { saveHaircutStyle(el.dataset.styleId);return; }
  if(action === "remove-haircut-photo") { if (ui.modal) { ui.modal.photoDataUrl = ""; render(); } return; }
  if(action === "toggle-haircut-style") { toggleHaircutStyle(el.dataset.styleId, el.dataset.nextActive === "true");return; }
  if(action === "remove-haircut-style") { removeHaircutStyle(el.dataset.styleId);return; }
  if(action === "save-booking-status") { saveBookingStatus(el.dataset.bookingId);return; }
  if(action === "save-settings") { saveSettings();return; }
  if(action === "remove-logo") {
    if (apiModeEnabled()) {
      runRemoteOwnerMutation(
        () => window.AyanApi.ownerUpdateBranding({ primaryColor: state.salon.themeColor, logoUri: "" }, apiSalonId()),
        "Salon logo removed from the server.",
        "The salon logo could not be removed."
      );
      return;
    }
    state.salon.logoDataUrl = "";
    state.audit.push({ id: uid("audit"), action: "Salon logo changed", actor: "Owner", reason: "Logo removed", createdAt: new Date().toISOString() });
    saveState(); toast("Salon logo removed"); render(); return;
  }
  if(action === "export-json") { downloadFile("ayan-beauty-salon-data.json", JSON.stringify(state,null,2), "application/json");toast("Data export ready");return; }
  if(action === "export-report") { const day = iso(today); const todayVisits = state.visits.filter(v => v.date === day); const missed = state.bookings.filter(b => b.date === day && ["Cancelled", "No-show"].includes(b.status)); const newCustomers = todayVisits.filter(v => state.visits.filter(item => item.customerId === v.customerId && item.completedAt < v.completedAt).length === 0).length; const rows=[["date","completed_revenue","completed_visits","missed_bookings","new_customers","repeat_customers","addon_revenue","reminder_bookings","tomorrow_bookings"],[day,todayVisits.reduce((sum,v)=>sum+Number(v.amount||0),0),todayVisits.length,missed.length,newCustomers,todayVisits.length-newCustomers,todayVisits.reduce((sum,v)=>sum+addonRevenueForRecord(v),0),state.bookings.filter(b=>b.date===day&&b.source==="Reminder").length,state.bookings.filter(b=>b.date===addDays(day,1)&&["Pending","Confirmed"].includes(b.status)).length]];downloadFile("ayan-beauty-salon-daily-summary.csv",rows.map(r=>r.join(",")).join("\n"),"text/csv");toast("Summary exported");return; }
  if(action === "reset-data") { if(confirm(RELEASE_MODE ? "Reset this salon workspace? This cannot be undone on this device." : "Reset all local test data?")) resetData(); return; }
  if(action === "clear-search") {ui.search="";render();return;}
}
async function confirmApiBooking() {
  const m = ui.modal || {};
  const cust = state.customers.find((c) => c.id === m.customerId) || customer();
  const selectedService = service(m.serviceId);
  const selectedStaff = staff(m.staffId);
  if (!cust?.id || !selectedService?.id || !selectedStaff?.id || !m.date || !m.time) {
    toast("Choose a service, barber and available slot first.", "error");
    return;
  }
  const selectedAddons = (m.addonIds || []).filter((id) => addon(id)?.active && addon(id).serviceIds.includes(selectedService.id));
  const startsAt = new Date(`${m.date}T${m.time}:00`);
  if (Number.isNaN(startsAt.getTime()) || startsAt <= new Date()) {
    toast("Choose a future date and time.", "error");
    return;
  }
  ui.apiBusy = true;
  ui.apiError = "";
  render();
  try {
    await window.AyanApi.createBooking({
      customerId: cust.id,
      serviceId: selectedService.id,
      staffId: selectedStaff.id,
      startsAt: startsAt.toISOString(),
      paymentMethod: m.useWallet ? "WALLET" : "CASH",
      addOnIds: selectedAddons.map((item) => item.id),
      idempotencyKey: window.AyanApi.makeKey("booking")
    }, apiSalonId());
    await hydrateRemoteCustomer();
    ui.apiBusy = false;
    ui.modal = null;
    toast(`Booking confirmed for ${dateLabel(m.date)} at ${m.time}`);
    render();
  } catch (error) {
    ui.apiBusy = false;
    if (apiSessionExpired(error)) {
      ui.modal = null;
      toast("Your session expired. Verify your mobile number again.", "error");
    } else {
      if (error?.status === 409) ui.modal.error = "That slot was just taken or the wallet is already committed. Choose another slot.";
      else ui.modal.error = apiErrorText(error, "The booking could not be confirmed.");
    }
    render();
  }
}

function confirmBooking() {
  if (apiModeEnabled()) { confirmApiBooking(); return; }
  const m = ui.modal; if (!m.time) { toast("Choose an available slot first", "error"); return; }
  const cust = state.customers.find((c) => c.id === m.customerId) || customer(); const s = service(m.serviceId);
  if (!cust || !s || !s.active) { toast("Choose an active customer and service.", "error"); return; }
  const selectedStaff = staff(m.staffId);
  const selectedAddons = (m.addonIds || []).filter((id) => addon(id)?.active && addon(id).serviceIds.includes(s.id) && addonEligibleForStaff(addon(id), selectedStaff));
  const subtotal = s.price + selectedAddons.reduce((sum, id) => sum + (addon(id)?.price || 0), 0);
  const referral = openReferralFor(cust); const configuredReferralDiscount = referral ? Number(referral.newCustomerDiscount ?? state.settings.referralNewCustomerDiscount ?? 0) : 0;
  const referralDiscount = Math.min(subtotal, Math.max(0, Number.isFinite(configuredReferralDiscount) ? configuredReferralDiscount : 0));
  const total = subtotal - referralDiscount;
  if (total <= 0) { toast("Referral discount must leave a positive paid total.", "error"); return; }
  const duration = s.duration + selectedAddons.reduce((sum, id) => sum + (addon(id)?.duration || 0), 0);
  if (!staffCanPerform(selectedStaff, s)) { toast("That team member is not assigned to this service.", "error"); return; }
  if (slotBusy(m.date, m.staffId, m.time, duration)) { toast("That slot is unavailable for the full service duration.", "error"); return; }
  if (m.useWallet && walletExpired(cust)) { toast("This wallet credit has expired.", "error"); return; }
  if (m.useWallet && availableWallet(cust) < total) { toast("Unreserved wallet balance is not enough for this total.", "error"); return; }
  const now = new Date().toISOString(); const b = {
    id: uid("bk"), customerId: cust.id, serviceId: s.id, addonIds: selectedAddons, staffId: m.staffId,
    addonSnapshots: addonSnapshot(selectedAddons),
    date: m.date, time: m.time, status: "Confirmed", total, duration, referralDiscount,
    referralId: referral?.id || null, referralRewardSnapshot: referral?.referrerReward ?? null,
    referralDiscountSnapshot: referral?.newCustomerDiscount ?? null,
    source: m.source || (m.customerId === state.currentCustomerId ? "Direct" : "Walk-in"),
    paymentMethod: m.useWallet ? "Salon credit" : "Pay at salon", paymentStatus: m.useWallet ? "Pending wallet charge" : "Pending", createdAt: now
  };
  state.bookings.push(b);
  if (referral) referral.firstEligibleBookingId = b.id;
  state.reminders.filter((r) => r.customerId === cust.id && r.serviceId === s.id && !r.bookingId && ["Scheduled", "Sent"].includes(r.status)).forEach((r) => { r.bookingId = b.id; r.status = "Converted"; });
  state.audit.push({ id: uid("audit"), action: "Booking confirmed", actor: m.customerId === state.currentCustomerId ? cust.id : "Owner", reason: `${s.name} on ${b.date} at ${b.time}`, createdAt: now });
  saveState(); ui.modal = null; toast(`Booking confirmed for ${dateLabel(b.date)} at ${b.time}`); render();
}
async function confirmApiWallet() {
  const provider = document.querySelector("[data-deposit-provider]")?.value || "";
  const reference = document.querySelector("[data-deposit-reference]")?.value.trim() || "";
  const proof = document.querySelector("[data-deposit-proof]")?.value.trim() || "";
  const amount = Number(document.querySelector("[data-deposit-amount]")?.value);
  if (!Number.isSafeInteger(amount) || amount <= 0) { ui.modal.error = "Enter a positive whole-PKR deposit amount."; render(); return; }
  if (!provider) { ui.modal.error = "Choose an enabled payment method."; render(); return; }
  if (!reference) { ui.modal.error = "Enter the provider transaction/reference ID so the owner can verify it."; render(); return; }
  ui.apiBusy = true;
  ui.modal.error = "";
  render();
  try {
    await window.AyanApi.submitDeposit({
      amountMinor: amount * 100,
      providerCode: provider,
      providerReference: normalizeProviderReference(reference),
      proofUri: proof || null,
      idempotencyKey: window.AyanApi.makeKey("deposit")
    }, apiSalonId());
    await hydrateRemoteCustomer();
    ui.apiBusy = false;
    ui.modal = null;
    toast("Deposit submitted. Wallet credit is pending owner verification.");
    render();
  } catch (error) {
    ui.apiBusy = false;
    if (apiSessionExpired(error)) {
      ui.modal = null;
      toast("Your session expired. Verify your mobile number again.", "error");
    } else {
      ui.modal.error = error?.status === 409 ? "That payment reference has already been submitted." : apiErrorText(error, "The deposit could not be submitted.");
    }
    render();
  }
}

function confirmWallet() {
  if (apiModeEnabled()) { confirmApiWallet(); return; }
  const provider = document.querySelector("[data-deposit-provider]")?.value;
  if (document.querySelector("[data-deposit-state]")?.value === "failed") { ui.modal = null; toast("Deposit submission failed. No wallet balance was changed.", "error"); render(); return; }
  const reference = document.querySelector("[data-deposit-reference]")?.value.trim() || "";
  const proof = document.querySelector("[data-deposit-proof]")?.value.trim() || "";
  if (!reference) { toast("Enter the provider transaction/reference ID so the owner can verify it.", "error"); return; }
  const method = (state.paymentMethods || []).find(m => m.provider === provider && m.enabled);
  if (!method) { toast("Choose an enabled payment method.", "error"); return; }
  const c = customer(); const amount = Number(state.settings.walletTopUp); const now = new Date().toISOString();
  if (!Number.isSafeInteger(amount) || amount <= 0) { toast("The owner has not configured a valid top-up amount.", "error"); return; }
  const providerKey = normalizeProviderReference(provider);
  const referenceKey = normalizeProviderReference(reference);
  const duplicate = state.deposits.find((d) => referenceKey && normalizeProviderReference(d.provider) === providerKey && normalizeProviderReference(d.reference) === referenceKey);
  if (duplicate) { ui.modal = null; toast("That provider reference has already been submitted for this salon.", "error"); render(); return; }
  state.deposits.push({ id: uid("dep"), customerId: c.id, amount, provider: method.provider, reference: referenceKey, proof, bonusAmount: Number(state.settings.walletBonus || 0), walletExpiryDays: Number(state.settings.walletExpiryDays || 0), status: "Pending verification", submittedAt: now, reviewedAt: null, reviewedBy: null, reason: "Customer-submitted manual deposit" });
  state.audit.push({ id: uid("audit"), action: "Deposit submitted", actor: c.id, reason: `${provider} manual deposit claim`, createdAt: now });
  saveState(); ui.modal = null; toast("Deposit submitted for owner verification. Wallet credit is still pending."); render();
}

function submitWithdrawal() {
  const c = customer(); const amount = Number(document.querySelector("[data-withdrawal-amount]")?.value); const provider = document.querySelector("[data-withdrawal-provider]")?.value; const destinationInput = document.querySelector("[data-withdrawal-destination]")?.value.trim(); const minimum = Number(state.settings.withdrawalMinimum || 100); const max = withdrawableCash(c);
  if (!Number.isFinite(amount) || amount <= 0 || amount < minimum || amount > max) { toast(`Enter an amount between ${money(Math.max(1, minimum))} and ${money(max)}.`, "error"); return; }
  const method = (state.paymentMethods || []).find((m) => m.provider === provider && m.enabled);
  if (!provider || !method || !destinationInput || normalizePhone(destinationInput).length < 7) { toast("Choose an enabled payout method and enter a valid account/mobile.", "error"); return; }
  const dailyReserved = state.withdrawals.filter(w => w.customerId === c.id && ["Pending", "Approved", "Completed"].includes(w.status) && w.requestedAt.slice(0,10) === iso(today)).reduce((sum, w) => sum + Number(w.amount || 0), 0);
  const dailyLimit = Number(state.settings.withdrawalDailyLimit || 10000);
  if (dailyLimit > 0 && dailyReserved + amount > dailyLimit) { toast(`Daily withdrawal limit is ${money(dailyLimit)}.`, "error"); return; }
  const now = new Date().toISOString(); const destination = maskPayoutDestination(destinationInput); state.withdrawals.push({ id: uid("wd"), customerId: c.id, amount, provider, destination, destinationLast4: normalizePhone(destinationInput).slice(-4), status: "Pending", requestedAt: now, updatedAt: now, reason: "Customer withdrawal request", reservedAmount: amount }); state.audit.push({ id: uid("audit"), action: "Withdrawal requested", actor: c.id, reason: `${money(amount)} via ${provider} to ${destination}`, createdAt: now }); saveState(); ui.modal = null; toast(`${money(amount)} reserved. Owner review is required.`); render();
}

async function runRemoteOwnerMutation(task, successMessage, fallbackMessage = "The owner action could not be completed.") {
  if (!apiModeEnabled() || !apiOwnerSessionActive()) return false;
  ui.apiBusy = true;
  render();
  try {
    await task();
    await hydrateRemoteOwner();
    ui.apiBusy = false;
    ui.modal = null;
    toast(successMessage);
    render();
  } catch (error) {
    ui.apiBusy = false;
    if (apiSessionExpired(error)) {
      ui.modal = null;
      toast("Your owner session expired. Verify the owner mobile again.", "error");
    } else {
      if (ui.modal) ui.modal.error = apiErrorText(error, fallbackMessage);
      else toast(apiErrorText(error, fallbackMessage), "error");
    }
    render();
  }
  return true;
}

function reviewDeposit(id, approve) {
  if (apiModeEnabled()) {
    if (!approve) {
      const reason = window.prompt("Reason for rejecting this deposit:", "Could not verify provider payment")?.trim();
      if (!reason) return;
      runRemoteOwnerMutation(
        () => window.AyanApi.ownerReviewDeposit(id, false, reason, apiSalonId()),
        "Deposit rejected. No wallet credit was created.",
        "The deposit could not be rejected."
      );
    } else {
      runRemoteOwnerMutation(
        () => window.AyanApi.ownerReviewDeposit(id, true, "", apiSalonId()),
        "Deposit approved and wallet ledger refreshed.",
        "The deposit could not be approved."
      );
    }
    return;
  }
  const deposit = state.deposits.find(d => d.id === id); if (!deposit) return;
  if (deposit.status !== "Pending verification") { toast("This deposit has already been reviewed.", "error"); return; }
  const now = new Date().toISOString(); const c = state.customers.find(x => x.id === deposit.customerId); if (!c) { toast("Customer record not found.", "error"); return; }
  if (!approve) { const reason = window.prompt("Reason for rejecting this deposit:", "Could not verify provider payment")?.trim(); if (!reason) return; deposit.status = "Rejected"; deposit.reason = reason; deposit.reviewedAt = now; deposit.reviewedBy = "Owner"; state.audit.push({ id: uid("audit"), action: "Deposit rejected", actor: "Owner", reason, createdAt: now }); saveState(); toast("Deposit rejected. No wallet credit was created.", "error"); render(); return; }
  const paid = Number(deposit.amount);
  const configuredBonus = Number.isFinite(Number(deposit.bonusAmount)) ? Number(deposit.bonusAmount) : Number(state.settings.walletBonus || 0);
  const bonus = c.firstDepositBonusClaimed === true ? 0 : Math.max(0, configuredBonus);
  const expiryDays = Number.isFinite(Number(deposit.walletExpiryDays)) ? Number(deposit.walletExpiryDays) : Number(state.settings.walletExpiryDays || 0);
  if (!Number.isSafeInteger(paid) || paid <= 0 || !Number.isSafeInteger(bonus) || bonus < 0 || !Number.isSafeInteger(expiryDays) || expiryDays < 0) { toast("This deposit has invalid money settings and cannot be approved.", "error"); return; }
  if (state.walletTransactions.some((t) => t.reference === deposit.id)) { toast("This deposit already has wallet entries; no duplicate credit was created.", "error"); return; }
  c.paidCredit = Number(c.paidCredit || 0) + paid; c.bonusCredit = Number(c.bonusCredit || 0) + bonus; c.creditExpiresAt = addDays(iso(today), expiryDays); c.walletExpiryRecorded = false; if (bonus > 0) c.firstDepositBonusClaimed = true; deposit.bonusAmountApplied = bonus; deposit.firstDepositBonus = bonus > 0; deposit.status = "Approved"; deposit.reason = "Provider account verified by owner"; deposit.reviewedAt = now; deposit.reviewedBy = "Owner";
  state.walletTransactions.push({ id: uid("wt"), customerId: c.id, type: "Paid credit", paidCredit: paid, bonusCredit: 0, debit: 0, amount: paid, reason: `Deposit ${deposit.provider} approved`, status: "Credited", createdAt: now, expiresAt: c.creditExpiresAt, reference: deposit.id, actor: "Owner" }, { id: uid("wt"), customerId: c.id, type: "Bonus credit", paidCredit: 0, bonusCredit: bonus, debit: 0, amount: bonus, reason: bonus > 0 ? `First deposit bonus for ${deposit.provider}` : `Deposit bonus already claimed`, status: bonus > 0 ? "Credited" : "Skipped", createdAt: now, expiresAt: c.creditExpiresAt, reference: deposit.id, actor: "Owner" });
  state.audit.push({ id: uid("audit"), action: "Deposit approved", actor: "Owner", reason: `Verified ${deposit.provider} reference ${deposit.reference || "(none)"}; first-time bonus ${bonus > 0 ? "released" : "not eligible"}`, createdAt: now }); saveState(); toast(`Deposit approved. ${money(paid)} cash${bonus > 0 ? ` and ${money(bonus)} first-time bonus` : ""} credited.`); render();
}

function reviewWithdrawal(id, complete) {
  if (apiModeEnabled()) {
    const remoteWithdrawal = (state.withdrawals || []).find((item) => item.id === id);
    const action = complete ? (remoteWithdrawal?.status === "Pending" ? "approve" : "complete") : "reject";
    const reason = !complete ? window.prompt("Reason for rejecting this withdrawal:", "Payout details could not be verified")?.trim() : "";
    if (!complete && !reason) return;
    runRemoteOwnerMutation(
      () => window.AyanApi.ownerReviewWithdrawal(id, action, reason, apiSalonId()),
      complete ? (action === "approve" ? "Withdrawal approved. Verify the external payout, then mark it paid." : "Withdrawal marked paid.") : "Withdrawal rejected and reserved cash released.",
      "The withdrawal action could not be completed."
    );
    return;
  }
  const withdrawal = state.withdrawals.find(w => w.id === id); if (!withdrawal) return;
  if (!["Pending", "Approved"].includes(withdrawal.status)) { toast("This withdrawal has already been resolved.", "error"); return; }
  const c = state.customers.find(x => x.id === withdrawal.customerId); if (!c) { toast("Customer record not found.", "error"); return; }
  const now = new Date().toISOString();
  if (!complete) { const reason = window.prompt("Reason for rejecting this withdrawal:", "Payout details could not be verified")?.trim(); if (!reason) return; withdrawal.status = "Rejected"; withdrawal.reason = reason; withdrawal.updatedAt = now; withdrawal.reservedAmount = 0; state.audit.push({ id: uid("audit"), action: "Withdrawal rejected", actor: "Owner", reason, createdAt: now }); saveState(); toast("Withdrawal rejected. Reserved cash is available again.", "error"); render(); return; }
  if (withdrawal.status === "Pending") { withdrawal.status = "Approved"; withdrawal.reason = "Owner approved for manual payout"; withdrawal.updatedAt = now; state.audit.push({ id: uid("audit"), action: "Withdrawal approved", actor: "Owner", reason: `${money(withdrawal.amount)} via ${withdrawal.provider}`, createdAt: now }); saveState(); toast("Withdrawal approved. Verify the external payout, then mark it paid."); render(); return; }
  const amount = Number(withdrawal.amount); const otherReservations = reservedCash(c) - Number(withdrawal.reservedAmount || withdrawal.amount || 0); const availableForThisPayout = Math.max(0, Number(c.paidCredit || 0) - Math.max(0, otherReservations)); if (availableForThisPayout < amount) { toast("Cash is no longer available for this payout.", "error"); return; }
  c.paidCredit = Number(c.paidCredit || 0) - amount; withdrawal.status = "Completed"; withdrawal.reason = "Owner marked payout sent after verification"; withdrawal.updatedAt = now; withdrawal.reservedAmount = 0; state.walletTransactions.push({ id: uid("wt"), customerId: c.id, type: "Withdrawal", paidCredit: -amount, bonusCredit: 0, debit: amount, amount: -amount, reason: `Withdrawal via ${withdrawal.provider}`, status: "Completed", createdAt: now, reference: withdrawal.id }); state.audit.push({ id: uid("audit"), action: "Withdrawal completed", actor: "Owner", reason: `${money(amount)} sent via ${withdrawal.provider}`, createdAt: now }); saveState(); toast("Withdrawal marked paid after owner verification."); render();
}

function togglePaymentMethod(provider) {
  const method = (state.paymentMethods || []).find(m => m.provider === provider); if (!method) return;
  if (apiModeEnabled()) {
    const enabled = !method.enabled;
    runRemoteOwnerMutation(
      () => window.AyanApi.ownerUpdatePaymentMethod(provider, {
        providerCode: provider, displayName: method.displayName || provider,
        accountTitle: method.accountTitle || null, accountReference: method.accountNumber || null, accountToken: null,
        instructions: method.instructions || null, enabled, sortOrder: Number(method.sortOrder || 0), mode: method.mode || "MANUAL"
      }, apiSalonId()),
      `${provider} ${enabled ? "enabled" : "disabled"} for manual deposits.`,
      "The payment method could not be updated."
    );
    return;
  }
  method.enabled = !method.enabled; state.audit.push({ id: uid("audit"), action: "Payment method changed", actor: "Owner", reason: `${provider} ${method.enabled ? "enabled" : "disabled"}`, createdAt: new Date().toISOString() }); saveState(); toast(`${provider} ${method.enabled ? "enabled" : "disabled"} for manual deposits.`); render();
}
function saveWalletAdjustment(customerId) {
  const c = state.customers.find((x) => x.id === customerId); const type = document.querySelector("[data-adjust-type]")?.value; const requestedBucket = document.querySelector("[data-adjust-bucket]")?.value || "paidCredit"; const amount = Number(document.querySelector("[data-adjust-amount]")?.value); const reason = document.querySelector("[data-adjust-reason]")?.value.trim(); const referenceId = document.querySelector("[data-adjust-reference]")?.value || "";
  if (!c || !type || !Number.isFinite(amount) || amount <= 0 || !reason) { toast("Choose an amount and enter a reason.", "error"); return; }
  const source = referenceId ? state.walletTransactions.find((t) => t.id === referenceId && t.customerId === customerId) : null;
  if (["Refund", "Reversal"].includes(type) && !source) { toast("Choose the original wallet entry for a refund or reversal.", "error"); return; }
  if (type === "Reversal" && source.type === "Reversal") { toast("A reversal cannot reverse another reversal.", "error"); return; }
  const sourceBucket = source ? (Number(source.paidCredit || 0) !== 0 ? "paidCredit" : "bonusCredit") : requestedBucket;
  const sourceSigned = source ? Number(source[sourceBucket] || source.amount || 0) : 0;
  const previousReversed = source ? state.walletTransactions.filter((t) => t.reversalOf === source.id).reduce((sum, t) => sum + Math.abs(Number(t.amount || 0)), 0) : 0;
  if (type === "Reversal" && previousReversed + amount > Math.abs(sourceSigned)) { toast("The reversal amount cannot exceed the original wallet entry.", "error"); return; }
  const bucket = type === "Reversal" ? sourceBucket : requestedBucket;
  const signed = type === "Reversal" ? (sourceSigned < 0 ? amount : -amount) : type === "Debit" ? -amount : amount;
  const currentBucketBalance = bucket === "paidCredit" ? Number(c.paidCredit || 0) : Number(c.bonusCredit || 0);
  if (signed < 0 && currentBucketBalance < amount) { toast(`That ${bucket === "paidCredit" ? "cash" : "promotional"} balance does not have enough credit.`, "error"); return; }
  if (signed < 0 && bucket === "paidCredit" && withdrawableCash(c) < amount) { toast("That cash is partly reserved or unavailable.", "error"); return; }
  if (bucket === "paidCredit") c.paidCredit = Math.max(0, Number(c.paidCredit || 0) + signed); else c.bonusCredit = Math.max(0, Number(c.bonusCredit || 0) + signed);
  if (signed > 0) { c.creditExpiresAt = addDays(iso(today), Number(state.settings.walletExpiryDays)); c.walletExpiryRecorded = false; }
  state.walletTransactions.push({ id: uid("wt"), customerId, type, paidCredit: bucket === "paidCredit" ? signed : 0, bonusCredit: bucket === "bonusCredit" ? signed : 0, debit: signed < 0 ? amount : 0, amount: signed, reason, status: type === "Debit" ? "Debited" : type === "Refund" ? "Refunded" : type === "Reversal" ? "Reversed" : "Credited", createdAt: new Date().toISOString(), actor: "Owner", reversalOf: referenceId || null });
  state.audit.push({ id: uid("audit"), action: `Wallet ${type.toLowerCase()}`, actor: "Owner", reason, createdAt: new Date().toISOString() });
  saveState(); ui.modal = null; toast(`Wallet ${type.toLowerCase()} recorded with an audit reason.`); render();
}
function completeBooking(id) {
  if (apiModeEnabled()) {
    runRemoteOwnerMutation(
      () => window.AyanApi.ownerCompleteBooking(id, apiSalonId()),
      "Visit completed. History and next reminder refreshed.",
      "The visit could not be completed. Confirm payment and try again."
    );
    return;
  }
  const b = state.bookings.find((x) => x.id === id);
  if (!b || !["Pending", "Confirmed"].includes(b.status) || state.visits.some((v) => v.bookingId === id)) { toast("Only a pending or confirmed booking can be completed.", "error"); return; }
  const s = service(b.serviceId); const c = state.customers.find((x) => x.id === b.customerId);
  if (!s || !c) { toast("The booking record is incomplete.", "error"); return; }
  const walletPayment = b.paymentMethod === "Salon credit";
  if (!walletPayment && !bookingPaymentConfirmed(b)) { toast("Confirm the customer's payment before completing this visit.", "error"); return; }
  const completedAt = new Date().toISOString();
  if (walletPayment) {
    if (walletExpired(c)) { toast("This wallet credit has expired. Collect another payment before completing the visit.", "error"); return; }
    if (availableWallet(c, b.id) < b.total) { toast("Unreserved wallet balance is no longer enough. Resolve the pending withdrawal or collect another payment.", "error"); return; }
    const bonusDebit = Math.min(Number(c.bonusCredit || 0), b.total); const paidDebit = b.total - bonusDebit;
    c.bonusCredit = Number(c.bonusCredit || 0) - bonusDebit; c.paidCredit = Number(c.paidCredit || 0) - paidDebit;
    state.walletTransactions.push({ id: uid("wt"), customerId: c.id, type: "Service payment", paidCredit: -paidDebit, bonusCredit: -bonusDebit, debit: b.total, amount: -b.total, reason: `Visit ${b.id}`, status: "Debited", createdAt: completedAt, reference: b.id });
  }
  b.paymentStatus = walletPayment ? "Wallet paid" : "Paid";
  b.status = "Completed";
  const completedDate = completedAt.slice(0, 10);
  const visit = { id: uid("visit"), bookingId: b.id, customerId: c.id, serviceId: s.id, staffId: b.staffId, date: completedDate, amount: b.total, paymentStatus: b.paymentStatus, addonIds: b.addonIds, addonSnapshots: b.addonSnapshots || addonSnapshot(b.addonIds), addonRevenue: addonRevenueForRecord(b), completedAt };
  state.visits.push(visit); c.lastVisit = completedDate;
  const next = addDays(completedDate, s.repeatDays || state.settings.reminderDefaultDays);
  state.reminders.filter((r) => r.customerId === c.id && r.serviceId === s.id && !r.bookingId && ["Scheduled", "Sent"].includes(r.status)).forEach((r) => { r.status = "Superseded"; r.supersededAt = completedAt; });
  state.reminders.push({ id: uid("rem"), customerId: c.id, serviceId: s.id, scheduledDate: next, status: c.consent ? "Scheduled" : "Opted out", bookingId: null, sourceVisitId: visit.id, optOut: !c.consent });
  const ref = (b.referralId && state.referrals.find((r) => r.id === b.referralId)) || openReferralFor(c);
  const priorPaidVisit = state.visits.some((v) => v.customerId === c.id && v.id !== visit.id && Number(v.amount || 0) > 0);
  const reward = ref ? Number(b.referralRewardSnapshot ?? ref.referrerReward ?? state.settings.referralReferrerReward ?? 0) : 0;
  const rewardMonth = monthKey(completedAt); const releasedThisMonth = ref ? state.referrals.filter((item) => item.referrerId === ref.referrerId && item.status === "Rewarded" && monthKey(item.rewardedAt) === rewardMonth).length : 0;
  const referrer = ref ? state.customers.find((x) => x.id === ref.referrerId) : null;
  const selfReferral = !!(ref && referrer && (referrer.id === c.id || samePakistaniMobile(referrer.phone, c.phone)));
  if (ref && ref.firstEligibleBookingId === b.id && ["Registered", "Pending visit"].includes(ref.status) && !selfReferral && !priorPaidVisit && bookingPaymentConfirmed(b) && b.total > 0 && reward > 0 && (!state.settings.monthlyReferralLimit || releasedThisMonth < Number(state.settings.monthlyReferralLimit))) {
    if (referrer && !state.walletTransactions.some((t) => t.type === "Referral reward" && t.reference === ref.id)) {
      ref.status = "Rewarded"; ref.completedVisitId = visit.id; ref.rewardedAt = completedAt; referrer.bonusCredit += reward;
      state.walletTransactions.push({ id: uid("wt"), customerId: referrer.id, type: "Referral reward", paidCredit: 0, bonusCredit: reward, debit: 0, amount: reward, reason: `Referral ${ref.code} rewarded`, status: "Credited", createdAt: completedAt, reference: ref.id });
    }
  } else if (ref && selfReferral) {
    ref.status = "Rejected"; ref.rejectionReason = "Self-referral identity match";
  }
  state.audit.push({ id: uid("audit"), action: "Visit completed", actor: "Owner", reason: `${s.name} for ${c.name}`, createdAt: completedAt });
  saveState(); toast(`Visit completed. Next reminder set for ${dateLabel(next)}.`); render();
}
async function saveApiService(id, value) {
  ui.apiBusy = true; render();
  try {
    const path = `/api/salons/${encodeURIComponent(apiSalonId())}/services${id ? `/${encodeURIComponent(id)}` : ""}`;
    await window.AyanApi.request(path, { method: id ? "PUT" : "POST", body: JSON.stringify(value) });
    await hydrateRemoteOwner();
    ui.apiBusy = false; ui.modal = null; toast("Service saved on the salon server."); render();
  } catch (error) {
    ui.apiBusy = false;
    if (apiSessionExpired(error)) ui.modal = null;
    else ui.modal.error = apiErrorText(error, "The service could not be saved.");
    render();
  }
}

function saveService(id) {
  const name = document.querySelector("[data-service-name]")?.value.trim();
  const category = document.querySelector("[data-service-category]")?.value.trim() || "General";
  const price = Number(document.querySelector("[data-service-price]")?.value);
  const duration = Number(document.querySelector("[data-service-duration]")?.value);
  const repeatDays = Number(document.querySelector("[data-service-repeat]")?.value);
  const active = !!document.querySelector("[data-service-active]")?.checked;
  if (!name || !Number.isSafeInteger(price) || price < 0 || !Number.isSafeInteger(duration) || duration <= 0 || !Number.isSafeInteger(repeatDays) || repeatDays <= 0) { toast("Enter a name, whole-PKR price and positive timings.", "error"); return; }
  if (apiModeEnabled()) {
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(String(id || "")) && id) { toast("This service cannot be edited until it has a server ID.", "error"); return; }
    saveApiService(id, { name, category, priceMinor: price * 100, durationMinutes: duration, active });
    return;
  }
  if (id) Object.assign(state.services.find((x) => x.id === id), { name, category, price, duration, repeatDays, active });
  else state.services.push({ id: uid("svc"), name, category, price, duration, repeatDays, active });
  state.audit.push({ id: uid("audit"), action: "Service changed", actor: "Owner", reason: `${name} · ${money(price)}`, createdAt: new Date().toISOString() }); saveState(); ui.modal = null; toast("Service saved"); render();
}
async function saveApiStaff(id, value) {
  ui.apiBusy = true; render();
  try {
    const path = `/api/salons/${encodeURIComponent(apiSalonId())}/staff${id ? `/${encodeURIComponent(id)}` : ""}`;
    await window.AyanApi.request(path, { method: id ? "PUT" : "POST", body: JSON.stringify(value) });
    await hydrateRemoteOwner();
    ui.apiBusy = false; ui.modal = null; toast("Staff member saved on the salon server."); render();
  } catch (error) {
    ui.apiBusy = false;
    if (apiSessionExpired(error)) ui.modal = null;
    else ui.modal.error = apiErrorText(error, "The staff member could not be saved.");
    render();
  }
}

function saveStaff(id) {
  const name = document.querySelector("[data-staff-name]")?.value.trim();
  const role = document.querySelector("[data-staff-role]")?.value.trim();
  const hours = document.querySelector("[data-staff-hours]")?.value.trim();
  const skills = (document.querySelector("[data-staff-skills]")?.value || "").split(",").map((x) => x.trim()).filter(Boolean);
  const active = !!document.querySelector("[data-staff-active]")?.checked;
  if (!name || (!apiModeEnabled() && (!hours || !parseStaffHours(hours)))) { toast("Enter working hours with an end time after the start, such as 10:00 - 20:00.", "error"); return; }
  if (apiModeEnabled()) {
    const roleMap = { owner: "OWNER", manager: "MANAGER", barber: "BARBER", staff: "STAFF", receptionist: "RECEPTIONIST" };
    const apiRole = roleMap[String(role || "staff").toLowerCase()] || "STAFF";
    if (apiRole === "OWNER") { toast("Owner identity is managed by phone authentication.", "error"); return; }
    if (id && !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(String(id))) { toast("This staff record cannot be edited until it has a server ID.", "error"); return; }
    saveApiStaff(id, { name, phone: null, role: apiRole, active, permissionsJson: "[]" });
    return;
  }
  if (id) Object.assign(state.staff.find((x) => x.id === id), { name, role, hours, skills, active });
  else state.staff.push({ id: uid("st"), name, role, hours, skills, active });
  state.audit.push({ id: uid("audit"), action: "Staff changed", actor: "Owner", reason: `${name} · ${role}`, createdAt: new Date().toISOString() });
  saveState(); ui.modal = null; toast("Staff profile saved"); render();
}
function saveBookingStatus(id) {
  const b = state.bookings.find((x) => x.id === id); const status = document.querySelector("[data-detail-status]")?.value; const payment = document.querySelector("[data-detail-payment]")?.value; if (!b || !status) return;
  if (apiModeEnabled()) {
    if (status === "Completed") {
      completeBooking(id);
      return;
    }
    if (!["Confirmed", "Cancelled", "No-show"].includes(status)) { toast("Choose a valid booking status.", "error"); return; }
    const reason = ["Cancelled", "No-show"].includes(status)
      ? (document.querySelector("[data-detail-reason]")?.value.trim() || window.prompt(`Reason for ${status.toLowerCase()}:`, "Customer request")?.trim() || "")
      : "";
    if (["Cancelled", "No-show"].includes(status) && !reason) { toast("A reason is required for this status.", "error"); return; }
    runRemoteOwnerMutation(
      () => window.AyanApi.ownerBookingStatus(id, status.toUpperCase().replace("-", "_"), reason, apiSalonId()),
      `Booking marked ${status.toLowerCase()}.`,
      "The booking status could not be updated."
    );
    return;
  }
  const previousPayment = b.paymentStatus || "Pending";
  if (payment && b.paymentMethod !== "Salon credit" && ["Pending", "Paid"].includes(payment) && b.status !== "Completed") b.paymentStatus = payment;
  if (status === "Completed" && b.status !== "Completed") {
    if (b.paymentMethod !== "Salon credit" && !bookingPaymentConfirmed(b)) { toast("Mark the payment as paid/confirmed before completing this visit.", "error"); return; }
    ui.modal = null; completeBooking(id); return;
  }
  if (b.status === "Completed" || b.status === "Cancelled" || b.status === "No-show") { toast("Completed, cancelled and no-show records are terminal. Use a refund or reversal entry for corrections.", "error"); return; }
  if (!["Pending", "Confirmed", "Cancelled", "No-show"].includes(status)) { toast("That status transition is not allowed.", "error"); return; }
  const previous = b.status; b.status = status; const paymentNote = previousPayment !== (b.paymentStatus || "Pending") ? `; payment ${previousPayment} → ${b.paymentStatus}` : ""; state.audit.push({ id: uid("audit"), action: "Booking status changed", actor: "Owner", reason: `${previous} → ${status}${paymentNote}`, createdAt: new Date().toISOString() }); saveState(); ui.modal = null; toast(`Booking marked ${status.toLowerCase()}`); render();
}
async function saveApiSettings() {
  const readWhole = (selector, fallback) => {
    const raw = String(document.querySelector(selector)?.value ?? fallback).trim();
    const value = Number(raw);
    return Number.isSafeInteger(value) && value >= 0 ? value : null;
  };
  const name = document.querySelector("[data-salon-name]")?.value.trim() || "";
  const address = document.querySelector("[data-salon-address]")?.value.trim() || "";
  const phone = document.querySelector("[data-salon-phone]")?.value.trim() || "";
  const reminderDays = readWhole("[data-setting='reminderDefaultDays']", state.settings.reminderDefaultDays);
  const bonus = readWhole("[data-setting='walletBonus']", state.settings.walletBonus);
  const referrer = readWhole("[data-setting='referralReferrerReward']", state.settings.referralReferrerReward);
  const discount = readWhole("[data-setting='referralNewCustomerDiscount']", state.settings.referralNewCustomerDiscount);
  const minimum = readWhole("[data-setting='withdrawalMinimum']", state.settings.withdrawalMinimum);
  const maximum = readWhole("[data-setting='withdrawalDailyLimit']", state.settings.withdrawalDailyLimit);
  if (!name || !address || !phone || [reminderDays, bonus, referrer, discount, minimum, maximum].some((value) => value === null)) {
    toast("Complete the salon details and enter whole non-negative amounts.", "error");
    return;
  }
  const theme = safeHexColor(document.querySelector("[data-salon-theme-color]")?.value || state.salon.themeColor, DEFAULT_THEME_COLOR);
  // Raw data URLs are intentionally not accepted by the production API. The
  // owner supplies an HTTPS object-storage URL in the online branding editor.
  const logoInput = document.querySelector("[data-salon-logo-url]")?.value.trim();
  const logo = safeImageSource(logoInput || state.salon.logoDataUrl);
  if (logoInput && !logo) {
    toast("Logo URL must be a valid HTTPS image URL.", "error");
    return;
  }
  if (logo.startsWith("data:")) {
    toast("Upload the logo to approved HTTPS image storage before saving online settings.", "error");
    return;
  }
  let providerPatch;
  try {
    providerPatch = (state.paymentMethods || []).map((method) => {
      const key = String(method.provider).toLowerCase();
      const accountTitle = document.querySelector(`[data-provider-title="${key}"]`);
      const accountNumber = document.querySelector(`[data-provider-number="${key}"]`);
      const instructions = document.querySelector(`[data-provider-instructions-input="${key}"]`);
      const sortOrder = document.querySelector(`[data-provider-order="${key}"]`);
      const enabled = document.querySelector(`[data-provider-enabled="${key}"]`);
      const order = sortOrder ? Number(String(sortOrder.value ?? "").trim()) : Number(method.sortOrder || 0);
      if (sortOrder && (!String(sortOrder.value ?? "").trim() || !Number.isSafeInteger(order) || order < 0)) {
        throw new Error(`${method.displayName || method.provider} sort order must be a whole number of at least 0.`);
      }
      return {
        method,
        accountTitle: accountTitle?.value.trim() ?? String(method.accountTitle || ""),
        accountNumber: accountNumber?.value.trim() ?? String(method.accountNumber || ""),
        instructions: instructions?.value.trim() ?? String(method.instructions || ""),
        sortOrder: order,
        enabled: enabled ? enabled.checked : method.enabled !== false
      };
    });
  } catch (error) {
    toast(error?.message || "Check payment method settings.", "error");
    return;
  }
  ui.apiBusy = true;
  ui.modal = null;
  render();
  try {
    const saved = await window.AyanApi.request(`/api/salons/${encodeURIComponent(apiSalonId())}/settings`, {
      method: "PUT",
      body: JSON.stringify({
        name, address, phone, reminderDays, depositBonusMinor: bonus * 100,
        referrerRewardMinor: referrer * 100, newCustomerDiscountMinor: discount * 100,
        minimumWithdrawalMinor: minimum * 100, maximumDailyWithdrawalMinor: maximum * 100,
         consumePromoFirst: true, primaryColor: theme,
         logoUri: logo || ""
      })
    });
    await Promise.all(providerPatch.map(({ method, accountTitle, accountNumber, instructions, sortOrder, enabled }) =>
      window.AyanApi.ownerUpdatePaymentMethod(method.provider, {
        providerCode: method.provider,
        displayName: method.displayName || method.provider,
        accountTitle: accountTitle || null,
        accountReference: accountNumber || null,
        // Null preserves any server-side provider token; the browser never
        // receives or handles that secret.
        accountToken: null,
        instructions: instructions || null,
        enabled: enabled === true,
        sortOrder,
        mode: method.mode || "MANUAL"
      }, apiSalonId())
    ));
    state.salon = { ...state.salon, name, address, phone, themeColor: theme };
    applyRemoteSettings(saved);
    await hydrateRemoteOwner();
    ui.apiBusy = false;
    toast("Settings saved on the salon server.");
    render();
  } catch (error) {
    ui.apiBusy = false;
    if (apiSessionExpired(error)) ui.role = "customer";
    else toast(apiErrorText(error, "Settings could not be saved."), "error");
    render();
  }
}

function saveSettings() {
  if (apiModeEnabled()) { saveApiSettings(); return; }
  const rules = {
    walletTopUp: { min: 1, label: "Top-up amount" }, walletBonus: { min: 0, label: "Bonus credit" }, walletExpiryDays: { min: 0, label: "Wallet expiry" },
    withdrawalMinimum: { min: 0, label: "Minimum withdrawal" }, withdrawalDailyLimit: { min: 0, label: "Daily withdrawal limit" }, cancellationHours: { min: 0, label: "Cancellation notice" },
    reminderDefaultDays: { min: 0, label: "Reminder cycle" }, referralReferrerReward: { min: 0, label: "Referrer reward" }, referralNewCustomerDiscount: { min: 0, label: "New customer discount" }, monthlyReferralLimit: { min: 0, label: "Monthly referral limit" }
  };
  const settingsPatch = {};
  for (const el of document.querySelectorAll("[data-setting]")) {
    const key = el.dataset.setting; const rule = rules[key] || { min: 0, label: key }; const raw = String(el.value ?? "").trim(); const value = Number(raw);
    if (!raw || !Number.isSafeInteger(value) || value < rule.min) { toast(`${rule.label} must be a whole number of at least ${rule.min}.`, "error"); return; }
    settingsPatch[key] = value;
  }
  const boolPatch = {};
  document.querySelectorAll("[data-setting-bool]").forEach((el) => { boolPatch[el.dataset.settingBool] = el.checked; });
  const salonFields = ["name", "tagline", "phone", "address", "hours", "timezone"];
  const salonPatch = {};
  salonFields.forEach((field) => { const input = document.querySelector(`[data-salon-${field}]`); if (input) salonPatch[field] = input.value.trim(); });
  const themeInput = document.querySelector("[data-salon-theme-color]");
  if (themeInput) salonPatch.themeColor = safeHexColor(themeInput.value, DEFAULT_THEME_COLOR);
  if (Object.prototype.hasOwnProperty.call(salonPatch, "name") && !salonPatch.name) { toast("Salon name cannot be empty.", "error"); return; }
  const providerPatch = [];
  for (const method of (state.paymentMethods || [])) {
    const key = method.provider.toLowerCase(); const accountTitle = document.querySelector(`[data-provider-title="${key}"]`); const accountNumber = document.querySelector(`[data-provider-number="${key}"]`); const instructions = document.querySelector(`[data-provider-instructions-input="${key}"]`);
    const qrCode = document.querySelector(`[data-provider-qr="${key}"]`); const sortOrder = document.querySelector(`[data-provider-order="${key}"]`); const enabled = document.querySelector(`[data-provider-enabled="${key}"]`);
    const order = sortOrder ? Number(String(sortOrder.value ?? "").trim()) : method.sortOrder;
    if (sortOrder && (!String(sortOrder.value ?? "").trim() || !Number.isSafeInteger(order) || order < 0)) { toast(`${method.displayName || method.provider} sort order must be a whole number of at least 0.`, "error"); return; }
    providerPatch.push({ method, accountTitle: accountTitle?.value.trim(), accountNumber: accountNumber?.value.trim(), instructions: instructions?.value.trim(), qrCode: qrCode?.value.trim(), sortOrder: order, enabled: enabled?.checked });
  }
  Object.assign(state.settings, settingsPatch, boolPatch); Object.assign(state.salon, salonPatch);
  providerPatch.forEach(({ method, accountTitle, accountNumber, instructions, qrCode, sortOrder, enabled }) => { if (accountTitle !== undefined) method.accountTitle = accountTitle; if (accountNumber !== undefined) method.accountNumber = accountNumber; if (instructions !== undefined) method.instructions = instructions; if (qrCode !== undefined) method.qrCode = qrCode; if (sortOrder !== undefined) method.sortOrder = sortOrder; if (enabled !== undefined) method.enabled = enabled; });
  state.audit.push({ id: uid("audit"), action: "Settings changed", actor: "Owner", reason: "Owner updated configurable salon rules", createdAt: new Date().toISOString() }); saveState(); toast("Settings saved for future bookings and reminders"); render();
}
function downloadFile(name, content, type){const blob=new Blob([content],{type}),url=URL.createObjectURL(blob),a=document.createElement("a");a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),500);}

setTimeout(() => {
  if (apiModeEnabled()) bootstrapApi();
  else { ui.loading = false; render(); }
}, 650);

// The Android shell injects its HTTPS API origin after the bundled/remote page
// finishes loading. Wake the bootstrap immediately instead of relying only on
// the startup timer (which can race on fast devices).
if (typeof window !== "undefined") {
  window.addEventListener("ayan-api-configured", () => {
    if (apiModeEnabled() && ui.loading && !ui.apiBusy) bootstrapApi();
  });
  // The bundled phone app also looks up the published salon address by itself.
  // When that lookup finds a live server after the offline shell has already
  // painted, reconnect without asking the owner to retype anything.
  window.addEventListener("ayan-api-discovered", () => {
    if (ui.apiBusy || !apiModeEnabled()) return;
    ui.loading = true;
    render();
    bootstrapApi();
  });
}

if (typeof document !== "undefined") {
  document.addEventListener("visibilitychange", () => {
    if (document.hidden && ownerSessionActive()) lockOwner();
  });
}

function ownerHaircutStylesPanel() {
  const styles = (state.haircutStyles || []).slice().sort((a, b) => {
    const publishedFirst = Number(b.active !== false) - Number(a.active !== false);
    if (publishedFirst) return publishedFirst;
    return Number(a.displayOrder || 0) - Number(b.displayOrder || 0) || String(a.name).localeCompare(String(b.name));
  });
  const published = styles.filter((style) => style.active !== false).length;
  const rows = styles.map((style) => {
    const photo = safeImageSource(style.photoDataUrl || style.photo);
    const linked = style.serviceId ? service(style.serviceId) : null;
    const isPublished = style.active !== false;
    const thumbnail = photo ? `<img class="style-thumb" src="${photo}" alt="${htmlesc(style.name)}">` : `<span class="style-thumb-placeholder">${htmlesc(salonMarkGlyph())}</span>`;
    const linkNote = linked ? ` · books ${htmlesc(linked.name)}` : " · no booking link yet";
    return `<div class="list-row style-admin-row${isPublished ? "" : " is-hidden-style"}"><div class="style-admin-media">${thumbnail}</div><div class="list-row-main"><div class="list-title">${htmlesc(style.name)} ${statusTag(isPublished ? "Published" : "Hidden")}</div><div class="list-meta">${money(style.price)}${linkNote}</div>${style.description ? `<div class="list-meta">${htmlesc(style.description)}</div>` : ""}</div><div class="style-admin-actions"><button class="btn btn-secondary btn-small" data-action="edit-haircut-style" data-style-id="${style.id}">Edit</button><button class="btn btn-secondary btn-small" data-action="toggle-haircut-style" data-style-id="${style.id}" data-next-active="${isPublished ? "false" : "true"}">${isPublished ? "Hide" : "Show"}</button><button class="btn btn-ghost btn-small" data-action="remove-haircut-style" data-style-id="${style.id}">Remove</button></div></div>`;
  }).join("");
  return `<div class="surface panel"><div class="panel-head"><div><h2>Haircut styles & photos</h2><p>Add a photo, name and PKR price. Link a style to a service so customers can book it in one tap. Remove takes a style off the customer home screen and keeps your records.</p></div><div style="display:flex;gap:8px;align-items:center"><span class="tag tag-neutral">${published} published</span><button class="btn btn-primary btn-small" data-action="add-haircut-style">+ Add style</button></div></div><div class="list">${rows || `<div class="empty-state"><strong>No haircut styles yet</strong>Add a photo, name and PKR price to show a look on the customer home screen.</div>`}</div></div>`;
}

function ownerCatalogueProduction() {
  const services = state.services || []; const staffMembers = state.staff || []; const addons = state.addons || [];
  const addonRows = addons.map((a) => {
    const serviceNames = (a.serviceIds || []).map((id) => service(id)?.name).filter(Boolean);
    const selectedStaff = Array.isArray(a.staffIds) ? a.staffIds : [];
    const staffNames = selectedStaff.length ? selectedStaff.map((id) => staff(id)?.name).filter(Boolean).join(", ") || "Selected staff" : "All active staff";
    return `<div class="list-row"><div class="list-row-main"><div class="list-title">${htmlesc(a.name)} ${statusTag(a.active ? "Confirmed" : "Opted out")}</div><div class="list-meta">${money(a.price)} · ${a.duration} mins · ${htmlesc(serviceNames.join(", ") || "No services")}</div><div class="list-meta">Staff: ${htmlesc(staffNames)}</div></div><button class="btn btn-secondary btn-small" data-action="edit-addon" data-addon-id="${a.id}">Edit</button></div>`;
  }).join("");
  const stylePanel = ownerHaircutStylesPanel();
  return `${ownerNav()}${ownerHeader("Services & staff", "Edit prices, add-ons, durations and availability whenever the salon changes its menu.", `<button class="btn btn-secondary" data-action="add-staff">+ Add staff</button><button class="btn btn-secondary" data-action="add-addon">+ Add add-on</button><button class="btn btn-primary" data-action="add-service">+ Add service</button>`)}<div class="settings-grid"><div class="surface panel"><div class="panel-head"><div><h2>Service catalogue</h2><p>All values are PKR and owner editable.</p></div><span class="tag tag-success">${services.filter((s) => s.active).length} active</span></div><div class="list">${services.length ? services.map((s) => `<div class="list-row"><div class="list-row-main"><div class="list-title">${htmlesc(s.name)} ${statusTag(s.active ? "Confirmed" : "Opted out")}</div><div class="list-meta">${htmlesc(s.category)} · ${s.duration} mins · remind after ${s.repeatDays} days</div></div><div style="text-align:right"><div class="list-amount">${money(s.price)}</div><button class="btn btn-secondary btn-small" style="margin-top:6px" data-action="edit-service" data-service-id="${s.id}">Edit</button></div></div>`).join("") : `<div class="empty-state"><strong>No services yet</strong>Add a service before accepting bookings.</div>`}</div></div><div class="surface panel"><div class="panel-head"><div><h2>Optional add-ons</h2><p>Customers choose these explicitly; prices and staff availability are owner editable.</p></div><span class="tag tag-neutral">${addons.filter((a) => a.active).length} active</span></div><div class="list">${addonRows || `<div class="empty-state"><strong>No add-ons yet</strong>Add an optional extra to lift average bill value.</div>`}</div></div><div class="surface panel"><div class="panel-head"><div><h2>Staff & hours</h2><p>Slot availability follows active team members.</p></div><span class="tag tag-neutral">${staffMembers.filter((s) => s.active).length} active</span></div><div class="list">${staffMembers.length ? staffMembers.map((st) => `<div class="list-row"><div class="list-row-main"><div class="list-title">${htmlesc(st.name)} ${statusTag(st.active ? "Confirmed" : "Opted out")}</div><div class="list-meta">${htmlesc(st.role)} · ${htmlesc((st.skills || []).join(", "))}</div></div><div style="text-align:right"><div class="list-amount">${htmlesc(st.hours)}</div><button class="btn btn-secondary btn-small" style="margin-top:6px" data-action="edit-staff" data-staff-id="${st.id}">Edit</button></div></div>`).join("") : `<div class="empty-state"><strong>No staff yet</strong>Add a team member before accepting bookings.</div>`}</div></div>${stylePanel}</div>`;
}

function addonModal() {
  const m = ui.modal; const existing = (state.addons || []).find((a) => a.id === m.addonId);
  const a = existing || { name: "", price: 0, duration: 15, serviceIds: [], staffIds: [], active: true };
  const serviceIds = Array.isArray(a.serviceIds) ? a.serviceIds : []; const staffIds = Array.isArray(a.staffIds) ? a.staffIds : [];
  const serviceOptions = (state.services || []).map((s) => `<label class="check-row"><input type="checkbox" data-addon-service="${s.id}" ${serviceIds.includes(s.id) ? "checked" : ""}> ${htmlesc(s.name)} · ${htmlesc(s.category)}</label>`).join("");
  const staffOptions = (state.staff || []).map((st) => `<label class="check-row"><input type="checkbox" data-addon-staff="${st.id}" ${!staffIds.length || staffIds.includes(st.id) ? "checked" : ""}> ${htmlesc(st.name)}${st.active ? "" : " · inactive"}</label>`).join("");
  return modalShell(m.addonId ? "Edit add-on" : "Add add-on", "Customers must opt in; the salon controls price, duration and eligibility.", `<div class="form-grid"><div class="form-grid two"><div class="form-field"><label>Add-on name</label><input data-addon-name value="${htmlesc(a.name)}"></div><div class="form-field"><label>Price (PKR)</label><input type="number" min="0" step="1" data-addon-price value="${a.price}"></div><div class="form-field"><label>Extra duration (minutes)</label><input type="number" min="1" step="1" data-addon-duration value="${a.duration}"></div></div><div class="form-field"><label>Eligible services</label><div class="list">${serviceOptions || `<div class="empty-state"><strong>No services available</strong>Add a service first.</div>`}</div><small>Select at least one service.</small></div><div class="form-field"><label>Eligible staff</label><div class="list">${staffOptions || `<div class="empty-state"><strong>No staff available</strong>The add-on will be unavailable until staff is added.</div>`}</div><small>All selected staff are allowed. Select everyone to make it available to all active staff.</small></div><label class="check-row"><input type="checkbox" data-addon-active ${a.active ? "checked" : ""}> Available for new bookings</label></div>`, `<button class="btn btn-secondary" data-action="close-modal">Cancel</button><button class="btn btn-primary" data-action="save-addon" data-addon-id="${m.addonId || ""}">Save add-on</button>`);
}

function haircutStyleModal() {
  const m = ui.modal || {};
  const existing = (state.haircutStyles || []).find((item) => item.id === m.styleId);
  const style = existing || { name: "", photoDataUrl: "", price: 0, description: "", serviceId: "", active: true };
  const photo = safeImageSource(m.photoDataUrl || style.photoDataUrl);
  const preview = photo
    ? `<div class="style-editor-preview"><img src="${photo}" alt="Selected haircut style preview"><button type="button" class="btn btn-ghost btn-small" data-action="remove-haircut-photo">Remove photo</button></div>`
    : `<div class="style-editor-placeholder">No photo selected yet</div>`;
  const serviceOptions = `<option value="">Informational style (no booking link)</option>${(state.services || []).map((candidate) => `<option value="${candidate.id}" ${candidate.id === style.serviceId ? "selected" : ""}>${htmlesc(candidate.name)} · ${money(candidate.price)}</option>`).join("")}`;
  return modalShell(m.styleId ? "Edit haircut style" : "Add haircut style", "Publish a clean photo, price and optional booking link for customers.", `<div class="form-grid"><div class="form-field"><label>Haircut name</label><input data-style-name value="${htmlesc(style.name)}" placeholder="e.g. Textured crop"></div><div class="form-field"><label>Photo</label><input type="file" accept="image/*" data-style-photo-input><small>JPG, PNG or WEBP up to 2 MB. The image is compressed on this device.</small>${preview}</div><div class="form-grid two"><div class="form-field"><label>Price (PKR)</label><input type="number" min="0" step="1" data-style-price value="${Number(style.price || 0)}"></div><div class="form-field"><label>Bookable service</label><select data-style-service>${serviceOptions}</select></div></div><div class="form-field"><label>Description (optional)</label><textarea rows="3" data-style-description placeholder="A short note about the finish or length">${htmlesc(style.description || "")}</textarea></div><label class="check-row"><input type="checkbox" data-style-active ${style.active !== false ? "checked" : ""}> Show this style to customers</label></div>`, `<button class="btn btn-secondary" data-action="close-modal">Cancel</button><button class="btn btn-primary" data-action="save-haircut-style" data-style-id="${m.styleId || ""}">Save style</button>`);
}

function saveHaircutStyle(id) {
  const name = document.querySelector("[data-style-name]")?.value.trim();
  const price = Number(document.querySelector("[data-style-price]")?.value);
  const description = document.querySelector("[data-style-description]")?.value.trim() || "";
  const serviceId = document.querySelector("[data-style-service]")?.value || null;
  const active = !!document.querySelector("[data-style-active]")?.checked;
  const photoDataUrl = safeImageDataUrl(ui.modal?.photoDataUrl || (state.haircutStyles || []).find((item) => item.id === id)?.photoDataUrl);
  if (!name || !Number.isSafeInteger(price) || price < 0) { toast("Enter a haircut name and a whole-PKR price.", "error"); return; }
  if (apiModeEnabled()) {
    const existing = (state.haircutStyles || []).find((item) => item.id === id);
    const selectedSource = ui.modal?.photoDataUrl || existing?.photoDataUrl || "";
    const photoUri = safeImageSource(selectedSource);
    // The production API stores a bounded HTTPS image reference. The chooser
    // uploads through /media before this save; a data URL here means the upload
    // was interrupted or the value came from an older client.
    if (!photoUri || photoUri.startsWith("data:")) {
      toast("Upload a haircut photo first, then save the style.", "error");
      return;
    }
    const displayOrder = Number.isSafeInteger(Number(existing?.displayOrder)) ? Number(existing.displayOrder) : (state.haircutStyles || []).length;
    runRemoteOwnerMutation(
      () => window.AyanApi.ownerSaveHaircutStyle(id, {
        name, photoUri, photoAltText: name, priceMinor: price * 100,
        description, displayOrder, active, serviceId: serviceId || null
      }, apiSalonId()),
      "Haircut style saved on the salon server.",
      "The haircut style could not be saved. Check the HTTPS image URL and try again."
    );
    return;
  }
  const record = { name, price, description, serviceId: serviceId || null, photoDataUrl, active };
  if (id) {
    const existing = (state.haircutStyles || []).find((item) => item.id === id);
    if (!existing) { toast("Haircut style record not found.", "error"); return; }
    Object.assign(existing, record);
  } else {
    if (!Array.isArray(state.haircutStyles)) state.haircutStyles = [];
    state.haircutStyles.push({ id: uid("style"), ...record });
  }
  state.audit.push({ id: uid("audit"), action: "Haircut style changed", actor: "Owner", reason: `${name} · ${money(price)}`, createdAt: new Date().toISOString() });
  saveState(); ui.modal = null; toast("Haircut style saved"); render();
}

function apiHaircutStylePhotoUri(style) {
  // The production API stores a bounded HTTPS image reference. A data URL here
  // means the photo never reached the media endpoint, so it must not be saved.
  const source = safeImageSource(style?.photoDataUrl || style?.photo);
  return source.startsWith("data:") ? "" : source;
}

function apiHaircutStylePayload(style, overrides = {}) {
  return {
    name: style.name, photoUri: apiHaircutStylePhotoUri(style), photoAltText: style.name,
    priceMinor: Math.round(Number(style.price || 0) * 100), description: style.description || "",
    displayOrder: Number.isSafeInteger(Number(style.displayOrder)) ? Number(style.displayOrder) : 0,
    active: style.active !== false, serviceId: style.serviceId || null,
    ...overrides
  };
}

function toggleHaircutStyle(id, nextActive) {
  const style = (state.haircutStyles || []).find((item) => item.id === id);
  if (!style) { toast("Haircut style not found.", "error"); return; }
  if (apiModeEnabled()) {
    if (!apiHaircutStylePhotoUri(style)) { toast("Upload a haircut photo for this style before hiding or showing it.", "error"); return; }
    runRemoteOwnerMutation(
      () => window.AyanApi.ownerSaveHaircutStyle(id, apiHaircutStylePayload(style, { active: nextActive }), apiSalonId()),
      nextActive ? "Style is visible to customers again." : "Style hidden from the customer home screen.",
      "The haircut style visibility could not be saved."
    );
    return;
  }
  style.active = nextActive;
  state.audit.push({ id: uid("audit"), action: "Haircut style visibility", actor: "Owner", reason: `${style.name} · ${nextActive ? "Published" : "Hidden"}`, createdAt: new Date().toISOString() });
  saveState(); ui.modal = null; toast(nextActive ? "Style published" : "Style hidden"); render();
}

function removeHaircutStyle(id) {
  const style = (state.haircutStyles || []).find((item) => item.id === id);
  if (!style) { toast("Haircut style not found.", "error"); return; }
  const question = `Remove "${style.name}" from the customer home screen?\n\nBookings, visits, wallet and payment records are never deleted.`;
  if (typeof window.confirm === "function" && !window.confirm(question)) return;
  if (apiModeEnabled()) {
    runRemoteOwnerMutation(
      () => window.AyanApi.ownerArchiveHaircutStyle(id, apiSalonId()),
      "Haircut style removed from the customer home screen.",
      "The haircut style could not be removed."
    );
    return;
  }
  state.haircutStyles = (state.haircutStyles || []).filter((item) => item.id !== id);
  state.audit.push({ id: uid("audit"), action: "Haircut style removed", actor: "Owner", reason: style.name, createdAt: new Date().toISOString() });
  saveState(); ui.modal = null; toast("Haircut style removed"); render();
}

async function saveApiAddon(id, value) {
  ui.apiBusy = true; render();
  try {
    const path = `/api/salons/${encodeURIComponent(apiSalonId())}/add-ons${id ? `/${encodeURIComponent(id)}` : ""}`;
    await window.AyanApi.request(path, { method: id ? "PUT" : "POST", body: JSON.stringify(value) });
    await hydrateRemoteOwner();
    ui.apiBusy = false; ui.modal = null; toast("Add-on saved on the salon server."); render();
  } catch (error) {
    ui.apiBusy = false;
    if (apiSessionExpired(error)) ui.modal = null;
    else ui.modal.error = apiErrorText(error, "The add-on could not be saved.");
    render();
  }
}

function saveAddon(id) {
  const name = document.querySelector("[data-addon-name]")?.value.trim();
  const price = Number(document.querySelector("[data-addon-price]")?.value);
  const duration = Number(document.querySelector("[data-addon-duration]")?.value);
  const serviceIds = [...document.querySelectorAll("[data-addon-service]:checked")].map((el) => el.dataset.addonService);
  const checkedStaffIds = [...document.querySelectorAll("[data-addon-staff]:checked")].map((el) => el.dataset.addonStaff);
  const staffCount = (state.staff || []).length;
  const staffIds = !staffCount || checkedStaffIds.length === staffCount ? [] : [...new Set(checkedStaffIds)];
  const active = !!document.querySelector("[data-addon-active]")?.checked;
  if (!name || !Number.isSafeInteger(price) || price < 0 || !Number.isSafeInteger(duration) || duration <= 0 || !serviceIds.length || (staffCount > 0 && !checkedStaffIds.length)) {
    toast("Enter a name, valid PKR price/duration, and choose a service and staff.", "error"); return;
  }
  if (apiModeEnabled()) {
    if (serviceIds.length !== 1) { toast("The online catalogue currently links each add-on to one service. Choose one service.", "error"); return; }
    if (id && !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(String(id))) { toast("This add-on cannot be edited until it has a server ID.", "error"); return; }
    saveApiAddon(id, { name, priceMinor: price * 100, durationMinutes: duration, serviceId: serviceIds[0], description: null, active });
    return;
  }
  const record = { name, price, duration, serviceIds: [...new Set(serviceIds)], staffIds, active };
  if (id) {
    const existing = (state.addons || []).find((a) => a.id === id);
    if (!existing) { toast("Add-on record not found.", "error"); return; }
    Object.assign(existing, record);
  } else {
    if (!Array.isArray(state.addons)) state.addons = [];
    state.addons.push({ id: uid("addon"), ...record });
  }
  state.audit.push({ id: uid("audit"), action: "Add-on changed", actor: "Owner", reason: `${name} · ${money(price)} · ${duration} mins`, createdAt: new Date().toISOString() });
  saveState(); ui.modal = null; toast("Add-on saved"); render();
}

function customerMoreScreenBase() {
  const c = customer(); const refs = (state.referrals || []).filter((r) => r.referrerId === c.id); const referralCode = referrerCodeFor(c);
  const referralHistory = (state.referrals || []).find((r) => r.referredCustomerId === c.id);
  const canClaim = state.settings.referralEnabled !== false && !referralHistory && customerVisitCount(c.id) === 0;
  const claimBlock = canClaim
    ? `<div class="form-field"><label>Have a referral code?</label><div style="display:flex;gap:8px"><input data-referral-claim-code placeholder="e.g. SALON100" autocomplete="off"><button class="btn btn-soft btn-small" data-action="claim-referral">Apply code</button></div><small>The discount applies to your first paid visit. The referrer reward stays pending until completion.</small></div>`
    : referralHistory ? `<div class="notice ${referralHistory.status === "Rejected" ? "notice-warning" : "notice-info"}" style="margin-top:14px">Referral claim ${statusTag(referralHistory.status)}. It cannot be changed after signup.</div>`
      : state.settings.referralEnabled === false ? `<div class="notice notice-info" style="margin-top:14px">Referral claims are currently paused by the salon.</div>` : "";
  return `<div class="page-head"><div class="page-title"><div class="eyebrow">More</div><h1>Your salon account.</h1><p>Keep your profile current, share your referral code, and choose how we contact you.</p></div></div><div class="settings-grid"><div class="surface panel"><div class="section-head"><h2>My profile</h2><button class="btn btn-secondary btn-small" data-action="edit-my-profile">Edit</button></div><div class="settings-line"><span>Name</span><strong>${htmlesc(c.name)}</strong></div><div class="settings-line"><span>Mobile</span><strong>${htmlesc(c.phone)}</strong></div><div class="settings-line"><span>Last visit</span><strong>${dateLabel(c.lastVisit)}</strong></div><div class="settings-line"><span>Promotional messages</span><strong><button class="toggle ${c.consent ? "on" : ""}" data-action="toggle-consent" aria-label="Toggle promotional messages"></button></strong></div><div class="notice notice-info" style="margin-top:13px">We only send service reminders and offers when you agree. You can opt out any time.</div></div><div class="surface panel"><div class="section-head"><h2>Refer & earn</h2><span class="tag tag-pink">${money(state.settings.referralReferrerReward)} reward</span></div><p class="list-meta" style="font-size:13px;margin-bottom:15px">Invite a friend. Your reward stays pending until their first paid visit is completed.</p><div class="summary-box" style="margin-bottom:13px"><div class="summary-line"><span>Your code</span><strong>${htmlesc(referralCode)}</strong></div><div class="summary-line"><span>Friend gets</span><strong>${money(state.settings.referralNewCustomerDiscount)} off</strong></div><div class="summary-line"><span>You get</span><strong>${money(state.settings.referralReferrerReward)} credit</strong></div></div><button class="btn btn-primary btn-block" data-action="share-referral" data-referral-code="${htmlesc(referralCode)}">Share code</button>${claimBlock}<div class="section" style="margin-top:17px"><div class="list">${refs.length ? refs.map((r) => `<div class="list-row"><div class="list-row-main"><div class="list-title">${htmlesc(r.code)}</div><div class="list-meta">Created ${dateLabel(r.createdAt)} · ${r.referredCustomerId ? customerName(r.referredCustomerId) : "Waiting for signup"}</div></div>${statusTag(r.status)}</div>`).join("") : `<div class="empty-state"><strong>No referrals yet</strong>Your shared referrals will be tracked here.</div>`}</div></div></div></div>`;
}

function customerAppearancePanel() {
  const enabled = customerDarkMode();
  return `<div class="surface panel appearance-panel"><div class="section-head"><div><h2>App appearance</h2><span class="hint">Saved on this device</span></div><button class="toggle ${enabled ? "on" : ""}" data-action="toggle-dark-mode" aria-label="Toggle dark mode"></button></div><div class="settings-line"><span>Theme</span><strong>${enabled ? "Dark mode" : "Light mode"}</strong></div><p class="list-meta">Choose the view that feels comfortable. Your preference stays with this customer account.</p></div>`;
}

function customerMoreScreen() {
  const html = customerMoreScreenBase();
  // The salon server address is an owner setting on purpose: a customer must not
  // be able to point their phone at an unknown server or break their own sign-in.
  return html.replace('<div class="settings-grid">', `<div class="settings-grid">${customerAppearancePanel()}${pinSecurityPanel()}`);
}

function customerModal() {
  const m = ui.modal; const isNew = !m.customerId; const existing = state.customers.find((x) => x.id === m.customerId);
  const c = existing || { name: "", phone: m.phone || "", consent: false, lastVisit: null };
  const title = m.signup ? "Create your salon profile" : isNew ? "Add customer" : "Edit customer";
  const referralField = isNew ? `<div class="form-field"><label>Referral code (optional)</label><input data-customer-referral-code placeholder="e.g. SALON100" autocomplete="off"><small>Applied only after this mobile is verified and before the first paid visit.</small></div>` : "";
  const existingPin = !m.signup && m.customerId ? (state.customers || []).find((item) => item.id === m.customerId) : null;
  const pinFields = m.signup && !apiModeEnabled()
    ? `<div class="form-grid two"><div class="form-field"><label>Create a password</label><input data-customer-pin type="password" inputmode="text" autocomplete="new-password" maxlength="20" placeholder="10 to 20 characters"><small>Your mobile number plus this password open your profile. ${SIGN_IN_PASSWORD_RULE}</small></div><div class="form-field"><label>Confirm password</label><input data-customer-pin-confirm type="password" inputmode="text" autocomplete="new-password" maxlength="20" placeholder="Repeat the password"></div></div>`
    : "";
  const pinReset = existingPin && customerHasPin(existingPin) && !apiModeEnabled()
    ? `<div class="notice notice-info" style="margin-top:12px">A sign-in password is set for this customer. <button class="btn btn-soft btn-small" style="margin-top:8px" data-action="reset-customer-pin" data-customer-id="${htmlesc(existingPin.id)}">Reset password</button></div>`
    : "";
  // No SMS code exists any more, so a customer who forgot the password can only
  // be helped at the counter. This is that control, and it works on the server
  // so the new password signs the customer in on their own phone immediately.
  const apiPinReset = existingPin && apiModeEnabled()
    ? `<div class="surface" style="margin-top:14px;padding:14px"><div class="section-head"><div><h3 style="margin:0;font-size:15px">Sign-in password</h3><span class="list-meta" style="font-size:12px">Owner only</span></div></div><p class="list-meta" style="font-size:13px;margin:7px 0 11px">Forgot password? Set a new one here, then ask the customer to change it from More once they are signed in.</p><div class="form-field"><label for="customer-new-password">New password</label><input id="customer-new-password" data-customer-new-password type="password" inputmode="text" autocomplete="new-password" maxlength="20" placeholder="10 to 20 characters"><small>${SIGN_IN_PASSWORD_RULE}</small></div><button class="btn btn-secondary" data-action="set-customer-password" data-customer-id="${htmlesc(existingPin.id)}">Set new password</button></div>`
    : "";
  return modalShell(title, "A mobile number is the customer lookup key. Promotional messages require clear consent.", `<div class="form-grid"><div class="form-grid two"><div class="form-field"><label>Name</label><input data-customer-name value="${htmlesc(c.name)}" autocomplete="name"></div><div class="form-field"><label>Mobile</label><input data-customer-phone value="${htmlesc(c.phone)}" type="tel" inputmode="tel" autocomplete="tel" placeholder="03xx xxx xxxx"></div></div>${pinFields}${referralField}<label class="check-row"><input type="checkbox" data-customer-consent ${c.consent ? "checked" : ""}> Customer agrees to promotional messages and service reminders.</label>${pinReset}${apiPinReset}</div>`, `<button class="btn btn-secondary" data-action="close-modal">Cancel</button><button class="btn btn-primary" data-action="save-customer" data-customer-id="${m.customerId || ""}">${m.signup ? "Create profile" : "Save profile"}</button>`);
}

function otpModal() {
  return lookupModal();
}

/** Opens the signed-in customer workspace without exposing data before the password check. */
function signInOfflineCustomer(target) {
  state.currentCustomerId = target.id;
  clearPinFailures(target.id);
  saveState();
  ui.modal = null;
  ui.role = "customer";
  ui.customerScreen = "home";
  render();
}

/** Offline password gate: verify the stored digest, or create the first password for this profile. */
function submitOfflineLookupPin(customerId) {
  const modalState = ui.modal || { type: "lookup" };
  const target = (state.customers || []).find((customer) => customer.id === customerId);
  if (!target) { modalState.error = "That profile is no longer available. Search again."; modalState.stage = "phone"; render(); return; }
  const lockedMinutes = pinLockRemainingMinutes(target.id);
  if (lockedMinutes) { modalState.error = `Too many incorrect attempts. Try again in about ${lockedMinutes} minute${lockedMinutes === 1 ? "" : "s"}.`; render(); return; }
  const pin = String(document.querySelector("[data-lookup-pin]")?.value || "").trim();
  if (!validSignInPin(pin)) { modalState.error = SIGN_IN_PASSWORD_RULE; render(); return; }
  if (!customerHasPin(target)) {
    const confirmPin = String(document.querySelector("[data-lookup-pin-confirm]")?.value || "").trim();
    if (pin !== confirmPin) { modalState.error = "Both password entries must match."; render(); return; }
    assignCustomerPin(target, pin);
    saveState();
    signInOfflineCustomer(target);
    toast("Password created. Keep it private.");
    return;
  }
  if (!verifyCustomerPin(target, pin)) {
    registerPinFailure(target.id);
    modalState.error = "Incorrect password. Try again.";
    render();
    return;
  }
  signInOfflineCustomer(target);
  toast(`Signed in as ${customerName(target.id)}`);
}

/**
 * Online sign-in: the mobile number plus the account password, in one step.
 * SMS verification codes are switched off everywhere, so there is no code
 * screen and no fallback to wait for.
 */
function submitApiPin() {
  const modalState = ui.modal || { type: "lookup" };
  const phone = apiPhoneInput();
  if (!isValidPakistaniMobile(phone)) {
    modalState.error = "Enter a complete Pakistani mobile number, for example 0300 123 4567.";
    modalState.stage = "phone";
    ui.modal = modalState;
    render();
    return;
  }
  const password = String(document.querySelector("[data-lookup-password]")?.value || "").trim();
  if (!validSignInPin(password)) {
    modalState.error = `Enter your password. ${SIGN_IN_PASSWORD_RULE}`;
    modalState.stage = "phone";
    ui.modal = modalState;
    render();
    return;
  }
  ui.apiBusy = true;
  modalState.error = "";
  modalState.phone = phone;
  render();
  let sessionHandled = false;
  (async () => {
    try {
      const session = await window.AyanApi.verifyPin(phone, password, apiSalonId());
      sessionHandled = true;
      await finishApiSession(session, { returnToBooking: modalState.returnToBooking === true });
      return;
    } catch (error) {
      if (String(error?.code || "") === "PIN_NOT_SET" || error?.status === 404) {
        modalState.error = "This mobile number has no password yet. Tap Create new account, or ask the salon owner to set one.";
      } else if (error?.status === 401) {
        modalState.error = "Wrong mobile number or password. Check both and try again.";
      } else if (error?.status === 429) {
        modalState.error = "Too many attempts. Please wait a few minutes and try again.";
      } else {
        modalState.error = apiErrorText(error, "Sign in failed. Check the mobile number and the password.");
      }
    } finally {
      if (!sessionHandled) {
        ui.apiBusy = false;
        ui.modal = modalState;
        render();
      }
    }
  })();
}

/**
 * Creates the customer account from the mobile number and the chosen password.
 * Nothing else is required: there is no verification code to wait for.
 */
function submitCustomerRegistration() {
  const modalState = ui.modal || { type: "lookup", stage: "register" };
  const name = String(document.querySelector("[data-api-customer-name]")?.value || modalState.name || "").trim();
  const typedPhone = String(document.querySelector("[data-api-customer-phone]")?.value || modalState.phone || "").trim();
  let phone = "";
  try { phone = canonicalPakistaniPhone(typedPhone); } catch (_) { phone = ""; }
  const password = String(document.querySelector("[data-api-customer-password]")?.value || "").trim();
  const confirmation = String(document.querySelector("[data-api-customer-password-confirm]")?.value || "").trim();
  const consent = !!document.querySelector("[data-api-customer-consent]")?.checked;
  modalState.stage = "register";
  modalState.name = name;
  modalState.phone = typedPhone;
  modalState.consent = consent;
  if (!name) { modalState.error = "Enter your name."; ui.modal = modalState; render(); return; }
  if (!isValidPakistaniMobile(phone)) {
    modalState.error = "Enter a complete Pakistani mobile number, for example 0300 123 4567.";
    ui.modal = modalState;
    render();
    return;
  }
  if (!validSignInPin(password)) {
    modalState.error = `Choose a password. ${SIGN_IN_PASSWORD_RULE}`;
    ui.modal = modalState;
    render();
    return;
  }
  if (password !== confirmation) { modalState.error = "Both password entries must match."; ui.modal = modalState; render(); return; }
  ui.apiBusy = true;
  modalState.error = "";
  modalState.phone = phone;
  render();
  let sessionHandled = false;
  (async () => {
    try {
      const session = await window.AyanApi.signupCustomer(phone, name, password, consent, apiSalonId());
      sessionHandled = true;
      await finishApiSession(session, { returnToBooking: modalState.returnToBooking === true });
      toast("Account created. Your mobile number and password sign you in.");
      return;
    } catch (error) {
      if (error?.status === 409) {
        modalState.error = apiErrorText(error, "An account already exists for this mobile number. Sign in instead.");
      } else if (error?.status === 429) {
        modalState.error = "Too many attempts from this connection. Please wait a few minutes and try again.";
      } else {
        modalState.error = apiErrorText(error, "The account could not be created. Please try again.");
      }
    } finally {
      if (!sessionHandled) {
        ui.apiBusy = false;
        ui.modal = modalState;
        render();
      }
    }
  })();
}

/** Sets or changes the signed-in customer's password from the profile screen. */
function saveSignInPin() {
  const current = String(document.querySelector("[data-pin-current]")?.value || "").trim();
  const next = String(document.querySelector("[data-pin-new]")?.value || "").trim();
  const confirmation = String(document.querySelector("[data-pin-confirm]")?.value || "").trim();
  if (!validSignInPin(next)) { toast(SIGN_IN_PASSWORD_RULE, "error"); return; }
  if (next !== confirmation) { toast("Both password entries must match.", "error"); return; }
  if (apiModeEnabled()) {
    ui.apiBusy = true;
    render();
    (async () => {
      try {
        await window.AyanApi.setPin(next, current, apiSalonId());
        ui.apiBusy = false;
        toast("Password saved on the salon server.");
      } catch (error) {
        ui.apiBusy = false;
        toast(apiErrorText(error, "The password could not be saved."), "error");
      }
      render();
    })();
    return;
  }
  const target = customer();
  if (!target) { toast("Sign in first.", "error"); return; }
  const lockedMinutes = pinLockRemainingMinutes(target.id);
  if (lockedMinutes) { toast(`Too many incorrect attempts. Try again in about ${lockedMinutes} minute${lockedMinutes === 1 ? "" : "s"}.`, "error"); return; }
  if (customerHasPin(target) && !verifyCustomerPin(target, current)) {
    registerPinFailure(target.id);
    toast("Your current password is incorrect.", "error");
    render();
    return;
  }
  assignCustomerPin(target, next);
  saveState();
  toast("Password saved on this phone.");
  render();
}

/**
 * Owner sets a brand new sign-in password for one customer. SMS recovery does
 * not exist, so this is the counter-side reset: the customer walks up, the
 * owner types a fresh password, and the customer signs in with it on their own
 * phone and changes it from More.
 */
function setCustomerSignInPassword(customerId) {
  const password = String(document.querySelector("[data-customer-new-password]")?.value || "").trim();
  if (!customerId) { toast("Choose a customer first.", "error"); return; }
  if (!validSignInPin(password)) { toast(SIGN_IN_PASSWORD_RULE, "error"); return; }
  if (!apiModeEnabled()) {
    toast("Connect the salon server before setting a customer password.", "error");
    return;
  }
  ui.apiBusy = true;
  render();
  (async () => {
    try {
      await window.AyanApi.resetCustomerPassword(customerId, password, apiSalonId());
      toast("New password saved. Ask the customer to sign in and change it from More.");
    } catch (error) {
      toast(apiErrorText(error, "The customer password could not be saved."), "error");
    } finally {
      ui.apiBusy = false;
      render();
    }
  })();
}

/** Owner-side reset so a customer who forgot the password can set a new one. */
function resetCustomerPinFromOwner(customerId) {
  if (!requireOwnerSession()) return;
  const target = (state.customers || []).find((customer) => customer.id === customerId);
  if (!target) { toast("Customer record not found.", "error"); return; }
  const confirmed = window.confirm(`Clear the sign-in password for ${target.name}? They will set a new password on the next sign-in.`);
  if (!confirmed) return;
  resetCustomerPin(target);
  state.audit.push({ id: uid("audit"), action: "Sign-in password reset", actor: "Owner", reason: `${target.name} · password cleared by owner`, createdAt: new Date().toISOString() });
  saveState();
  ui.modal = null;
  toast("Password cleared. The customer sets a new password on the next sign-in.");
  render();
}

function pinSecurityPanel() {
  const target = customer();
  const label = customerHasPin(target) ? "Password set" : "Not set yet";
  const intro = apiModeEnabled()
    ? "Your mobile number and this password open your profile. No SMS code is used anywhere in this app."
    : "Your mobile number and this password open your profile on this phone. Five wrong attempts pause sign-in for 15 minutes.";
  return `<div class="surface panel"><div class="section-head"><h2>Sign-in password</h2><span class="tag ${customerHasPin(target) ? "tag-success" : "tag-neutral"}">${label}</span></div><p class="list-meta" style="font-size:13px;margin-bottom:15px">${intro}</p><div class="form-grid two"><div class="form-field"><label>New password</label><input data-pin-new type="password" inputmode="text" autocomplete="new-password" maxlength="20" placeholder="10 to 20 characters"></div><div class="form-field"><label>Confirm new password</label><input data-pin-confirm type="password" inputmode="text" autocomplete="new-password" maxlength="20" placeholder="Repeat the password"></div></div><div class="form-field"><label>Current password (only when changing it)</label><input data-pin-current type="password" inputmode="text" autocomplete="current-password" maxlength="20" placeholder="Your current password"></div><button class="btn btn-primary" data-action="save-sign-in-pin">${customerHasPin(target) ? "Change password" : "Create password"}</button></div>`;
}

/** Only the installed Android shell can change the salon server address. */
function serverAddressPanel() {
  if (!nativeShellAvailable()) return "";
  const configured = nativeServerUrl();
  const status = configured || "Offline app on this phone";
  return `<div class="surface panel"><div class="section-head"><h2>Salon server address</h2><span class="tag ${configured ? "tag-success" : "tag-neutral"}">${configured ? "Connected" : "Offline"}</span></div><p class="list-meta" style="font-size:13px;margin-bottom:15px">Point this phone at the salon server running on the shop laptop. Leave it empty to keep this phone working offline on its own data.</p><div class="settings-line"><span>Current</span><strong style="word-break:break-all">${htmlesc(status)}</strong></div><div class="form-field" style="margin-top:12px"><label>Server address (https)</label><input data-server-url value="${htmlesc(configured)}" placeholder="https://salon.example.com" autocapitalize="off" autocomplete="off" spellcheck="false"></div><div style="display:flex;gap:8px;flex-wrap:wrap"><button class="btn btn-primary" data-action="save-server-url">Save &amp; reconnect</button><button class="btn btn-secondary" data-action="clear-server-url">Use offline mode</button></div><div class="notice notice-info" style="margin-top:13px">Only https addresses are accepted. Ask the person who set up the laptop for the current address.</div></div>`;
}

function saveServerAddress() {
  const value = String(document.querySelector("[data-server-url]")?.value || "").trim();
  if (!value) { clearServerAddress(); return; }
  if (!/^https:\/\/[^\s/?#]+$/i.test(value)) {
    toast("Enter a full https:// server address, for example https://salon.example.com.", "error");
    return;
  }
  let accepted = false;
  try { accepted = window.AyanSalonNative?.setServerUrl?.(value) === true; } catch (_) { accepted = false; }
  if (!accepted) { toast("That address was not accepted. Use a bare https:// address without a path.", "error"); return; }
  // A deliberate choice beats the published address the app finds by itself.
  try { localStorage.removeItem("ayan-api-opt-out"); } catch (_) { /* private storage */ }
  toast("Server address saved. Reconnecting this phone.");
}

function clearServerAddress() {
  let cleared = false;
  try { cleared = window.AyanSalonNative?.clearServerUrl?.() !== false; } catch (_) { cleared = false; }
  // Remember that this phone really wants to stay offline, otherwise the
  // automatic lookup would reconnect it on the next start.
  if (cleared) { try { localStorage.setItem("ayan-api-opt-out", "1"); } catch (_) { /* private storage */ } }
  toast(cleared ? "Offline mode restored on this phone." : "The address could not be cleared.", cleared ? "success" : "error");
}

/**
 * Only the installed Android shell can carry the salon name and logo on its
 * launch screen and on a pinned home-screen icon. Android cannot rename an
 * installed app's own launcher entry, so this offers a shortcut instead.
 */
function phoneAppPanel() {
  if (!nativeShellAvailable()) return "";
  const applied = nativeSalonName();
  const suggested = applied || String(state?.salon?.name || "").trim();
  const chosen = ui.phoneIconDataUrl || safeImageDataUrl(state?.salon?.logoDataUrl);
  const preview = chosen
    ? `<div class="logo-preview"><img src="${chosen}" alt="Home-screen icon preview"></div>`
    : `<div class="logo-preview"><div class="logo-placeholder">${htmlesc(initialsFor(suggested) || "S")}</div></div>`;
  return `<div class="surface panel"><div class="section-head"><h2>Phone app name &amp; logo</h2><span class="tag ${applied ? "tag-success" : "tag-neutral"}">${applied ? "Applied" : "Not set"}</span></div><p class="list-meta" style="font-size:13px;margin-bottom:15px">Android keeps the installed app's own name, so this pins a home-screen icon that carries your salon name and logo. The launch screen uses them from the next start.</p><div class="settings-line"><span>On this phone</span><strong>${htmlesc(applied || "No salon name yet")}</strong></div><div class="form-field" style="margin-top:12px"><label>Salon name on this phone</label><input data-phone-salon-name maxlength="40" value="${htmlesc(suggested)}" placeholder="Your salon name"></div><div class="form-field"><label>Home-screen icon</label><input type="file" accept="image/*" data-phone-icon-input><small>Choose a square JPG, PNG or WEBP up to 2 MB. Leave it empty to use your initials.</small></div>${preview}<div style="display:flex;gap:8px;flex-wrap:wrap;margin-top:12px"><button class="btn btn-primary" data-action="apply-phone-branding">Apply to this phone</button></div><div class="notice notice-info" style="margin-top:13px">Saved on this phone only. It does not change the salon profile other staff or customers see.</div></div>`;
}

/** Two initials for the fallback mark, matching the shell's own rule. */
function initialsFor(name) {
  const cleaned = String(name || "").trim().replace(/\s{2,}/g, " ");
  if (!cleaned) return "";
  return cleaned.split(" ").slice(0, 2).map((part) => part.charAt(0).toUpperCase()).join("");
}

function applyPhoneBranding() {
  const name = String(document.querySelector("[data-phone-salon-name]")?.value || "").trim().replace(/\s{2,}/g, " ");
  if (!name) { toast("Enter the salon name to show on this phone.", "error"); return; }
  const logo = ui.phoneIconDataUrl || safeImageDataUrl(state?.salon?.logoDataUrl) || "";
  let accepted = false;
  try { accepted = window.AyanSalonNative?.applyBranding?.(name, logo) === true; } catch (_) { accepted = false; }
  if (!accepted) { toast("The salon name could not be saved on this phone.", "error"); return; }
  ui.phoneIconDataUrl = "";
  toast("Look for your salon icon on the home screen.", "success");
  render();
}

function stylePhotoModal() {
  const style = (state.haircutStyles || []).find((item) => item.id === ui.modal?.styleId);
  if (!style) return "";
  const photo = safeImageSource(style.photoDataUrl || style.photo);
  const linkedService = style.serviceId ? service(style.serviceId) : null;
  const price = Number.isSafeInteger(Number(style.price)) ? Number(style.price) : Number(linkedService?.price || 0);
  const media = photo
    ? `<img src="${photo}" alt="${htmlesc(style.name)} haircut style">`
    : `<div class="style-card-placeholder" aria-hidden="true"><span>${htmlesc(salonMarkGlyph())}</span></div>`;
  return modalShell(htmlesc(style.name), style.description ? htmlesc(style.description) : "Fresh from the salon chair.", `<div class="style-photo-stage">${media}</div><div class="summary-box" style="margin-top:14px"><div class="summary-line"><span>Price</span><strong>${money(price)}</strong></div>${linkedService ? `<div class="summary-line"><span>Booking</span><strong>${htmlesc(linkedService.name)} · ${linkedService.duration} mins</strong></div>` : `<div class="summary-line"><span>Booking</span><strong>Ask at the salon</strong></div>`}</div>`, `<button class="btn btn-secondary" data-action="close-modal">Close</button>${linkedService ? `<button class="btn btn-primary" data-action="book-haircut-style" data-style-id="${htmlesc(style.id)}">Book this look</button>` : `<button class="btn btn-primary" data-action="open-booking">Choose a service</button>`}`);
}

function lookupModal() {
  const m = ui.modal || {}; const phone = m.phone || ""; const valid = isValidPakistaniMobile(phone);
  if (apiModeEnabled()) {
    const stage = m.stage || "phone";
    const message = m.error ? `<div class="notice notice-warning" role="alert">${htmlesc(m.error)}</div>` : "";
    const busy = ui.apiBusy ? "disabled" : "";
    if (stage === "register") {
      return modalShell("Create your account", "Your mobile number and a password are all you need. No SMS code is sent.", `<div class="form-grid"><div class="form-field"><label for="api-customer-name">Your name</label><input id="api-customer-name" data-api-customer-name autocomplete="name" placeholder="Full name" value="${htmlesc(m.name || "")}" ${busy}></div><div class="form-field"><label for="api-customer-phone">Mobile number</label><input id="api-customer-phone" data-api-customer-phone type="tel" inputmode="tel" autocomplete="tel" placeholder="03xx xxx xxxx" value="${htmlesc(phone)}" ${busy}><small>This number becomes your account name at this salon.</small></div><div class="form-field"><label for="api-customer-password">Password</label><input id="api-customer-password" data-api-customer-password type="password" inputmode="text" autocomplete="new-password" maxlength="20" placeholder="10 to 20 characters" ${busy}><small>${SIGN_IN_PASSWORD_RULE} Never share it with anyone, not even salon staff.</small></div><div class="form-field"><label for="api-customer-password-confirm">Confirm password</label><input id="api-customer-password-confirm" data-api-customer-password-confirm type="password" inputmode="text" autocomplete="new-password" maxlength="20" placeholder="Repeat the password" ${busy}></div><label class="check-row"><input type="checkbox" data-api-customer-consent ${m.consent ? "checked" : ""} ${busy}> I agree to service reminders and promotional messages from this salon.</label>${message}<div class="notice notice-info">One phone keeps one account. A few accounts can be created from the same internet connection.</div></div>`, `<button class="btn btn-secondary" data-action="close-modal">Cancel</button><button class="btn btn-soft btn-small" data-action="back-to-phone" ${busy}>Back</button><button class="btn btn-primary" data-action="submit-registration" ${busy}>Create account</button>`);
    }
    return modalShell("Sign in or create your account", "Your mobile number and password open your salon profile. No SMS code is used.", `<div class="form-grid"><div class="form-field"><label for="lookup-phone">Pakistani mobile number</label><input id="lookup-phone" data-lookup-phone type="tel" inputmode="tel" autocomplete="tel" placeholder="03xx xxx xxxx" value="${htmlesc(phone)}" ${busy}><small>Use the complete number, for example 0300 123 4567.</small></div><div class="form-field"><label for="lookup-password">Password</label><input id="lookup-password" data-lookup-password type="password" inputmode="text" autocomplete="current-password" maxlength="20" placeholder="Your password" ${busy}></div>${message}<div class="notice notice-info">New customer? Tap Create new account. Existing customer? Sign in with your number and password.</div></div>`, `<button class="btn btn-secondary" data-action="close-modal">Cancel</button><button class="btn btn-soft" data-action="begin-registration" ${busy}>Create new account</button><button class="btn btn-primary" data-action="submit-api-pin" ${busy}>Sign in</button>`);
  }
  const matches = m.searched && valid ? (state.customers || []).filter((c) => samePakistaniMobile(c.phone, phone)) : [];
  const match = matches.length === 1 ? matches[0] : null;
  const stage = m.stage || "phone";
  if (match && (stage === "pin" || stage === "create-pin")) {
    const creating = stage === "create-pin";
    const lockedMinutes = pinLockRemainingMinutes(match.id);
    const message = m.error ? `<div class="notice notice-warning" role="alert">${htmlesc(m.error)}</div>` : "";
    const lockNotice = lockedMinutes
      ? `<div class="notice notice-warning" role="alert">Too many incorrect password attempts. Try again in about ${lockedMinutes} minute${lockedMinutes === 1 ? "" : "s"}.</div>`
      : "";
    const fields = creating
      ? `<div class="form-field"><label for="lookup-pin">Create a password</label><input id="lookup-pin" data-lookup-pin type="password" inputmode="text" autocomplete="new-password" maxlength="20" placeholder="10 to 20 characters" ${lockedMinutes ? "disabled" : ""}><small>Keep your profile private: the mobile number alone will not open it again. ${SIGN_IN_PASSWORD_RULE}</small></div><div class="form-field"><label for="lookup-pin-confirm">Confirm password</label><input id="lookup-pin-confirm" data-lookup-pin-confirm type="password" inputmode="text" autocomplete="new-password" maxlength="20" placeholder="Repeat the password" ${lockedMinutes ? "disabled" : ""}></div>`
      : `<div class="form-field"><label for="lookup-pin">Password</label><input id="lookup-pin" data-lookup-pin type="password" inputmode="text" autocomplete="current-password" maxlength="20" placeholder="Your password" ${lockedMinutes ? "disabled" : ""}><small>Five wrong attempts pause sign-in for 15 minutes.</small></div>`;
    return modalShell(creating ? "Create your sign-in password" : "Enter your sign-in password",
      creating
        ? `Set a password for ${maskPakistaniMobile(match.phone)} so nobody else can open your profile with only your number.`
        : `Enter the password for ${htmlesc(match.name)} · ${maskPakistaniMobile(match.phone)}.`,
      `<div class="form-grid">${fields}${message}${lockNotice}<div class="notice notice-info">Forgot the password? Ask the salon owner to reset it, then set a new one here.</div></div>`,
      `<button class="btn btn-secondary" data-action="close-modal">Close</button><button class="btn btn-soft" data-action="lookup-restart">Use another number</button><button class="btn btn-primary" data-action="submit-lookup-pin" data-customer-id="${match.id}" ${lockedMinutes ? "disabled" : ""}>${creating ? "Create password & continue" : "Continue"}</button>`);
  }
  const lookupIntro = RELEASE_MODE ? "Enter your complete Pakistani mobile number. We verify an exact match before showing your salon profile." : "Enter your complete Pakistani mobile number. An exact match is used for local QA; production signs in with the mobile number and password.";
  let result = `<div class="notice notice-info">${lookupIntro}</div>`;
  if (m.searched && !valid) {
    result = `<div class="notice notice-warning">Enter a complete Pakistani mobile number, for example 0300 123 4567.</div>`;
  } else if (m.searched && matches.length === 1) {
    const match = matches[0];
    result = `<div class="notice notice-success">Profile found. Confirm the masked number to continue.</div><div class="list"><button class="service-option" data-action="select-customer" data-customer-id="${match.id}"><span><strong>Continue as ${htmlesc(match.name)}</strong><small>${maskPakistaniMobile(match.phone)} · Salon profile</small></span><span class="service-price">Continue</span></button></div>`;
  } else if (m.searched && matches.length > 1) {
    // Duplicate phone data should never expose a list or allow an ambiguous sign-in.
    result = `<div class="notice notice-warning">This mobile is linked to more than one profile. Please ask the salon owner to correct the records.</div>`;
  } else if (m.searched) {
    result = `<div class="notice notice-warning">No profile found for this mobile.${ui.role === "customer" ? ` <button class="btn btn-soft btn-small" style="margin-top:10px" data-action="start-customer-signup" data-phone="${htmlesc(phone)}">Create profile</button>` : " Create a new customer record from Owner mode."}</div>`;
  }
  return modalShell("Find your salon profile", "Verified customer lookup by mobile number.", `<div class="form-grid"><div class="form-field"><label>Mobile number</label><input data-lookup-phone type="tel" inputmode="tel" autocomplete="tel" placeholder="03xx xxx xxxx" value="${htmlesc(phone)}"><small>Only an exact full mobile match returns a profile. Other customers, wallet balances and visit dates stay hidden.</small></div>${result}</div>`, `<button class="btn btn-secondary" data-action="close-modal">Close</button><button class="btn btn-primary" data-action="search-customer">Find profile</button>`);
}

function saveCustomer(id) {
  const modalContext = ui.modal || {}; const name = document.querySelector("[data-customer-name]")?.value.trim(); const phone = document.querySelector("[data-customer-phone]")?.value.trim(); const consent = !!document.querySelector("[data-customer-consent]")?.checked; const referralCode = !id ? document.querySelector("[data-customer-referral-code]")?.value.trim() : ""; const normalized = canonicalPakistaniPhone(phone);
  const ownerWasAuthenticated = ownerSessionActive();
  const editingOwnProfile = modalContext.signup || id === state.currentCustomerId;
  if (!editingOwnProfile && !requireOwnerSession()) return;
  if (!name || !phone || !isValidPakistaniMobile(normalized)) { toast("Enter a complete Pakistani mobile number, for example 0300 123 4567.", "error"); return; }
  // A customer creating their own profile chooses a sign-in password here. The
  // owner never sets it for someone else, and it is never stored as plain text.
  const newPin = modalContext.signup && !apiModeEnabled() ? String(document.querySelector("[data-customer-pin]")?.value || "").trim() : "";
  const newPinConfirmation = modalContext.signup && !apiModeEnabled() ? String(document.querySelector("[data-customer-pin-confirm]")?.value || "").trim() : "";
  if (modalContext.signup && !apiModeEnabled()) {
    if (!validSignInPin(newPin)) { toast(SIGN_IN_PASSWORD_RULE, "error"); return; }
    if (newPin !== newPinConfirmation) { toast("Both password entries must match.", "error"); return; }
  }
  if ((state.customers || []).some((c) => c.id !== id && samePakistaniMobile(c.phone, normalized))) { toast("That mobile number is already in the customer list.", "error"); return; }
  const displayPhone = formatPakistaniMobile(normalized);
  if (apiModeEnabled()) {
    // A customer session can edit the display name and consent, but the mobile
    // identity is fixed to the OTP-verified account. Owner create/edit uses the
    // privileged customer endpoint and may change the stored phone after the
    // server performs its duplicate and staff-account checks.
    const ownProfile = id && id === state.currentCustomerId && !ownerWasAuthenticated;
    if (ownProfile) {
      const existing = state.customers.find((item) => item.id === id);
      if (!existing || !samePakistaniMobile(existing.phone, normalized)) {
        toast("Your verified mobile number cannot be changed here. Sign out and verify the new number instead.", "error");
        return;
      }
      ui.apiBusy = true;
      render();
      (async () => {
        try {
          await window.AyanApi.updateProfile({ name, marketingConsent: consent }, apiSalonId());
          await hydrateRemoteCustomer();
          ui.apiBusy = false;
          ui.modal = null;
          toast("Profile updated on the salon server.");
        } catch (error) {
          ui.apiBusy = false;
          if (apiSessionExpired(error)) ui.modal = null;
          else if (ui.modal) ui.modal.error = apiErrorText(error, "Your profile could not be updated.");
        }
        render();
      })();
      return;
    }
    const path = id
      ? `/api/salons/${encodeURIComponent(apiSalonId())}/customers/${encodeURIComponent(id)}`
      : `/api/salons/${encodeURIComponent(apiSalonId())}/customers`;
    ui.apiBusy = true;
    render();
    (async () => {
      try {
        await window.AyanApi.request(path, {
          method: id ? "PUT" : "POST",
          body: JSON.stringify({ name, phone: normalized, marketingConsent: consent })
        });
        await hydrateRemoteOwner();
        ui.apiBusy = false;
        ui.modal = null;
        toast(referralCode
          ? "Customer saved. Referral codes are applied during verified customer signup."
          : "Customer saved on the salon server.");
      } catch (error) {
        ui.apiBusy = false;
        if (apiSessionExpired(error)) ui.modal = null;
        else if (ui.modal) ui.modal.error = apiErrorText(error, "The customer could not be saved.");
      }
      render();
    })();
    return;
  }
  let created = null;
  if (id) {
    const c = state.customers.find((x) => x.id === id); if (!c) { toast("Customer record not found.", "error"); return; }
    Object.assign(c, { name, phone: displayPhone }); setCustomerConsent(c, consent);
  } else {
    created = { id: uid("cus"), name, phone: displayPhone, consent: false, createdAt: iso(today), lastVisit: null, paidCredit: 0, bonusCredit: 0, firstDepositBonusClaimed: false, darkMode: false, birthday: null };
    state.customers.push(created); setCustomerConsent(created, consent);
    if (newPin) assignCustomerPin(created, newPin);
    if (referralCode) {
      const result = claimReferralForCustomer(created, referralCode);
      if (!result.ok) { state.customers = state.customers.filter((c) => c.id !== created.id); toast(result.message, "error"); return; }
    }
    if (modalContext.signup) {
      state.currentCustomerId = created.id;
      if (ownerWasAuthenticated) { ui.role = "owner"; ui.ownerScreen = "customers"; } else { ui.role = "customer"; ui.customerScreen = "home"; }
    }
  }
  saveState();
  if (created && modalContext.returnToBooking) {
    ui.modal = null;
    toast("Customer profile saved. Choose the walk-in service and slot.");
    openBooking({ customerId: created.id, source: "Walk-in" });
    return;
  }
  ui.modal = null; toast(referralCode ? "Customer profile saved and referral code applied." : "Customer profile saved"); render();
}
