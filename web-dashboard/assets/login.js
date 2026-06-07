import { api, auth } from "./api.js";
import { initPasswordToggles } from "./password-toggle.js";

initPasswordToggles();

// Ako ima token, proveri dali e validen — inaku izbrishi go
if (auth.isLoggedIn()) {
  api.me()
    .then(() => { location.href = "./dashboard.html"; })
    .catch(() => { auth.clear(); });
}

const form = document.getElementById("loginForm");
const emailEl = document.getElementById("email");
const passwordEl = document.getElementById("password");
const errorEl = document.getElementById("error");
const submitEl = document.getElementById("submit");
const labelEl = document.getElementById("submitLabel");
const spinnerEl = document.getElementById("submitSpinner");

function showError(msg) {
  errorEl.textContent = msg;
  errorEl.classList.remove("hidden");
}
function clearError() {
  errorEl.textContent = "";
  errorEl.classList.add("hidden");
}
function setBusy(busy) {
  submitEl.disabled = busy;
  emailEl.disabled = busy;
  passwordEl.disabled = busy;
  labelEl.classList.toggle("hidden", busy);
  spinnerEl.classList.toggle("hidden", !busy);
}

form.addEventListener("submit", async (e) => {
  e.preventDefault();
  clearError();
  setBusy(true);
  try {
    const resp = await api.login(emailEl.value.trim(), passwordEl.value);
    if (!resp.user || !["TEACHER", "ADMIN"].includes(resp.user.role)) {
      showError("Оваа страница е само за професори.");
      return;
    }
    auth.saveSession(resp.token, resp.user);
    location.href = "./dashboard.html";
  } catch (err) {
    showError(err.message || "Неуспешна најава");
  } finally {
    setBusy(false);
  }
});
