# Testovací checklist — frontend

Prejdi si appku stránku po stránke a označuj si, čo funguje (`[x]`) a čo nie. Pri nefunkčnej veci si k riadku dopíš krátku poznámku (čo presne sa stalo), nech sa to dá potom rýchlo dohľadať v kóde.

Legenda: 🔒 = vidno/funguje len pre Super Admina, 👤 = ktokoľvek s manager rolou.

---

## Prierezové veci (naprieč celou appkou)

### Prihlásenie (`/login`)
- [X] Tlačidlo "Login with Discord" ťa pošle na Discord OAuth.
- [X] Po schválení na Discorde ťa appka vráti prihláseného, bez `?code=` v URL.
- [X] `/login?error=unauthorized_manager_required` → "Access denied: configured Manager Role or Super Admin access required."
- [X] `/login?error=access_revoked` → "Your dashboard access has been revoked..."
- [X] `/login?error=bot_not_ready` → "The bot just restarted..."
- [X] Neznámy `?error=xyz` → zobrazí sa aspoň generická chybová hláška s kódom.
- [X] Po zobrazení chyby sa `?error=` vyčistí z URL (needá sa pri refreshi znova).

### Výber servera (`/select-server` + prepínač hore v sidebare)
- [X] Žiadny dostupný server → "No eligible servers found...".
- [X] Presne 1 dostupný server → appka ho rovno vyberie a preskočí rovno na Dashboard (bez zobrazenia gridu).
- [X] Viac serverov → zobrazí sa grid kariet (ikona/fallback iniciály + názov), kliknutie prepne na daný server.
- [X] Predtým vybraný server, ktorý už nie je v zozname (napr. bot z neho odstránený) → grid sa znova ukáže.
- [X] Prepínač servera hore v sidebare — dropdown so zoznamom serverov, aktívny má zelenú bodku.
- [X] Prepnutie servera v prepínači prekreslí dáta na aktuálnej stránke bez plného reloadu.
- [X] Kliknutie mimo otvoreného prepínača ho zatvorí.
- [X] Bez vybraného servera každá dátová stránka (Dashboard, Codes, Users, Settings, Commands, Modules, Logs, Wipe, Semester) ukáže vlastný "no server selected" stav.

### Navigácia v sidebare
- [X] Bežné odkazy vidno vždy: Dashboard, Codes, Users Directory, Logs.
- [X] "Semester" odkaz sa objaví/zmizne podľa toho, či má guild nastavený semester log kanál.
- [X] 🔒 Sekcia Admin/Settings/Modules/Commands/Wipe sa NEZOBRAZUJE vôbec ako obyčajný manažér (nielen disabled — celkom chýba).
- [X] Aktívny odkaz je zvýraznený, aj pre pod-routy (`/modules/*`, `/semester/*`, `/commands/*`).
- [X] Mobilné menu (hamburger) sa otvára/zatvára, kliknutím na odkaz sa samo zatvorí, na mobile je dole aj Logout tlačidlo.

### Maintenance mode banner
- [X] Keď je zapnutý maintenance mode (cez Admin stránku), na každej stránke hore pribudne jantárový banner.
- [X] Banner zmizne po vypnutí maintenance (over aj po prenavigovaní, nie len po reloade).

### Session / refresh
- [X] Po vypršaní access tokenu appka potichu obnoví session (refresh) a dokončí pôvodnú akciu bez toho, aby ťa vyhodila.
- [X] Ak zlyhá aj refresh, presmeruje na `/login`.
- [X] Ak dostaneš 403 "Manager access required" (napr. ti niekto zrušil manager rolu počas session), appka ťa odhlási a presmeruje na `/login?error=access_revoked`.

### Toasty (notifikácie)
- [X] Toast sa objaví vpravo hore, sám zmizne po pár sekundách (progress bar dole).
- [X] Dá sa zavrieť ručne krížikom.
- [X] Viac toastov naraz sa correctne stackuje (skús to napr. na Auto Delete module).

---

## Dashboard (`/`) 👤

