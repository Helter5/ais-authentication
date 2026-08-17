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
- [X] **Verification tab** — rovnaká tabuľka ako Automod, len iné dáta.
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
- [ ] Zmazanie configu — hover → kôš → inline Yes/No potvrdenie.
- [X] Editor: Channel select (už použité kanály disabled), "Delete after (seconds)" s približným prekladom ("≈ 5m"/"instant").
- [ ] Ignore Rules: Ignore bots/pinned toggle, Ignore roles multi-select, Ignore users — pridávanie Discord ID (Enter/čiarka), neplatné ID (nie 17-20 číslic) sa nepridá.
- [X] Notify User toggle → In Channel/DM voľba; DM ukáže poznámku o zatvorených DMkách.
- [ ] In Channel + Delete bot message → "Delete after" s minimom 3s (nižšie hodnoty sa orežú).
- [ ] Message textarea s placeholderom `{channel} {server} {user}`.
- [X] Save bez vybraného kanála → toast "Select a channel first." (needá sa uložiť).
- [X] Úspešné uloženie → toast "Config saved successfully."; zlyhanie → toast s chybou.

---

## 🔒 Commands (`/commands`)
- [ ] Bez servera: "No server selected. Pick one."
- [ ] 3 taby: Moderation, Verification, Utility — správne príkazy v každom.
- [ ] Každý príkaz má toggle enable/disable (optimistické prepnutie, vráti sa späť pri chybe).
- [ ] Zámok pri príkaze, ktorý má nastavené obmedzenia (admin-only/role/kanály) + farebné chipy pod popisom.
- [ ] "Authorization" modal — admin-only toggle, sekcie Require roles / Block roles / Allowed channels / Blocked channels (rozbaľovacie, s vyhľadávaním).
- [ ] Kliknutie na už otvorenú/aktívnu sekciu v Authorization modali ju zbalí AJ vymaže výber.
- [ ] Save v modali — spinner → "Saved!" na 2s; Cancel/klik mimo modal zavrie bez uloženia.
- [ ] "Settings" modal (len pri príkazoch, čo ho majú) — napr. Ephemeral response, Include bots, DM before wipe.
- [ ] Kategóriové "Authorization" tlačidlo (hromadne pre celý tab) — ak majú príkazy rôzne nastavenia, formulár začne prázdny (explicitné prepísanie).
- [ ] "Enable All"/"Disable All" pre aktívny tab.
- [ ] "Sync Visibility" — spinner → "Synced!" na 3s.
- [ ] Prepnutie servera resetne všetky stavy a znova načíta.

---

## 🔒 Settings (`/settings`)
- [ ] Bez servera: "No server selected. Go back and pick one."
- [ ] Verification & Roles: toggle Verification, Verified Role picker, Inactive Role picker — každé auto-save s vlastným Saving/Saved/Error indikátorom.
- [ ] Warn Thresholds — zoznam "{N} warns → {Action}" s farbami podľa akcie, X na zmazanie (bez potvrdenia); pridanie (stepper + dropdown + Add); duplicitný limit → chyba.
- [ ] Auto-Mentions — zoznam kanál→rola, toggle ON/OFF, X na zmazanie; pridanie (kanál + rola + Add, disabled kým oboje nevybrané).
- [ ] Manager Roles — multi-select s vyhľadávaním, chipy; **explicitné "Save Manager Roles" tlačidlo** (NEukladá sa automaticky) — zmena bez uloženia zmizne pri opustení stránky.
- [ ] Log Channels — varovania pri rozbitej existujúcej konfigurácii; prepnutie kanála v slote migruje priradenia; checkbox na typ udalosti v jednom kanáli automaticky odškrtne rovnaký typ v inom slote (jedna udalosť = jeden kanál); pridanie nového slotu (len nepoužité kanály); explicitné "Save Log Channels".

---

