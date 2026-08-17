# Testovací checklist

Prejdi si appku stránku po stránke a označuj si, čo funguje (`[x]`) a čo nie. Pri nefunkčnej veci si k riadku dopíš krátku poznámku (čo presne sa stalo), nech sa to dá potom rýchlo dohľadať v kóde.

Legenda: 🔒 = vidno/funguje len pre Super Admina, 👤 = ktokoľvek s manager rolou.

---

# Časť 1 — Frontend

## Prierezové veci (naprieč celou appkou)

### Prihlásenie (`/login`)
- [X] Tlačidlo "Login with Discord" ťa pošle na Discord OAuth.
- [X] Po schválení na Discorde ťa appka vráti prihláseného, bez `?code=` v URL.
- [X] `/login?error=unauthorized_manager_required` → "Access denied: configured Manager Role or Super Admin access required."
- [X] `/login?error=access_revoked` → "Your dashboard access has been revoked..."
- [X] `/login?error=bot_not_ready` → "The bot just restarted..."
- [X] Neznámy `?error=xyz` → zobrazí sa aspoň generická chybová hláška s kódom.
- [X] Po zobrazení chyby sa `?error=` vyčistí z URL (nemusí sa pri refreshi znova).

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
- [X] Viac toastov naraz sa správne stackuje (skús to napr. na Auto Delete module).

---

## Dashboard (`/`) 👤
- [X] Bez vybraného servera: "No server selected." + link na výber servera.
- [X] Server Info karta: ikona/fallback, názov, počty (Members, Categories, Text Channels, Voice Channels, Roles).
- [X] "Copy Server ID" skutočne skopíruje ID servera do schránky.
- [x] Database Sync karta zobrazuje Last/Next sync (alebo "Not run yet").

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
- [ ] Discord log embedy (Warn issued/removed/cleared/threshold, Hacked Account Trap ban, Wipe inactive removed, User Verified, Manual Verification, Removed From Users Directory, Role Added Outside /verify) — over vizuálne v Discorde, že farby sú teraz zjednotené (zelená=success, červená=danger, jantárová=warning) a "User" pole má všade formát mention + username.
- [X] **Verification tab** — rovnaká tabuľka ako Automod, len iné dáta.
- [x] Log embed "Removed From Users Directory" (kick/ban/leave aj Verified rola odobraná) obsahuje AIS ID; pri odobratí roly je namiesto poľa "Role" pole "AIS ID".
- [X] **Commands tab** — Time/User/Channel/Command/Status/Duration, dôvod blokovania/chyba viditeľné, status filter má viac možností.
- [X] Search je na každom tabe (okrem loading/error stavu).
- [X] "Show all" / "Paginate" prepínač, pri väčšom počte strán sa čísla strán skracujú na "…".
- [X] Prázdny stav na každom tabe → "No {tab} found" s ikonkou.

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
- [ ] Log Channel picker (ľavý panel) — výber kanála treba potvrdiť "Save" tlačidlom, NEukladá sa hneď pri výbere (rovnako ako pri Wipe/Commands).
- [X] Run panel — Switch: From/To dropdown, "Run Switch" disabled kým nie sú vybrané rôzne hodnoty; potvrdzovací modal pred spustením.
- [ ] Bug fix over (Switch): rovnaký test ako pri Wipe — nastav semester log kanál, over že Switch beží; potom kanál v Settings odober a skús spustiť Switch znova → musí byť odmietnutý so správou o chýbajúcom log kanáli (predtým bežalo aj bez kanála, len ticho bez recap správy na konci).
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
Prepracované: modul teraz vždy len permanentne banuje (žiadny výber akcie), incident kanál/ticket systém je preč, cleanup sekcia nahradená Discord-natívnym "delete message history" pri bane, moderation reason už nie je editovateľný (vždy natvrdo "Hacked account trap triggered"), trigger message sa už nemaže.
- [X] Breadcrumb späť na Modules.
- [X] "Enable/Disable Module" tlačidlo v hlavičke (rovnaká kontrola trap/log kanála ako na Modules stránke).
- [X] Trap channel picker (povinný).
- [X] Uloženie settings bez vybraného kanála → jasná notifikácia "vyber aspoň jeden kanál" (NIE generické "Failed to save settings").
- [X] V sekcii Trigger je "bans the author permanently" vizuálne zvýraznené (bold/farba), nie len v obyčajnom texte.
- [X] Žiadny výber moderation akcie v UI — trigger vždy permanentne banuje.
- [X] Ignore administrators toggle.
- [X] Delete message history toggle → po zapnutí sa objaví dropdown s presne týmito možnosťami: Previous Hour, Previous 6 Hours, Previous 12 Hours, Previous 24 Hours, Previous 3 Days, Previous 7 Days.
- [X] Vyskúšaj trigger s Delete message history zapnutým aj vypnutým — over v Discorde (audit log / mazané správy v iných kanáloch), že Discord skutočne zmazal históriu na zvolenú dĺžku, keď je zapnuté, a nezmazal nič navyše, keď je vypnuté.
- [X] Exempt roles multi-select.
- [X] Trigger Message sekcia (Delete triggering message toggle) už neexistuje — over, že sa nikde nezobrazuje; trigger správa v trap kanáli ostáva nezmazaná.
- [X] Message Cleanup sekcia (cleanup perióda v minútach) už neexistuje — over, že sa nikde nezobrazuje.
- [X] DM affected user toggle → textarea so správou (placeholder `{user}, {server}`).
- [X] Incident Channel sekcia už neexistuje — over, že sa nikde nezobrazuje (žiadny incident kanál sa nevytvára, žiadne ticket ovládacie tlačidlá/príkazy).
- [X] Audit Log sekcia (Moderation reason input) už neexistuje — over, že sa nikde nezobrazuje; ban v Discord audit logu má vždy dôvod "Hacked account trap triggered".
- [X] "Save Module" — spinner → "Saved!" na 2s.