- [X] Bez vybraného servera: "No server selected." + link na výber servera.
- [X] Server Info karta: ikona/fallback, názov, počty (Members, Categories, Text Channels, Voice Channels, Roles).
- [X] "Copy Server ID" skutočne skopíruje ID servera do schránky.
- [X] Database Sync karta zobrazuje Last/Next sync (alebo "Not run yet").

---

## Verification Codes (`/codes`) 👤

- [X] Hore počítadlo "N active" / "N expired".
- [X] Vyhľadávanie funguje podľa Discord ID, AIS ID, emailu, Guild ID.
- [X] Filter podľa mesiaca — v zozname sú len mesiace, ktoré majú reálne dáta.
- [X] Filter Active/Expired.
- [X] "Reset" tlačidlo sa objaví len keď je aktívny filter/search a vyčistí všetko naraz.
- [X] Expirované riadky majú červený nádych, dátum expirácie je červený (aktívne zelený).
- [X] Prázdny výsledok → "No codes found."
- [X] Stránkovanie (30/strana) — prev/next/first/last, čísla strán, disabled na krajoch.
- [X] "Show all N" prepne na zobrazenie všetkého na jednej strane a späť.
- [X] Zmena filtra/search vráti na stranu 1.

---

## Verified Directory (`/users`) 👤

- [X] Počítadlo "{filtered} / {total}" pri aktívnom filtri.
- [X] Vyhľadávanie podľa Discord ID / AIS ID / email / Guild ID.
- [X] Filter podľa mesiaca (podľa `verified_at`).
- [X] Kliknutie na hlavičku "AIS ID" cyklí triedenie: bez triedenia → vzostupne → zostupne → bez triedenia (šípka sa mení).
- [X] "Reset" vyčistí search/mesiac/triedenie naraz.
- [X] Prázdny výsledok → "No users matched."
- [X] Stránkovanie a "Show all" rovnako ako pri Codes.

---

## Logs (`/access-logs`) 👤

- [X] Bez servera: "No server selected. Pick a server from the switcher above..."
- [X] 6 tabov: Dashboard, Logins, Warnings, Automod, Verification, Commands.
- [X] Prepnutie tabu vyresetuje search/filter/stránkovanie.
- [X] **Dashboard tab** — tabuľka zmien nastavení (Time/User/Action/Details), search funguje.
- [X] **Logins tab** — Time/User/IP/Action, "login" akcia sa zobrazí ako "Logged into dashboard".
- [X] **Warnings tab** — Time/Warning ID/User/Moderator/Reason; opakovaný priestupca dostane "{N}×" odznak.
- [X] **Automod tab** — status pill (success/failed/other), info "i" tlačidlo vysvetľuje čo presne sa loguje; funguje status filter.
- [X] **Verification tab** — rovnaká tabuľka ako Automod, len iné dáta.
- [X] **Commands tab** — Time/User/Channel/Command/Status/Duration, dôvod blokovania/chyba viditeľné, status filter má viac možností.
- [X] Search je na každom tabe (okrem loading/error stavu).
- [X] "Show all" / "Paginate" prepínač, pri väčšom počte strán sa čísla strán skracujú na "…".
- [X] Prázdny stav na každom tabe → "No {tab} found" s ikonkou.

---

## Ticket Transcript (`/tickets/:channelId?guildId=...`) 👤
Need to recheck: seems like it does not log all messages
- [X] Bez `channelId` alebo `guildId` v URL → "Missing channel or server id in the link."
- [X] Chyba pri načítaní → "Failed to load transcript." (alebo iná chyba zo servera).
- [X] Hlavička ukazuje Status (open/zatvorené), kto zatvoril a kedy.
- [X] Zoznam správ — autor, čas, text, prílohy (klikateľný odkaz, otvorí sa v novom tabe).
- [X] Prázdny transcript → "No messages recorded."

---

## Semester Management (`/semester`, `/semester/switch`, `/semester/setup`) 👤