## 🔒 Wipe (`/wipe`)
- [ ] Bez servera: "No server selected. Go to the dashboard..."
- [ ] "Checking access…" počas kontroly.
- [ ] Chýba wipe log kanál → jantárová hláška + link na Settings.
- [ ] Iný dôvod zamietnutia → červená hláška.
- [ ] Varovanie o deštruktívnej operácii je vždy viditeľné.
- [ ] Progress panel (Total/Checked/Inactive/Errors) + percento.
- [ ] "Remove all roles" checkbox — pri zaškrtnutí sa objaví "Keep roles" multi-picker.
- [ ] Potvrdzovací checkbox ("I understand...") — **"Start Wipe" je disabled, kým nie je zaškrtnutý**.
- [ ] Počas behu sa ovládacie prvky skryjú a zobrazí sa "Wipe in progress... do not close this page."
- [ ] Live konzola so stavom, farby podľa úrovne (error/warn/success/info), Clear tlačidlo.
- [ ] Odíď zo stránky počas behu a vráť sa — beh a konzola sa správne dotiahnu.
- [ ] Prepnutie servera resetne celý stav Wipe stránky (aj zaškrtnutý confirm checkbox).

---

## 🔒 Admin (`/admin`)
- [ ] Metriky: Uptime, Servers, Verified, Active Codes, Warnings, Memory.
- [ ] Allowed Servers — pridanie ID: neplatný formát (nie 17-20 číslic) → chyba; duplicitné ID → chyba; platné nové ID sa pridá a uloží.
- [ ] Zoznam pripojených guildov s "Allow"/"Allowed" tlačidlom.
- [ ] Pokus odstrániť POSLEDNÝ povolený server → chyba "Keep at least one server allowed..." (needá sa odstrániť).
- [ ] Guildy povolené, ale bot v nich nie je pripojený → samostatný riadok "Bot is not connected" s možnosťou zmazať.
- [ ] Maintenance Mode — vypnutie je okamžité; **zapnutie vyžaduje potvrdzovací dialóg** (Cancel/Enable Maintenance).
- [ ] Zrušenie dialógu (Cancel) → maintenance ostane vypnutý.
- [ ] Super Admin panel je čisto na čítanie (žiadne pridávanie/mazanie z UI).

---

## NotFound (neznáma URL)
- [ ] Návšteva neexistujúcej cesty (napr. `/toto-neexistuje`) → "404 — Page not found" s tlačidlami "Go to Dashboard" a "Go back".
- [ ] "Go back" ide na skutočne predchádzajúcu stránku (histórie), nie vždy na `/`.
- [ ] Ako bežný manažér skús ručne zadať do URL 🔒 stránku (`/admin`, `/settings`, `/modules`, `/commands`, `/wipe`) → ticho ťa to prehodí na `/` (Dashboard), NEukáže sa 404 ani obsah stránky.

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
- [ ] V sandboxe (Docker-in-Docker) sa nedajú spustiť — Testcontainers nevie nadviazať na Ryuk/Postgres. Over, že v GitHub Actions CI prebehnú zelené.
- [ ] Ak CI nie je nastavené alebo nebeží, spusti `./mvnw test` lokálne mimo sandboxu, na stroji s funkčným Dockerom pre Testcontainers.

## 2. SSE live-push (Maintenance mode banner, Verification status)
- [ ] Otvor dashboard v dvoch rôznych prehliadačoch/tabov naraz, prihlásený ako manažér na tom istom guilde.
- [ ] V jednom tabe zapni Maintenance Mode cez `/admin` (🔒) → v druhom tabe sa banner objaví bez refreshu.
- [ ] Vypni Maintenance Mode → banner v druhom tabe zmizne bez refreshu.
- [ ] Na Settings → Verification toggle prepni enabled/disabled → v druhom otvorenom tabe sa stav prekreslí bez refreshu.
- [ ] Zavri jeden tab (spadni SSE spojenie) → over, že server si neudržiava mŕtve spojenie donekonečna.