### 🔒 Modules → Auto Delete (`/modules/autodelete`)
- [X] Breadcrumb späť na Modules.
- [X] Číselné inputy (Delete after seconds, cleanup min) — zmazanie prvej číslice/nuly funguje normálne (known bug z minula, over či je opravený).
- [X] "New Channel" vytvorí nový config; existujúce v ľavom zozname (kanál + delay napr. "60s"/"5m"/"1h"/"instant").
- [X] Zmazanie configu — hover → kôš → inline Yes/No potvrdenie.
- [X] Editor: Channel select (už použité kanály disabled), "Delete after (seconds)" s približným prekladom ("≈ 5m"/"instant").
- [X] "Ignore bots" toggle už neexistuje — over, že sa nikde nezobrazuje; bot správy sa naďalej nikdy nemažú (natvrdo).
- [X] Ignore Rules: Ignore pinned toggle, Ignore roles multi-select, Ignore users — pridávanie Discord ID (Enter/čiarka), neplatné ID (nie 17-20 číslic) sa nepridá.
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
- [X] Kliknutie na už otvorenú/aktívnu sekciu v Authorization modali ju zbalí AJ vymaže výber.
- [X] Save v modali — spinner → "Saved!" na 2s; Cancel/klik mimo modal zavrie bez uloženia.
- [X] "Settings" modal (len pri príkazoch, čo ho majú) — napr. Ephemeral response, Include bots, DM before wipe.
- [X] Kategóriové "Authorization" tlačidlo (hromadne pre celý tab) — ak majú príkazy rôzne nastavenia, formulár začne prázdny (explicitné prepísanie).
- [X] "Enable All"/"Disable All" pre aktívny tab.
- [X] "Sync Visibility" — spinner → "Synced!" na 3s.
- [X] Prepnutie servera resetne všetky stavy a znova načíta.
- [X] "Log Channel" modal (na karte príkazu, čo loguje) — výber kanála sa NEuloží hneď; treba kliknúť "Save" (predtým bola len "Close", žiadne Save). "Save" je disabled kým sa nič nezmenilo. Zavretie bez uloženia zahodí výber.
- [ ] `/code` má teraz Settings modal (predtým nemal žiadny) — "Link channel" picker + textarea "Success message" s placeholdermi `{user}` `{server}` `{channel}` `{ais_id}`; prázdne pole správy = použije sa default "Úspešne overené! Vitaj.". `{channel}` NIE JE kanál, kde bol príkaz spustený — je to kanál vybraný v "Link channel" (napr. kanál na výber rolí), vykreslí sa ako klikateľný Discord channel-mention. Bez vybraného "Link channel" sa `{channel}` nahradí prázdnym reťazcom. Ulož vlastnú správu s placeholdermi aj Link channel, spusti `/verify` + `/code` v Discorde a over: meno, názov servera, klikateľný odkaz na zvolený kanál (klik ťa naň prenesie), AIS ID.

