/**
 * Tenok API klient — wrapper okolu fetch.
 * Site povici prefiksiraat so BASE_URL i automatski dodavaat Bearer token.
 *
 * BASE_URL prioritet:
 *   1. window.__TAPIN_API_BASE__   (runtime override, npr. preku <script>)
 *   2. "" — koga e nginx Docker (port 80) — proxy-ja /api/*
 *   3. http://<host>:8080          (dev: dashboard na :8000, backend na :8080)
 *      Avtomatski koristi ist host kako toj na browser-ot, taka da raboti
 *      i preku LAN IP (npr. http://192.168.0.106:8000 → backend na .106:8080)
 */
function detectBaseUrl() {
  if (typeof window !== "undefined" && window.__TAPIN_API_BASE__) {
    return window.__TAPIN_API_BASE__;
  }
  if (typeof location === "undefined") return "http://localhost:8080";

  // Same-origin slucai (nema CORS / Safari ITP problemi):
  //   - Docker / nginx (port 80 / 443)
  //   - FastAPI servisot direktno (port 8080) — sega frontend i backend zaedno
  if (
    location.port === "" ||
    location.port === "80" ||
    location.port === "443" ||
    location.port === "8080"
  ) {
    return "";
  }

  // Dev: dashboard na bilo koj drug port (5173, 3000…), backend e na :8080
  const proto = location.protocol === "https:" ? "https:" : "http:";
  return `${proto}//${location.hostname}:8080`;
}

export const BASE_URL = detectBaseUrl();
if (typeof console !== "undefined") {
  console.log("[TapIn] API BASE_URL =", BASE_URL || "(same-origin)");
}

const TOKEN_KEY = "tapin.token";
const USER_KEY = "tapin.user";

export const auth = {
  saveSession(token, user) {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(USER_KEY, JSON.stringify(user));
  },
  token() {
    return localStorage.getItem(TOKEN_KEY);
  },
  user() {
    try {
      return JSON.parse(localStorage.getItem(USER_KEY) || "null");
    } catch {
      return null;
    }
  },
  clear() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  },
  isLoggedIn() {
    return !!this.token();
  },
};

class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

async function call(path, { method = "GET", body, query } = {}) {
  let url = BASE_URL + path;
  if (query) {
    const qs = new URLSearchParams();
    for (const [k, v] of Object.entries(query)) {
      if (v !== undefined && v !== null && v !== "") qs.append(k, v);
    }
    const s = qs.toString();
    if (s) url += "?" + s;
  }

  const headers = { "Content-Type": "application/json" };
  const token = auth.token();
  if (token) headers["Authorization"] = "Bearer " + token;

  let resp;
  try {
    resp = await fetch(url, {
      method,
      headers,
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch (e) {
    throw new ApiError(0, "Мрежна грешка: " + (e.message || "?"));
  }

  if (resp.status === 401) {
    let msg = "Сесијата истече. Најави се повторно.";
    try {
      const j = await resp.json();
      msg = j.detail || j.message || msg;
    } catch {
      /* ignore */
    }

    const isLoginAttempt =
      path === "/api/login" ||
      path === "/api/register" ||
      path.startsWith("/api/auth/login") ||
      path.startsWith("/api/auth/register");

    if (!isLoginAttempt) {
      auth.clear();
      if (!location.pathname.endsWith("index.html") && location.pathname !== "/") {
        location.href = "./index.html";
      }
      throw new ApiError(401, "Сесијата истече. Најави се повторно.");
    }

    throw new ApiError(401, msg);
  }

  if (!resp.ok) {
    let msg = resp.statusText;
    try {
      const j = await resp.json();
      msg = j.detail || j.message || j.title || msg;
    } catch {
      /* ignore */
    }
    throw new ApiError(resp.status, msg);
  }

  if (resp.status === 204) return null;
  const ct = resp.headers.get("content-type") || "";
  return ct.includes("application/json") ? resp.json() : resp.text();
}

export const api = {
  // Auth
  login: (email, password) => call("/api/login", { method: "POST", body: { email, password } }),
  register: (body) => call("/api/register", { method: "POST", body }),
  me: () => call("/api/auth/me"),

  // Statistics
  statistics: (days = 30) => call("/api/statistics", { query: { days } }),
  perStudent: (courseId) => call("/api/statistics/per-student", { query: { courseId } }),

  // Attendance
  listAttendance: ({ courseId, sessionId, from, to, page = 0, size = 20 } = {}) =>
    call("/api/attendance", { query: { courseId, sessionId, from, to, page, size } }),

  // Courses
  listCourses: () => call("/api/courses"),
};

export { ApiError };
