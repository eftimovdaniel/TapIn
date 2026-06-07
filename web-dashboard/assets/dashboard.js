import { api, auth } from "./api.js";

if (!auth.isLoggedIn()) location.href = "./index.html";

// ── Top bar + role-based scope (RBAC) ────────────────────
const user = auth.user();
const isAdmin = user?.role === "ADMIN";
document.getElementById("userName").textContent = user?.fullName || "—";

// Prikazi ja ulogata + opsegot na podatoci (admin gleda se, profesor samo svoi)
const roleEl = document.getElementById("userRole");
if (roleEl) {
  roleEl.textContent = isAdmin
    ? "Администратор · сите предмети"
    : "Професор · мои предмети";
}

// Page subtitle reflektira RBAC opseg
const subtitleEl = document.getElementById("pageSubtitle");
if (subtitleEl) {
  subtitleEl.textContent = isAdmin
    ? "Статистики и евиденција за сите предмети и професори."
    : "Статистики и евиденција за твоите предмети.";
}

// Kolonata "Професор" vo tabelata e relevantna samo za admin (profesorot e sekogash toj).
// Klasata na <body> e otporna na re-render na tabelata (CSS skriva .col-teacher).
if (!isAdmin) {
  document.body.classList.add("role-teacher");
}

document.getElementById("logout").addEventListener("click", () => {
  auth.clear();
  location.href = "./index.html";
});

// ── Helpers ──────────────────────────────────────────────
const fmtNum = (n) => (typeof n === "number" ? n.toLocaleString("mk-MK") : "—");
const fmtPct = (p) => (Number.isFinite(p) ? Math.round(p * 100) + "%" : "—");

function fmtDateTime(iso) {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString("mk-MK", {
    day: "2-digit", month: "2-digit", year: "numeric",
    hour: "2-digit", minute: "2-digit",
  });
}

function fmtDateOnly(iso) {
  const d = new Date(iso);
  return Number.isNaN(d.getTime())
    ? iso
    : d.toLocaleDateString("mk-MK", { day: "2-digit", month: "short" });
}

// ── Stats cards + charts ─────────────────────────────────
let trendChart = null;
let courseChart = null;

async function loadStatistics(days = 30) {
  try {
    const data = await api.statistics(days);
    const o = data.overview;
    document.getElementById("statToday").textContent = fmtNum(o.todayCount);
    document.getElementById("statWeek").textContent = fmtNum(o.weekCount);
    document.getElementById("statMonth").textContent = fmtNum(o.monthCount);
    document.getElementById("statActive").textContent = fmtNum(o.activeSessions);
    document.getElementById("statStudents").textContent = fmtNum(o.totalStudents);
    document.getElementById("statCourses").textContent = fmtNum(o.totalCourses);

    drawTrend(data.trend, days);
    drawPerCourse(data.perCourse);
  } catch (e) {
    console.error(e);
  }
}

function drawTrend(points, days) {
  // Popolni so 0 za site denovi vo opsegot za rovniot grafikon
  const map = new Map((points || []).map((p) => [p.date, p.count]));
  const labels = [];
  const values = [];
  const today = new Date();
  for (let i = days - 1; i >= 0; i--) {
    const d = new Date(today.getFullYear(), today.getMonth(), today.getDate() - i);
    const iso = d.toISOString().slice(0, 10);
    labels.push(fmtDateOnly(iso));
    values.push(map.get(iso) || 0);
  }

  if (trendChart) trendChart.destroy();
  const ctx = document.getElementById("trendChart").getContext("2d");
  const grad = ctx.createLinearGradient(0, 0, 0, 240);
  grad.addColorStop(0, "rgba(10,10,10,0.12)");
  grad.addColorStop(1, "rgba(10,10,10,0)");

  trendChart = new Chart(ctx, {
    type: "line",
    data: {
      labels,
      datasets: [{
        data: values,
        borderColor: "#0A0A0A",
        backgroundColor: grad,
        borderWidth: 1.5,
        tension: 0.4,
        pointRadius: 0,
        pointHoverRadius: 4,
        pointHoverBackgroundColor: "#0A0A0A",
        pointHoverBorderColor: "#fff",
        pointHoverBorderWidth: 2,
        fill: true,
      }],
    },
    options: {
      maintainAspectRatio: false,
      plugins: { legend: { display: false }, tooltip: { mode: "index", intersect: false } },
      interaction: { mode: "index", intersect: false },
      scales: {
        x: { ticks: { maxTicksLimit: 8, color: "#737373", font: { family: 'Inter, sans-serif', size: 11 } }, grid: { display: false } },
        y: {
          beginAtZero: true,
          ticks: { precision: 0, color: "#737373", font: { family: 'Inter, sans-serif', size: 11 } },
          grid: { color: "#F5F5F5", drawBorder: false },
        },
      },
    },
  });
}