---

## 🔒 Settings (`/settings`)
- [X] Bez servera: "No server selected. Go back and pick one."
- [X] Verification & Roles: toggle Verification, Verified Role picker, Inactive Role picker — každé auto-save s vlastným Saving/Saved/Error indikátorom.
- [X] Warn Thresholds — zoznam "{N} warns → {Action}" s farbami podľa akcie, X na zmazanie (bez potvrdenia); pridanie (stepper + dropdown + Add); duplicitný limit → chyba.
- [X] Auto-Mentions — zoznam kanál→rola, toggle ON/OFF, X na zmazanie; pridanie (kanál + rola + Add, disabled kým oboje nevybrané).
- [X] Manager Roles — multi-select s vyhľadávaním, chipy; **explicitné "Save Manager Roles" tlačidlo** (NEukladá sa automaticky) — zmena bez uloženia zmizne pri opustení stránky.
- [X] Log Channels — varovania pri rozbitej existujúcej konfigurácii; prepnutie kanála v slote migruje priradenia; checkbox na typ udalosti v jednom kanáli automaticky odškrtne rovnaký typ v inom slote (jedna udalosť = jeden kanál); pridanie nového slotu (len nepoužité kanály); explicitné "Save Log Channels".

---

## 🔒 Wipe (`/wipe`)
- [X] Bez servera: "No server selected. Go to the dashboard..."
- [X] "Checking access…" počas kontroly.
- [X] Chýba wipe log kanál → jantárová hláška + link na Settings.
- [X] Log Channel picker (aj v jantárovej hláške, aj hore v ľavom paneli po povolení) — výber kanála treba potvrdiť "Save" tlačidlom, NEukladá sa hneď pri výbere.
- [X] Bug fix over: vyber log kanál a ulož (Save), over že Wipe stránka sa sprístupní. Potom v Settings kanál odober/zmaž a skús spustiť Wipe znova (aj priamym volaním, ak sa dá) — Wipe sa MUSÍ odmietnuť so správou o chýbajúcom log kanáli, nesmie prebehnúť len preto, že prístup bol povolený pri predchádzajúcom načítaní stránky.
- [X] Iný dôvod zamietnutia → červená hláška.
- [X] Varovanie o deštruktívnej operácii je vždy viditeľné.
- [X] Progress panel (Total/Checked/Inactive/Errors) + percento.
- [X] "Remove all roles" checkbox — pri zaškrtnutí sa objaví "Keep roles" multi-picker.
- [X] Potvrdzovací checkbox ("I understand...") — **"Start Wipe" je disabled, kým nie je zaškrtnutý**.
- [X] Počas behu sa ovládacie prvky skryjú a zobrazí sa "Wipe in progress... do not close this page."
- [X] Live konzola so stavom, farby podľa úrovne (error/warn/success/info), Clear tlačidlo.
- [X] Odíď zo stránky počas behu a vráť sa — beh a konzola sa správne dotiahnu.
- [X] Prepnutie servera resetne celý stav Wipe stránky (aj zaškrtnutý confirm checkbox).

---

