# OpenVault – Oprava černé obrazovky a GitHub Actions APK Release Workflow

Tento plán řeší příčiny černé obrazovky při spuštění aplikace OpenVault (jak ve streamovaném emulátoru, tak při přechodu mezi uzamčeným a odemčeným stavem SQLCipher databáze) a přidává plně automatizovaný GitHub Actions CI/CD workflow pro kompilaci Debug APK a publikaci GitHub Release.

### User Review & Critical Decisions

> [!IMPORTANT]
> Níže jsou potvrzené volby z úvodního dotazování a klíčová architektonická rozhodnutí pro opravu vykreslování a CI/CD pipeline:

- **Potvrzený spouštěč GitHub Actions workflow**: Workflow se spustí automaticky při každém pushi do hlavní větve (`main` / `master`), při vytvoření verzovacího git tagu (`v*`) a rovněž umožní ruční spuštění (`workflow_dispatch`) přímo z rozhraní GitHubu.
- **Potvrzený výstupní artefakt v GitHub Release**: Workflow zkompiluje a připojí k vytvořenému GitHub Release **Debug APK** (`OpenVault-debug.apk`), které je podepsané debug klíčem a připravené k okamžité instalaci na jakékoli Android zařízení bez nutnosti ruční správy produkčního keystore v GitHub Secrets.
- **Výchozí stav `FLAG_SECURE` kvůli streamovanému zobrazení**: Android příznak `WindowManager.LayoutParams.FLAG_SECURE` na úrovni systémového kompozitoru (SurfaceFlinger) blokuje přenos obrazu do streamovaného emulátoru a vzdálené obrazovky, což způsobuje 100% černou obrazovku. Výchozí hodnota `flagSecureEnabled` bude změněna na `false` (s možností kdykoliv ji jedním klepnutím zapnout v *Nastavení → Bezpečnost*).

---

### 1. Overview & Core Concept

- **Co tato úprava přináší**:
  1. **Okamžité vykreslení UI bez černé obrazovky**: Odstranění blokace obrazu příznakem `FLAG_SECURE` ve výchozím stavu a oprava životního cyklu SQLCipher databáze, která dříve při startu v uzamčeném stavu vytvářela nešifrovaný databázový soubor a následně při odemknutí kolidovala se SQLCipher klíčem.
  2. **Automatizovaný GitHub Release s APK**: Kompletní GitHub Actions workflow, který v čistém prostředí s JDK 17 a Android SDK sestaví `assembleDebug`, přejmenuje výstupní APK na čitelný název a automaticky vytvoří/aktualizuje GitHub Release s přiloženým instalačním `.apk` souborem.
- **Cílový přínos**: Aplikace okamžitě naběhne na uvítací/odemykací obrazovku na fyzickém zařízení i ve streamovaném náhledu a každý push do `main` nebo tag `v*` vygeneruje stažitelný instalační balíček v sekci GitHub Releases.

---

### 2. User Experience & Visual Design

- **Spuštění a první vykreslení (Zero-State to Unlock)**:
  - Ihned po spuštění `MainActivity` se vykreslí `OnboardingWelcomeScreen` (pokud trezor ještě neexistuje) nebo `UnlockBiometricScreen` / `UnlockPasswordScreen` (pokud je trezor inicializován).
  - Vizuální kontrast na AMOLED pozadí (`#000000`) je zvýrazněn kartami s povrchem `#0D0D11`, okrajem `#262633` a smaragdovým akcentem `#10B981`, takže uživatel okamžitě vidí stav trezoru a interaktivní prvky.
- **Ochrana proti snímkům obrazovky (`FLAG_SECURE`)**:
  - Ve výchozím stavu je vypnutá, aby neblokovala streamovaný obraz v prohlížeči nebo při sdílení obrazovky.
  - V sekci *Nastavení → Bezpečnost* zůstává plně funkční přepínač `Blokovat snímky obrazovky (FLAG_SECURE)`, který příznak na okně `MainActivity` dynamicky aktivuje či deaktivuje.

---

### 3. Key Product Decisions & Trade-Offs