- [X] Bez servera: "No server selected."
- [X] Prístup sa kontroluje — kým sa nenačíta: "Checking access…"
- [X] Chýba semester log kanál → jantárová hláška + link na Settings.
- [X] Bez oprávnenia (iný dôvod) → červená hláška "Access denied...".
- [X] "Running" odznak sa zobrazí, keď práve beží switch/setup.
- [X] Ľavý panel: "New Semester" vytvorí nový draft; existujúce configy v zozname (názov + počet kategórií/mapovaní).
- [X] Zmazanie configu — hover ukáže kôš, klik → inline potvrdenie Yes/No.
- [X] Prepínač Switch/Setup módu (disabled kým beží run).
- [X] **Switch mód** — editor: Allowed Transitions (zoznam From→To, pridávanie/mazanie), Name, Categories, "@everyone View Channel" prepínač, Role Mappings (pridanie/edit/mazanie, zmazaná Discord rola sa zobrazí ako preškrtnutá "deleted role").
- [X] **Setup mód** — center panel s editorom sa skryje, len run panel.
- [X] Info tlačidlo "How it works" otvorí modal s krokmi.
- [X] Run panel — Switch: From/To dropdown, "Run Switch" disabled kým nie sú vybrané rôzne hodnoty; potvrdzovací modal pred spustením.
- [X] Run panel — Setup: výber semestra, Show/Hide prepínač, checkbox na vyčistenie cleanup rolí, potvrdzovací modal.
- [X] Progress bar a live konzola so streamovanými logmi (farby podľa úrovne) počas behu.
- [X] "Resume unfinished steps" sa objaví len keď posledný run zlyhal/je nedokončený.
- [X] Konzola sa dá vyčistiť (X), po novom behu sa znova naplní.
- [X] Zavretie a znovuotvorenie stránky počas behu — run pokračuje a konzola sa dotiahne zo servera.

---

## 🔒 Modules (`/modules`)

- [X] Bez servera: "No server selected. Pick one." + link.
- [X] Dve karty: Hacked Account Trap, Auto Delete — každá s toggle na zapnutie/vypnutie a linkom na Settings.
- [X] Zapnutie Hacked Account Trap bez nastaveného trap kanála → chyba, toggle sa vráti späť.
- [X] Zapnutie Hacked Account Trap bez nastaveného Automod Log kanála → chyba (treba nastaviť v Settings → Log Channels).
- [X] Auto Delete toggle — zapnutie/vypnutie funguje, chyba sa ukáže inline pri zlyhaní.

### 🔒 Modules → Hacked Account Trap (`/modules/hacked-account-trap`)
- [X] Breadcrumb späť na Modules.
- [X] "Enable/Disable Module" tlačidlo v hlavičke (rovnaká kontrola trap/log kanála ako vyššie).
- [X] Trap channel picker (povinný).
- [X] Need to add, when you save settings, and you do not pick channel where it should save, then it wont throw: "Failed to save settings", but will actually show notification, that you have to pick one channel at least.
- [ ] Moderation action: Timeout/Kick/Ban; pri Timeout sa objaví stepper na minúty (1–40320).
- [X] Delete triggering message toggle.
- [X] Delete recent messages toggle → cleanup perióda (1–1440 min).
- [X] Ignore administrators toggle.
- [X] Exempt roles multi-select.
- [X] DM affected user toggle → textarea so správou (placeholder `{user}, {server}`).
- [X] Create incident channel toggle → vnorené nastavenia (názov kanála s `{user},{id}`, kategória, kategória po zatvorení, prístup pre postihnutého, správa v kanáli, tag rolí).
- [X] Moderation reason input.
- [X] "Save Module" — spinner → "Saved!" na 2s.
- [X] Skús hraničné hodnoty (1 a 40320 min pri timeoute, 1 a 1440 pri cleanupe) — mali by sa orezať n111a limity.
- [X] When it creates ticket, add behind name also tag. so e.g  .helter (@helter) so it actually tags also. But keep both versions.
- [X] You can not remove first number for some reason, you have to type at least two numbers, and then switch to the first one and replace it by removing it and adding something. As I think, this is not correct behaviour.
- [X] Also what I noticed is that bot is spamming too much when user retypes into the trap channel into the ticket if it was created. Always same logic. Once there is a channel that has been created specific for this trap and for this user, then it should maybe just tag that user in that specific channel.
- [X] Also would be good if it did pin control buttons
- [X] Also would be good if it contained commands not just UI buttons to handle ticket, so maybe /ticketclose, /ticketrecap, /ticketdelete, /ticketopen ? 

