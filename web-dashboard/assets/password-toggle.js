/** Toggle visibility на password полиња (иконка closed-eye / eye). */
const ICON_HIDDEN =
  "https://img.icons8.com/ios/50/closed-eye.png";
const ICON_VISIBLE =
  "https://img.icons8.com/ios/50/eye.png";

export function initPasswordToggles() {
  document.querySelectorAll("[data-password-toggle]").forEach((btn) => {
    const inputId = btn.getAttribute("aria-controls");
    const input = inputId ? document.getElementById(inputId) : null;
    if (!input) return;

    const img = btn.querySelector("img");
    if (!img) return;

    btn.addEventListener("click", () => {
      const show = input.type === "password";
      input.type = show ? "text" : "password";
      img.src = show ? ICON_VISIBLE : ICON_HIDDEN;
      btn.setAttribute(
        "aria-label",
        show ? "Сокриј лозинка" : "Покажи лозинка",
      );
    });
  });
}
