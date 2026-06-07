import { api, auth } from "./api.js";
import { initPasswordToggles } from "./password-toggle.js";

initPasswordToggles();

if (auth.isLoggedIn()) {
  location.href = "./dashboard.html";
}

const form = document.getElementById("registerForm");
const fullNameEl = document.getElementById("fullName");
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
  fullNameEl.disabled = busy;
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
    const resp = await api.register({
      fullName: fullNameEl.value.trim(),
      email: emailEl.value.trim(),
      password: passwordEl.value,
      role: "TEACHER",
    });
    auth.saveSession(resp.token, resp.user);
    location.href = "./dashboard.html";
  } catch (err) {
    showError(err.message || "Неуспешна регистрација");
  } finally {
    setBusy(false);
  }
});