### 🔒 Modules → Auto Delete (`/modules/autodelete`)
- [X] Breadcrumb späť na Modules.
- [X] There is always 0 and you can not delete that 0. You have to first type at least two numbers, and then switch to the first one and replace it by removing and adding something else. As I think, this is not correct behaviour. Same case as in modules.
- [X] "New Channel" vytvorí nový config; existujúce v ľavom zozname (kanál + delay napr. "60s"/"5m"/"1h"/"instant").
- [X] Zmazanie configu — hover → kôš → inline Yes/No potvrdenie.
- [X] Editor: Channel select (už použité kanály disabled), "Delete after (seconds)" s približným prekladom ("≈ 5m"/"instant").
- [X] Ignore Rules: Ignore bots/pinned toggle, Ignore roles multi-select, Ignore users — pridávanie Discord ID (Enter/čiarka), neplatné ID (nie 17-20 číslic) sa nepridá.
- [X] Notify User toggle → In Channel/DM voľba; DM ukáže poznámku o zatvorených DMkách.
- [X] In Channel + Delete bot message → "Delete after" s minimom 3s (nižšie hodnoty sa orežú).
- [X] Message textarea s placeholderom `{channel} {server} {user}`.
- [X] Save bez vybraného kanála → toast "Select a channel first." (needá sa uložiť).
- [X] Úspešné uloženie → toast "Config saved successfully."; zlyhanie → toast s chybou.

---

## 🔒 Commands (`/commands`)

- [X] Bez servera: "No server selected. Pick one."
- [X] 3 taby: Moderation, Verification, Utility — správne príkazy v každom.
- [X] Každý príkaz má toggle enable/disable (optimistické prepnutie, vráti sa späť pri chybe).
- [X] Zámok pri príkaze, ktorý má nastavené obmedzenia (admin-only/role/kanály) + farebné chipy pod popisom.
- [X] "Authorization" modal — admin-only toggle, sekcie Require roles / Block roles / Allowed channels / Blocked channels (rozbaľovacie, s vyhľadávaním).
- [X] Kliknutie na už otvorenú/aktívnu sekciu v Authorization modali ju zbalí AJ vymaže výber — over toto správanie explicitne.
- [X] Save v modali — spinner → "Saved!" na 2s; Cancel/klik mimo modal zavrie bez uloženia.
- [X] "Settings" modal (len pri príkazoch, čo ho majú) — napr. Ephemeral response, Include bots, DM before wipe.
- [X] Kategóriové "Authorization" tlačidlo (hromadne pre celý tab) — ak majú príkazy rôzne nastavenia, formulár začne prázdny (explicitné prepísanie).
- [X] "Enable All"/"Disable All" pre aktívny tab.
- [X] "Sync Visibility" — spinner → "Synced!" na 3s.
- [X] Prepnutie servera resetne všetky stavy a znova načíta.

---

## 🔒 Settings (`/settings`)

- [X] Bez servera: "No server selected. Go back and pick one."
- [X] Verification & Roles: toggle Verification, Verified Role picker, Inactive Role picker — každé auto-save s vlastným Saving/Saved/Error indikátorom.
- [X] Warn Thresholds — zoznam "{N} warns → {Action}" s farbami podľa akcie, X na zmazanie (bez potvrdenia); pridanie (stepper + dropdown + Add); duplicitný limit → chyba.
- [X] Auto-Mentions — zoznam kanál→rola, toggle ON/OFF, X na zmazanie; pridanie (kanál + rola + Add, disabled kým oboje nevybrané).
- [X] Manager Roles — multi-select s vyhľadávaním, chipy; **explicitné "Save Manager Roles" tlačidlo** (NEukladá sa automaticky) — over, že zmena bez uloženia zmizne pri opustení stránky.
- [X] Log Channels — varovania pri rozbitej existujúcej konfigurácii; prepnutie kanála v slote migruje priradenia; checkbox na typ udalosti v jednom kanáli automaticky odškrtne rovnaký typ v inom slote (jedna udalosť = jeden kanál); pridanie nového slotu (len nepoužité kanály); explicitné "Save Log Channels".

