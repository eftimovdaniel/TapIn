/**
 * Tenok API klient — wrapper okolu fetch.
 * Site povici prefiksiraat so BASE_URL i automatski dodavaat Bearer token.
 */
export const BASE_URL = "http://localhost:8080";

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
    throw new ApiError(0, "Mrezhna greshka: " + (e.message || "?"));
  }

  if (resp.status === 401) {
    auth.clear();
    if (!location.pathname.endsWith("index.html") && location.pathname !== "/") {
      location.href = "./index.html";
    }
    throw new ApiError(401, "Sesija e isteche. Najavi se povtorno.");
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
  me: () => call("/api/auth/me"),

  // Statistics
  statistics: (days = 30) => call("/api/statistics", { query: { days } }),

  // Attendance
  listAttendance: ({ courseId, sessionId, from, to, page = 0, size = 20 } = {}) =>
    call("/api/attendance", { query: { courseId, sessionId, from, to, page, size } }),

  // Courses
  listCourses: () => call("/api/courses"),
};

export { ApiError };
