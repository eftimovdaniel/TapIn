# TapIn — Web Dashboard

Profesorski web pregled na atendansa, statistiki i izvozi.

## Funkcii

- **Login** preku istiot backend (`POST /api/login`)
- **Stats kartichki** — denes / nedela / mesec / aktivni sesii / studenti / predmeti
- **Trend grafikon** — atendansa po denovi (7 / 14 / 30 / 90 dena), Chart.js
- **Po-predmet bar grafikon** — stapka na atendansa za sekoj predmet
- **Tabela so filteri** — predmet, datumski opseg, pretraga po ime / broj
- **Paginacija** — 20 redovi / strana
- **CSV izvoz** — do 5000 redovi po klikni
- **Avto-osvezhuvanje** na statistikite sekoja 30 sek

## Tehnologii

| Sloj | Tehnologija |
|------|-------------|
| HTML | Standard, semantichen |
| CSS | TailwindCSS (CDN) + mali custom klasi |
| Charts | Chart.js |
| API | fetch() so JWT Bearer token |
| State | localStorage (token + user) |

**Bez build step!** Site biblioteki se uchitaat preku CDN, taka shto otvarash so prost server.

## Brz start

Backend mora da raboti na `http://localhost:8080`.

### Vo eden terminal — backend:

```bash
cd backend && source .venv/bin/activate
uvicorn main:app --host 0.0.0.0 --port 8080
```

### Vo drug terminal — dashboard:

```bash
cd web-dashboard
python3 -m http.server 8000
```

Otvori vo browser: **http://localhost:8000**

## CORS

Backend e konfiguriran da prifaka pobaranja od:
- `http://localhost:5173` (Vite default)
- `http://localhost:3000` (CRA default)
- `http://localhost:8000` (`python -m http.server` default)
- I `127.0.0.1` ekvivalentite

Ako ti treba drugo (npr. produkcija), dodaj go vo `backend/.env`:
```
CORS_ORIGINS=...,https://moj-domen.mk
```

## Folder struktura

```
web-dashboard/
├── index.html              ← login stranica
├── dashboard.html          ← glavna stranica (po login)
├── README.md
└── assets/
    ├── style.css           ← custom Tailwind dopolnenije
    ├── api.js              ← fetch klient + auth (token vo localStorage)
    ├── login.js            ← login logika
    └── dashboard.js        ← stats + grafikoni + tabela + CSV
```

## Kako da go probash

1. Login so postojeechki teacher akaunt
   - Email: `prof.fastapi@finki.mk`
   - Lozinka: `test12345`
2. Vidi stats kartichki (mozhebi se 0 ako nemash atendansi)
3. Promeni period vo trend grafikonot (7 / 14 / 30 / 90 dena)
4. Filteri vo tabela: predmet, datumi, pretraga po ime
5. Klikni "Izvezi CSV" → zapishi `tapin-attendance-YYYY-MM-DD.csv`