## 🔒 Admin (`/admin`)
- [X] Metriky: Uptime, Servers, Verified, Active Codes, Warnings, Memory.
- [X] Allowed Servers — pridanie ID: neplatný formát (nie 17-20 číslic) → chyba; duplicitné ID → chyba; platné nové ID sa pridá a uloží.
- [X] Zoznam pripojených guildov s "Allow"/"Allowed" tlačidlom.
- [X] Pokus odstrániť POSLEDNÝ povolený server → chyba "Keep at least one server allowed..." (needá sa odstrániť).
- [X] Guildy povolené, ale bot v nich nie je pripojený → samostatný riadok "Bot is not connected" s možnosťou zmazať.
- [X] Maintenance Mode — vypnutie je okamžité; **zapnutie vyžaduje potvrdzovací dialóg** (Cancel/Enable Maintenance).
- [X] Zrušenie dialógu (Cancel) → maintenance ostane vypnutý.
- [X] Super Admin panel je čisto na čítanie (žiadne pridávanie/mazanie z UI).

---

## NotFound (neznáma URL)
- [X] Návšteva neexistujúcej cesty (napr. `/toto-neexistuje`) → "404 — Page not found" s tlačidlami "Go to Dashboard" a "Go back".
- [X] "Go back" ide na skutočne predchádzajúcu stránku (histórie), nie vždy na `/`.
- [X] Ako bežný manažér skús ručne zadať do URL 🔒 stránku (`/admin`, `/settings`, `/modules`, `/commands`, `/wipe`) → ticho ťa to prehodí na `/` (Dashboard), NEukáže sa 404 ani obsah stránky.

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
| `/modules*` | 🔒 super admin |
| `/commands` | 🔒 super admin |
| `/wipe` | 🔒 super admin + vlastná kontrola prístupu |
| `/settings` | 🔒 super admin |
| `/admin` | 🔒 super admin |

---

# Časť 2 — Backend (čo nepokrývajú unit testy)

Backend má rozsiahle unit-testové pokrytie (services, listeners, controllery), ale niektoré veci sa
z princípu nedajú overiť automatizovaným testom v sandboxe — buď potrebujú živé pripojenie (Discord,
LDAP server, SMTP), alebo reálny servlet request/AsyncListener lifecycle (SSE), alebo bežia len cez
`@Scheduled` na reálnom čase. Tieto over ručne, ideálne na testovacom (nie produkčnom) guilde.

## 1. Integračné testy (`*IntegrationTest.java`)
- [X] V sandboxe (Docker-in-Docker) sa nedajú spustiť — Testcontainers nevie nadviazať na Ryuk/Postgres. Over, že v GitHub Actions CI prebehnú zelené.
- [X] Ak CI nie je nastavené alebo nebeží, spusti `./mvnw test` lokálne mimo sandboxu, na stroji s funkčným Dockerom pre Testcontainers.

## 2. SSE live-push (Maintenance mode banner, Verification status)
- [X] Otvor dashboard v dvoch rôznych prehliadačoch/tabov naraz, prihlásený ako manažér na tom istom guilde.
- [X] V jednom tabe zapni Maintenance Mode cez `/admin` (🔒) → v druhom tabe sa banner objaví bez refreshu.
- [X] Vypni Maintenance Mode → banner v druhom tabe zmizne bez refreshu.
- [X] Na Settings → Verification toggle prepni enabled/disabled → v druhom otvorenom tabe sa stav prekreslí bez refreshu.
- [X] Zavri jeden tab (spadni SSE spojenie) → over, že server si neudržiava mŕtve spojenie donekonečna.

## 3. Reálne Discord slash príkazy (end-to-end cez skutočný bot)
- [X] `/info` — embed sa vykreslí správne v Discorde (farby, polia, thumbnail, footer s časom vytvorenia servera).
- [X] `/user @niekto` — embed s account info, roles, verified status, warns; skús aj na niekom, kto už nie je na serveri.
- [X] `/warn add`, `/warn list`, `/warn remove`, `/mywarns`, `/warn clearall` — celý flow, Discord embed/reply formátovanie.
- [X] `/verify` flow — AIS ID, doručenie emailu s kódom (bod 5), `/code` na potvrdenie, pridelenie Verified role.
- [X] Po úspešnom `/code` — log embed v Verification log kanáli má titulok "User Verified" a polia User, Channel, AIS ID (nielen User ako predtým).
- [ ] Role menu interakcie (klik na tlačidlo/select v role menu správe) — pridelenie/odobratie role.
- [X] Moderation auto-action z `/warn` (timeout/kick/ban pri dosiahnutí limitu) — bot má reálne oprávnenia, akcia prebehne v Discorde.