## 3. Reálne Discord slash príkazy (end-to-end cez skutočný bot)
- [ ] `/info` — embed sa vykreslí správne v Discorde (farby, polia, thumbnail, footer s časom vytvorenia servera).
- [ ] `/user @niekto` — embed s account info, roles, verified status, warns; skús aj na niekom, kto už nie je na serveri.
- [ ] `/warn add`, `/warn list`, `/warn remove`, `/mywarns`, `/warn clearall` — celý flow, Discord embed/reply formátovanie.
- [ ] `/verify` flow — AIS ID, doručenie emailu s kódom (bod 5), `/code` na potvrdenie, pridelenie Verified role.
- [ ] Role menu interakcie (klik na tlačidlo/select v role menu správe) — pridelenie/odobratie role.
- [ ] Moderation auto-action z `/warn` (timeout/kick/ban pri dosiahnutí limitu) — bot má reálne oprávnenia, akcia prebehne v Discorde.

## 4. LDAP (reálny univerzitný server)
- [ ] `/verify` s reálnym AIS ID — `LdapStudentDirectoryService` sa pripojí a spáruje študenta (meno, email, fakulta, account status) zo skutočného LDAP servera.
- [ ] Rate-limit (`LdapRequestThrottle`) pri viacerých rýchlo za sebou idúcich `/verify` — mimo testing mode oddelené min. 1 sekundou.

## 5. Reálne odoslanie emailu
- [ ] `/verify` doručí email s kódom na skutočnú adresu (skontroluj inbox, nie len logy) — predmet "Discord - Overovací kód", formátovanie tela.

## 6. Auto Delete / Auto Mention — plný pipeline
- [ ] Auto Delete s delay > 0 — správa sa naozaj zmaže po uplynutí nastaveného času.
- [ ] Auto Delete "Notify User" (In Channel aj DM) — správa/DM sa reálne odošle, placeholdery `{channel}`/`{server}`/`{user}` nahradené správne.
- [ ] Auto Delete "Delete bot message after" — bot správa sa naozaj zmaže po nastavenom čase.
- [ ] Auto Mention — rola sa naozaj mentionne v správnom kanáli; pri auto-zmazaní mention message zmizne po nastavenom čase.

## 7. Hacked Account Trap — plný trigger flow
- [ ] Skutočné triggernutie pasce → autor sa permanentne banuje, DM sa odošle (ak zapnuté), trigger správa sa zmaže (ak zapnuté).
- [ ] S Delete message history zapnutým → over v Discorde, že sa naozaj zmazala história správ na zvolenú dĺžku (Discord to robí sám ako súčasť banu, dashboard nevracia počet).
- [ ] Keďže ban je permanentný, autor sa po prvom triggeri už nemôže vrátiť a spustiť trigger znova (žiadna "repeat trigger" logika už neexistuje — bola viazaná na incident kanál/ticket, ktoré sú preč).

## 8. Scheduled joby (reálny čas)
- [ ] `DatabaseSyncService` — reštart bota → v logoch vidno, že sync prebehol a vo `verified_users` zmizli riadky pre ľudí bez Verified role.
- [ ] `ExpiredDataCleanupJob` — mazanie expirovaných verification codes/sessions po reálnom čase.

## 9. Wipe — celý beh (🔒, deštruktívne — len testovací guild!)
- [ ] Reálny Wipe beh na testovacom guilde — progress panel, live konzola, finálny recap post do wipe log kanála.
- [ ] "Remove all roles" + "Keep roles" kombinácia — vybrané roly naozaj ostanú.

## 10. Semester Setup/Switch — celý beh (🔒, testovací guild)
- [ ] Reálny Setup aj Switch beh — vytvorenie/premenovanie kategórií a kanálov v Discorde, priradenie rolí podľa mapovania, recap post do semester log kanála.
- [ ] Zavri stránku počas behu a vráť sa — beh pokračoval na serveri, UI sa správne dotiahne.