function drawPerCourse(items) {
  const labels = (items || []).map((c) => c.courseName);
  const values = (items || []).map((c) => Math.round(c.rate * 100));

  if (courseChart) courseChart.destroy();
  const ctx = document.getElementById("courseChart").getContext("2d");
  courseChart = new Chart(ctx, {
    type: "bar",
    data: {
      labels,
      datasets: [{
        data: values,
        backgroundColor: "#0A0A0A",
        borderRadius: 4,
        barThickness: 14,
      }],
    },
    options: {
      indexAxis: "y",
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: { callbacks: { label: (ctx) => ctx.parsed.x + "%" } },
      },
      scales: {
        x: {
          beginAtZero: true, max: 100,
          ticks: { color: "#737373", font: { family: 'Inter, sans-serif', size: 11 }, callback: (v) => v + "%" },
          grid: { color: "#F5F5F5", drawBorder: false },
        },
        y: {
          ticks: { color: "#404040", font: { family: 'Inter, sans-serif', size: 12 } },
          grid: { display: false },
        },
      },
    },
  });
}

document.getElementById("trendDays").addEventListener("change", (e) => {
  loadStatistics(parseInt(e.target.value, 10));
});

// ── Courses: filter dropdowns + tabela "Мои предмети" ────
const courseTbody = document.getElementById("courseTbody");

function fillCourseDropdown(sel, courses) {
  if (!sel) return;
  const current = sel.value;
  sel.innerHTML = `<option value="">Сите предмети</option>`;
  courses.forEach((c) => {
    const o = document.createElement("option");
    o.value = c.id;
    o.textContent = `${c.code} · ${c.name}`;
    sel.appendChild(o);
  });
  sel.value = current;
}

function renderCourseTable(courses) {
  if (!courseTbody) return;
  if (!courses || courses.length === 0) {
    courseTbody.innerHTML = `<tr><td colspan="3" class="px-3 py-8 text-center text-ink-40">Сè уште немаш предмети. Додај го првиот погоре.</td></tr>`;
    return;
  }
  courseTbody.innerHTML = courses
    .map(
      (c) => `
        <tr class="text-ink">
          <td class="px-3 py-3 font-mono text-xs text-ink-60">${escapeHtml(c.code)}</td>
          <td class="px-3 py-3 font-medium">${escapeHtml(c.name)}</td>
          <td class="col-teacher px-3 py-3 text-ink-60">${escapeHtml(c.teacherName)}</td>
        </tr>`,
    )
    .join("");
}

async function loadCourses() {
  try {
    const courses = await api.listCourses();
    fillCourseDropdown(document.getElementById("filterCourse"), courses);
    fillCourseDropdown(studentCourseFilter, courses);
    renderCourseTable(courses);
  } catch (e) {
    console.error(e);
    if (courseTbody) {
      courseTbody.innerHTML = `<tr><td colspan="3" class="px-3 py-8 text-center text-danger">${escapeHtml(e.message || "Грешка")}</td></tr>`;
    }
  }
}

// ── Креирање нов предмет ─────────────────────────────────
const courseForm = document.getElementById("courseForm");
const courseCodeEl = document.getElementById("courseCode");
const courseNameEl = document.getElementById("courseName");
const courseSubmitEl = document.getElementById("courseSubmit");
const courseErrorEl = document.getElementById("courseError");

courseForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  courseErrorEl.classList.add("hidden");
  const code = courseCodeEl.value.trim();
  const name = courseNameEl.value.trim();
  if (!code || !name) return;

  courseSubmitEl.disabled = true;
  try {
    await api.createCourse({ code, name });
    courseCodeEl.value = "";
    courseNameEl.value = "";
    await loadCourses();
  } catch (err) {
    courseErrorEl.textContent = err.message || "Неуспешно креирање предмет";
    courseErrorEl.classList.remove("hidden");
  } finally {
    courseSubmitEl.disabled = false;
  }
});

// ── Tabela na atendansi so paginacija + filteri ──────────
const tbody = document.getElementById("tbody");
const pageInfo = document.getElementById("pageInfo");
const prevBtn = document.getElementById("prevPage");
const nextBtn = document.getElementById("nextPage");

const tableState = {
  page: 0,
  size: 20,
  total: 0,
  items: [],
  search: "",
};