## 4. LDAP (reálny univerzitný server)
- [X] `/verify` s reálnym AIS ID — `LdapStudentDirectoryService` sa pripojí a spáruje študenta (meno, email, fakulta, account status) zo skutočného LDAP servera.
- [X] Rate-limit (`LdapRequestThrottle`) pri viacerých rýchlo za sebou idúcich `/verify` — mimo testing mode oddelené min. 1 sekundou.
- [ ] Rate-limit `/verify` sa už nespotrebuje na "lacných" zamietnutiach — spusti `/verify` opakovane s AIS ID, ktoré je už verifikované (alebo s vlastným už-verifikovaným Discord účtom): odpoveď musí byť vždy "already verified", NIKDY "Vyčerpal si limit..." (predtým sa po pár pokusoch limit vyčerpal, aj keď sa LDAP vôbec nevolal). Skutočný LDAP-backed pokus (platné, ešte neverifikované AIS ID) limit stále spotrebuje ako predtým.

## 5. Reálne odoslanie emailu
- [ ] `/verify` doručí email s kódom na skutočnú adresu (skontroluj inbox, nie len logy) — predmet "Discord - Overovací kód", formátovanie tela.

## 6. Auto Delete / Auto Mention — plný pipeline
- [X] Auto Delete s delay > 0 — správa sa naozaj zmaže po uplynutí nastaveného času.
- [X] Auto Delete "Notify User" (In Channel aj DM) — správa/DM sa reálne odošle, placeholdery `{channel}`/`{server}`/`{user}` nahradené správne.
- [X] Auto Delete "Delete bot message after" — bot správa sa naozaj zmaže po nastavenom čase.
- [X] Auto Mention — rola sa naozaj mentionne v správnom kanáli; pri auto-zmazaní mention message zmizne po nastavenom čase.

## 7. Hacked Account Trap — plný trigger flow
- [X] Skutočné triggernutie pasce → autor sa permanentne banuje, DM sa odošle (ak zapnuté), trigger správa sa zmaže (ak zapnuté).
- [X] S Delete message history zapnutým → over v Discorde, že sa naozaj zmazala história správ na zvolenú dĺžku (Discord to robí sám ako súčasť banu, dashboard nevracia počet).
- [X] Keďže ban je permanentný, autor sa po prvom triggeri už nemôže vrátiť a spustiť trigger znova (žiadna "repeat trigger" logika už neexistuje — bola viazaná na incident kanál/ticket, ktoré sú preč).

## 8. Scheduled joby (reálny čas)
- [ ] `DatabaseSyncService` — reštart bota → v logoch vidno, že sync prebehol a vo `verified_users` zmizli riadky pre ľudí bez Verified role.
- [ ] `ExpiredDataCleanupJob` — mazanie expirovaných verification codes/sessions po reálnom čase.

## 9. Wipe — celý beh (🔒, deštruktívne — len testovací guild!)
- [X] Reálny Wipe beh na testovacom guilde — progress panel, live konzola, finálny recap post do wipe log kanála.
- [X] "Remove all roles" + "Keep roles" kombinácia — vybrané roly naozaj ostanú.

## 10. Semester Setup/Switch — celý beh (🔒, testovací guild)
- [X] Reálny Setup aj Switch beh — vytvorenie/premenovanie kategórií a kanálov v Discorde, priradenie rolí podľa mapovania, recap post do semester log kanála.
- [X] Zavri stránku počas behu a vráť sa — beh pokračoval na serveri, UI sa správne dotiahne.