---

## 🔒 Wipe (`/wipe`)

- [ ] Bez servera: "No server selected. Go to the dashboard..."
- [ ] "Checking access…" počas kontroly.
- [ ] Chýba wipe log kanál → jantárová hláška + link na Settings.
- [ ] Iný dôvod zamietnutia → červená hláška.
- [ ] Varovanie o deštruktívnej operácii je vždy viditeľné.
- [ ] Progress panel (Total/Checked/Inactive/Errors) + percento.
- [ ] "Remove all roles" checkbox — pri zaškrtnutí sa objaví "Keep roles" multi-picker.
- [ ] Potvrdzovací checkbox ("I understand...") — **"Start Wipe" je disabled, kým nie je zaškrtnutý** — over explicitne.
- [ ] Počas behu sa ovládacie prvky skryjú a zobrazí sa "Wipe in progress... do not close this page."
- [ ] Live konzola so stavom, farby podľa úrovne (error/warn/success/info), Clear tlačidlo.
- [ ] Odíď zo stránky počas behu a vráť sa — beh a konzola sa correctne dotiahnu.
- [ ] Prepnutie servera resetne celý stav Wipe stránky (aj zaškrtnutý confirm checkbox).

---

## 🔒 Admin (`/admin`)

- [X] Metriky: Uptime, Servers, Verified, Active Codes, Warnings, Memory.
- [X] Allowed Servers — pridanie ID: neplatný formát (nie 17-20 číslic) → chyba; duplicitné ID → chyba; platné nové ID sa pridá a uloží.
- [X] Zoznam pripojených guildov s "Allow"/"Allowed" tlačidlom.
- [X] Pokus odstrániť POSLEDNÝ povolený server → chyba "Keep at least one server allowed..." (needá sa odstrániť).
- [X] Guildy povolené, ale bot v nich nie je pripojený → samostatný riadok "Bot is not connected" s možnosťou zmazať.
- [X] Maintenance Mode — vypnutie je okamžité; **zapnutie vyžaduje potvrdzovací dialóg** (Cancel/Enable Maintenance).
- [X] Zrušenie dialógu (Cancel) → maintenance ostane vypnutý.
- [X] Super Admin panel je čisto na čítanie (žiadne pridávanie/mazanie z UI) — over, že sa naozaj nedá nič zmeniť.

---

## NotFound (neznáma URL)

- [X] Návšteva neexistujúcej cesty (napr. `/toto-neexistuje`) → "404 — Page not found" s tlačidlami "Go to Dashboard" a "Go back".
- [X] "Go back" ide na skutočne predchádzajúcu stránku (histórie), nie vždy na `/`.
- [X] Ako bežný manažér skús ručne zadať do URL 🔒 stránku (`/admin`, `/settings`, `/modules`, `/commands`, `/wipe`) — očakávané: ticho ťa to prehodí na `/` (Dashboard), NEukáže sa 404 ani obsah stránky.

---

## Rýchly prehľad prístupov (na overenie práv)

| Stránka | Prístup |
|---|---|
| `/login` | verejné |
| `/select-server` | ktokoľvek prihlásený |
| `/` Dashboard | 👤 manažér |
| `/codes` | 👤 manažér |
| `/users` | 👤 manažér |
| `/semester*` | 👤 manažér + vlastná kontrola prístupu |
| `/access-logs` | 👤 manažér |
| `/tickets/:channelId` | 👤 manažér |
| `/modules*` | 🔒 super admin |
| `/commands` | 🔒 super admin |
| `/wipe` | 🔒 super admin + vlastná kontrola prístupu |
| `/settings` | 🔒 super admin |
| `/admin` | 🔒 super admin |