function readFilters() {
  return {
    courseId: document.getElementById("filterCourse").value || null,
    from: document.getElementById("filterFrom").value
      ? document.getElementById("filterFrom").value + "T00:00:00"
      : null,
    to: document.getElementById("filterTo").value
      ? document.getElementById("filterTo").value + "T23:59:59"
      : null,
  };
}

function escapeHtml(s) {
  if (s == null) return "";
  return String(s)
    .replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;").replaceAll("'", "&#39;");
}

function renderTable() {
  const q = tableState.search.trim().toLowerCase();
  const items = q
    ? tableState.items.filter(
        (a) =>
          (a.studentName || "").toLowerCase().includes(q) ||
          (a.studentNumber || "").toLowerCase().includes(q),
      )
    : tableState.items;

  if (items.length === 0) {
    tbody.innerHTML = `<tr><td colspan="5" class="px-3 py-8 text-center text-ink-40">Нема податоци.</td></tr>`;
  } else {
    tbody.innerHTML = items
      .map(
        (a) => `
        <tr class="text-ink">
          <td class="px-3 py-3 font-mono text-xs text-ink-60 whitespace-nowrap">${escapeHtml(fmtDateTime(a.tappedAt))}</td>
          <td class="px-3 py-3 font-medium">${escapeHtml(a.studentName)}</td>
          <td class="px-3 py-3 font-mono text-xs text-ink-60">${escapeHtml(a.studentNumber || "—")}</td>
          <td class="px-3 py-3">${escapeHtml(a.courseName)}</td>
          <td class="col-teacher px-3 py-3 text-ink-60">${escapeHtml(a.teacherName)}</td>
        </tr>`,
      )
      .join("");
  }

  const totalPages = Math.max(1, Math.ceil(tableState.total / tableState.size));
  pageInfo.textContent = `Страна ${tableState.page + 1} од ${totalPages} · вкупно ${fmtNum(tableState.total)}`;
  prevBtn.disabled = tableState.page <= 0;
  nextBtn.disabled = tableState.page + 1 >= totalPages;
}

async function loadAttendance() {
  tbody.innerHTML = `<tr><td colspan="5" class="px-3 py-8 text-center text-ink-40">Вчитување…</td></tr>`;
  try {
    const f = readFilters();
    const data = await api.listAttendance({
      courseId: f.courseId,
      from: f.from,
      to: f.to,
      page: tableState.page,
      size: tableState.size,
    });
    tableState.total = data.totalElements ?? 0;
    tableState.items = data.items ?? [];
    renderTable();
  } catch (e) {
    tbody.innerHTML = `<tr><td colspan="5" class="px-3 py-8 text-center text-danger">${escapeHtml(e.message || "Грешка")}</td></tr>`;
  }
}

document.getElementById("filterCourse").addEventListener("change", () => {
  tableState.page = 0;
  loadAttendance();
});
document.getElementById("filterFrom").addEventListener("change", () => {
  tableState.page = 0;
  loadAttendance();
});
document.getElementById("filterTo").addEventListener("change", () => {
  tableState.page = 0;
  loadAttendance();
});
document.getElementById("filterSearch").addEventListener("input", (e) => {
  tableState.search = e.target.value;
  renderTable();
});
prevBtn.addEventListener("click", () => {
  if (tableState.page > 0) {
    tableState.page--;
    loadAttendance();
  }
});
nextBtn.addEventListener("click", () => {
  tableState.page++;
  loadAttendance();
});

