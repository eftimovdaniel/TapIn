import { api, auth } from "./api.js";

if (!auth.isLoggedIn()) location.href = "./index.html";

// ── Top bar ──────────────────────────────────────────────
const user = auth.user();
document.getElementById("userName").textContent = user?.fullName || "—";
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
  grad.addColorStop(0, "rgba(14,14,15,0.18)");
  grad.addColorStop(1, "rgba(14,14,15,0)");

  trendChart = new Chart(ctx, {
    type: "line",
    data: {
      labels,
      datasets: [{
        data: values,
        borderColor: "#0E0E0F",
        backgroundColor: grad,
        borderWidth: 2,
        tension: 0.35,
        pointRadius: 0,
        pointHoverRadius: 5,
        pointHoverBackgroundColor: "#0E0E0F",
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
        x: { ticks: { maxTicksLimit: 8, color: "#8A8A92" }, grid: { display: false } },
        y: {
          beginAtZero: true,
          ticks: { precision: 0, color: "#8A8A92" },
          grid: { color: "#EAEAEA", drawBorder: false },
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
        backgroundColor: "#0E0E0F",
        borderRadius: 6,
        barThickness: 18,
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
          ticks: { color: "#8A8A92", callback: (v) => v + "%" },
          grid: { color: "#EAEAEA", drawBorder: false },
        },
        y: {
          ticks: { color: "#3F3F44" },
          grid: { display: false },
        },
      },
    },
  });
}

document.getElementById("trendDays").addEventListener("change", (e) => {
  loadStatistics(parseInt(e.target.value, 10));
});

// ── Courses za filterot ──────────────────────────────────
async function loadCourses() {
  try {
    const courses = await api.listCourses();
    const sel = document.getElementById("filterCourse");
    courses.forEach((c) => {
      const o = document.createElement("option");
      o.value = c.id;
      o.textContent = `${c.code} · ${c.name}`;
      sel.appendChild(o);
    });
  } catch (e) {
    console.error(e);
  }
}

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
    tbody.innerHTML = `<tr><td colspan="5" class="px-3 py-8 text-center text-ink-40">Nema podatoci.</td></tr>`;
  } else {
    tbody.innerHTML = items
      .map(
        (a) => `
        <tr class="text-ink">
          <td class="px-3 py-3 font-mono text-xs text-ink-60 whitespace-nowrap">${escapeHtml(fmtDateTime(a.tappedAt))}</td>
          <td class="px-3 py-3 font-medium">${escapeHtml(a.studentName)}</td>
          <td class="px-3 py-3 font-mono text-xs text-ink-60">${escapeHtml(a.studentNumber || "—")}</td>
          <td class="px-3 py-3">${escapeHtml(a.courseName)}</td>
          <td class="px-3 py-3 text-ink-60">${escapeHtml(a.teacherName)}</td>
        </tr>`,
      )
      .join("");
  }

  const totalPages = Math.max(1, Math.ceil(tableState.total / tableState.size));
  pageInfo.textContent = `Strana ${tableState.page + 1} od ${totalPages} · vkupno ${fmtNum(tableState.total)}`;
  prevBtn.disabled = tableState.page <= 0;
  nextBtn.disabled = tableState.page + 1 >= totalPages;
}

async function loadAttendance() {
  tbody.innerHTML = `<tr><td colspan="5" class="px-3 py-8 text-center text-ink-40">Vchituvanje…</td></tr>`;
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
    tbody.innerHTML = `<tr><td colspan="5" class="px-3 py-8 text-center text-danger">${escapeHtml(e.message || "Greshka")}</td></tr>`;
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
      ["Vreme", "Student", "Broj", "Predmet", "Profesor"],
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
    alert("Neuspeshen izvoz: " + e.message);
  }
});

// ── Kick off ─────────────────────────────────────────────
loadStatistics(30);
loadCourses();
loadAttendance();

// Auto-refresh statistiki sekoja 30 sekundi (taa zhiva ekran)
setInterval(() => loadStatistics(parseInt(document.getElementById("trendDays").value, 10)), 30000);