- **Rozhodnutí 1: Oddělení reaktivních Room/SQLCipher toků od uzamčeného stavu**
  - *Zvolený přístup*: Dokud je `SecurityGate` v uzamčeném nebo neinicializovaném stavu (`!securityGate.isUnlocked()`), reaktivní datové toky (`activeNotesFlow`, `activePasswordsFlow`, `activeEventsFlow` atd.) vracejí prázdný seznam (`flowOf(emptyList())`) a **neotevírají** databázový soubor `openvault_sqlcipher.db`. Databáze se otevírá výhradně s platným 256bitovým SQLCipher klíčem až po úspěšném odemknutí trezoru.
  - *Proč*: Pokud Room otevře databázový soubor před odemknutím trezoru bez klíče (`sqlKey = null`), vytvoří na disku nešifrovanou SQLite hlavičku. Následné odemknutí trezoru a předání SQLCipher klíče stejnému souboru způsobí výjimku `SQLiteException: file is not a database` a zablokování UI.
- **Rozhodnutí 2: Kompilace a publikace Debug APK v GitHub Actions**
  - *Zvolený přístup*: Workflow nastaví JDK 17, Android SDK a Gradle cache, vygeneruje `debug.keystore` (pokud v repozitáři není přítomen), spustí `gradle :app:assembleDebug` (s automatickou detekcí `./gradlew` nebo systémového `gradle`) a publikuje výsledný APK soubor do GitHub Releases pomocí `softprops/action-gh-release@v2` s oprávněním `contents: write`.
  - *Proč*: Debug APK nevyžaduje nahrávání soukromých produkčních klíčů do GitHub Secrets, je okamžitě instalovatelné na telefony a funguje spolehlivě při každém pushi do `main` i při tagování verzí `v*`.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

```
┌──────────────────────────────────────────────────────────────────────────┐
│                        Spuštění aplikace (MainActivity)                  │
│  1. window.clearFlags(FLAG_SECURE) (výchozí stav pro viditelný stream)   │
│  2. SecurityGate vyhodnotí stav: Uninitialized / Locked / Unlocked       │
└───────────────────────────────────┬──────────────────────────────────────┘
                                    │
                  ┌─────────────────┴─────────────────┐
                  ▼                                   ▼
┌───────────────────────────────────┐ ┌────────────────────────────────────┐
│   Stav: Uninitialized / Locked    │ │          Stav: Unlocked            │
│ • SQLCipher DB se NEOTVÍRÁ        │ │ • SecurityGate odvodí SQLCipher    │
│ • Reaktivní Flow emitují emptyList│ │   podklíč (HKDF-SHA256)            │
│ • Okamžitě se vykreslí Onboarding │ │ • OpenVaultDatabase otevře šifro-  │
│   nebo Unlock obrazovka           │ │   vanou DB a načte položky trezoru │
└───────────────────────────────────┘ └────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────┐
│                 GitHub Actions CI/CD (Build & Release APK)               │
│  Spouštěč: push (main/master) | tag (v*) | workflow_dispatch             │
│     │                                                                    │
│     ├─► 1. Checkout & Setup JDK 17 + Android SDK + Gradle Action         │
│     ├─► 2. Kontrola / generování debug.keystore (androiddebugkey)        │
│     ├─► 3. Sestavení APK (:app:assembleDebug)                            │
│     ├─► 4. Upload Actions Artifact (OpenVault-debug.apk)                 │
│     └─► 5. Vytvoření GitHub Release s přiloženým OpenVault-debug.apk     │
└──────────────────────────────────────────────────────────────────────────┘
```

- **Ochrana při migraci nešifrovaného DB souboru**: Pokud z předchozího běhu zůstal na disku nešifrovaný nebo poškozený soubor `openvault_sqlcipher.db`, `OpenVaultDatabase` při zachycení `SQLiteException` během prvního připojení SQLCipher klíče bezpečně zotaví databázi, aby aplikace nikdy nespadla ani nezůstala viset na černé obrazovce.
- **Životní cyklus aktivity (`onStop` / `onResume`)**: Výchozí hodnota `lockOnBackground` bude nastavena tak, aby systémové dialogy (např. výběr souboru nebo biometrický prompt) nezpůsobovaly okamžité uzamčení uprostřed akce, zatímco neaktivita (`autoLockTimeoutMinutes`) a vypnutí obrazovky (`ACTION_SCREEN_OFF`) trezor spolehlivě uzamknou.