// ── CSV export ───────────────────────────────────────────
document.getElementById("exportCsv").addEventListener("click", async () => {
  // Ke iznesem do 5000 redovi (dovolno za prosechen semester)
  try {
    const f = readFilters();
    const all = await api.listAttendance({
      courseId: f.courseId,
      from: f.from,
      to: f.to,
      page: 0,
      size: 5000,
    });

    const rows = [
      ["Време", "Студент", "Број", "Предмет", "Професор"],
      ...(all.items || []).map((a) => [
        fmtDateTime(a.tappedAt),
        a.studentName,
        a.studentNumber || "",
        a.courseName,
        a.teacherName,
      ]),
    ];

    const csv = rows
      .map((row) =>
        row
          .map((cell) => {
            const s = String(cell ?? "");
            return /[",\n;]/.test(s) ? `"${s.replaceAll('"', '""')}"` : s;
          })
          .join(","),
      )
      .join("\n");

    const blob = new Blob(["\uFEFF" + csv], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `tapin-attendance-${new Date().toISOString().slice(0, 10)}.csv`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  } catch (e) {
    alert("Неуспешен извоз: " + e.message);
  }
});

// ── Per-student attendance rate tablica ──────────────────
const studentTbody = document.getElementById("studentTbody");
const studentCourseFilter = document.getElementById("studentCourseFilter");

async function loadPerStudent() {
  studentTbody.innerHTML = `<tr><td colspan="5" class="px-3 py-8 text-center text-ink-40">Вчитување…</td></tr>`;
  try {
    const courseId = studentCourseFilter.value || undefined;
    const rows = await api.perStudent(courseId);
    if (!rows || rows.length === 0) {
      studentTbody.innerHTML = `<tr><td colspan="5" class="px-3 py-8 text-center text-ink-40">Нема податоци.</td></tr>`;
      return;
    }
    studentTbody.innerHTML = rows
      .map((r) => {
        const pct = Math.round((r.rate || 0) * 100);
        const barColor = pct >= 75 ? "bg-success" : pct >= 50 ? "bg-ink-60" : "bg-danger";
        return `
          <tr class="text-ink">
            <td class="px-3 py-3 font-medium">${escapeHtml(r.studentName)}</td>
            <td class="px-3 py-3 font-mono text-xs text-ink-60">${escapeHtml(r.studentNumber || "—")}</td>
            <td class="px-3 py-3 text-right font-mono text-xs">${fmtNum(r.attended)}</td>
            <td class="px-3 py-3 text-right font-mono text-xs text-ink-60">${fmtNum(r.totalSessions)}</td>
            <td class="px-3 py-3 min-w-[140px]">
              <div class="flex items-center gap-2">
                <div class="h-1.5 flex-1 rounded-full bg-ink-10 overflow-hidden">
                  <div class="h-full ${barColor}" style="width:${pct}%"></div>
                </div>
                <span class="font-mono text-xs tabular-nums text-ink-60 min-w-[36px] text-right">${pct}%</span>
              </div>
            </td>
          </tr>`;
      })
      .join("");
  } catch (e) {
    studentTbody.innerHTML = `<tr><td colspan="5" class="px-3 py-8 text-center text-danger">${escapeHtml(e.message || "Грешка")}</td></tr>`;
  }
}

studentCourseFilter.addEventListener("change", loadPerStudent);

// ── Скорешни присуства (последни 5) ──────────────────────
const recentList = document.getElementById("recentList");

async function loadRecent() {
  try {
    const data = await api.listAttendance({ page: 0, size: 5 });
    const items = data.items || [];
    if (items.length === 0) {
      recentList.innerHTML = `<li class="py-3 text-center text-ink-40">Сè уште нема присуства.</li>`;
      return;
    }
    recentList.innerHTML = items
      .map(
        (a) => `
        <li class="flex items-center justify-between gap-3 py-3">
          <div class="min-w-0">
            <div class="truncate font-medium text-ink">${escapeHtml(a.studentName)}</div>
            <div class="truncate text-xs text-ink-40">${escapeHtml(a.courseName)}</div>
          </div>
          <div class="shrink-0 font-mono text-xs text-ink-60">${escapeHtml(fmtDateTime(a.tappedAt))}</div>
        </li>`,
      )
      .join("");
  } catch (e) {
    recentList.innerHTML = `<li class="py-3 text-center text-danger">${escapeHtml(e.message || "Грешка")}</li>`;
  }
}

// ── Kick off ─────────────────────────────────────────────
async function bootstrap() {
  // loadCourses gi polni i dvata dropdown-a (filter + per-student) i tabelata
  await loadCourses();

  loadStatistics(30);
  loadAttendance();
  loadPerStudent();
  loadRecent();
}
bootstrap();

// ── Live polling — auto-refresh статистики + tabeли ──────
let lastTotal = 0;
async function liveRefresh() {
  loadStatistics(parseInt(document.getElementById("trendDays").value, 10));
  loadPerStudent();
  // Detekcija на nov tap → osveжи tabela so vizuelen pulse
  try {
    const head = await api.listAttendance({ page: 0, size: 1 });
    const total = head.totalElements || 0;
    if (total > lastTotal && lastTotal > 0) {
      // ima nov zapis — osveжи + indikatorот
      await loadAttendance();
      loadRecent();
      pulseFirstRow();
    }
    lastTotal = total;
  } catch { /* offline ok */ }
}
setInterval(liveRefresh, 15000);

function pulseFirstRow() {
  const first = tbody.querySelector("tr");
  if (!first) return;
  first.classList.add("row-pulse");
  setTimeout(() => first.classList.remove("row-pulse"), 2200);
}
