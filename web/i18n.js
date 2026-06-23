/**
 * DeltaVoice web i18n — loads JSON locale bundles and applies [data-i18n] / [data-i18n-placeholder].
 */
(function (global) {
  const STORAGE_KEY = "deltavoice_locale";
  const DEFAULT_LOCALE = "en";

  const LOCALE_LABELS = {
    en: "English",
    ar: "العربية",
    es: "Español",
    fr: "Français",
    de: "Deutsch",
    hi: "हिन्दी",
    ja: "日本語",
    ko: "한국어",
    pt: "Português",
    ru: "Русский",
    zh: "中文",
    tr: "Türkçe",
    vi: "Tiếng Việt",
  };

  let currentLocale = DEFAULT_LOCALE;
  let messages = {};
  let ready = false;
  const readyWaiters = [];

  function detectLocale() {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored && LOCALE_LABELS[stored]) return stored;
    const nav = (navigator.language || navigator.userLanguage || "en").toLowerCase();
    if (nav.startsWith("ar")) return "ar";
    if (nav.startsWith("es")) return "es";
    if (nav.startsWith("fr")) return "fr";
    if (nav.startsWith("de")) return "de";
    if (nav.startsWith("hi")) return "hi";
    if (nav.startsWith("ja")) return "ja";
    if (nav.startsWith("ko")) return "ko";
    if (nav.startsWith("pt")) return "pt";
    if (nav.startsWith("ru")) return "ru";
    if (nav.startsWith("zh")) return "zh";
    if (nav.startsWith("tr")) return "tr";
    if (nav.startsWith("vi")) return "vi";
    return DEFAULT_LOCALE;
  }

  function getNested(obj, path) {
    return path.split(".").reduce((o, k) => (o && o[k] != null ? o[k] : null), obj);
  }

  function t(key, vars) {
    let val = getNested(messages, key);
    if (val == null) val = key;
    if (vars && typeof val === "string") {
      Object.keys(vars).forEach((k) => {
        val = val.replace(new RegExp(`\\{${k}\\}`, "g"), vars[k]);
      });
    }
    return val;
  }

  function applyDom() {
    document.documentElement.lang = currentLocale;
    document.documentElement.dir = currentLocale === "ar" ? "rtl" : "ltr";

    document.querySelectorAll("[data-i18n]").forEach((el) => {
      const key = el.getAttribute("data-i18n");
      const val = t(key);
      if (val == null) return;
      if (el.tagName === "TITLE") {
        document.title = val;
      } else if (el.hasAttribute("data-i18n-html")) {
        el.innerHTML = val;
      } else {
        el.textContent = val;
      }
    });

    document.querySelectorAll("[data-i18n-placeholder]").forEach((el) => {
      el.placeholder = t(el.getAttribute("data-i18n-placeholder"));
    });

    document.querySelectorAll("[data-i18n-aria]").forEach((el) => {
      el.setAttribute("aria-label", t(el.getAttribute("data-i18n-aria")));
    });

    document.querySelectorAll("select[data-i18n-options]").forEach((sel) => {
      const prefix = sel.getAttribute("data-i18n-options");
      Array.from(sel.options).forEach((opt) => {
        const code = opt.value;
        const label = t(`${prefix}.${code}`);
        if (label && label !== `${prefix}.${code}`) opt.textContent = label;
      });
    });
  }

  async function loadLocale(locale) {
    const tag = LOCALE_LABELS[locale] ? locale : DEFAULT_LOCALE;
    let data = {};
    try {
      const res = await fetch(`locales/${tag}.json`);
      if (res.ok) data = await res.json();
    } catch (_) {
      /* offline or missing bundle */
    }
    if (tag !== DEFAULT_LOCALE) {
      try {
        const fb = await fetch(`locales/${DEFAULT_LOCALE}.json`);
        if (fb.ok) {
          const base = await fb.json();
          messages = deepMerge(base, data);
        } else {
          messages = data;
        }
      } catch (_) {
        messages = data;
      }
    } else {
      messages = data;
    }
    currentLocale = tag;
    localStorage.setItem(STORAGE_KEY, tag);
    applyDom();
    document.dispatchEvent(new CustomEvent("deltavoice:locale", { detail: { locale: tag } }));
  }

  function deepMerge(base, over) {
    const out = { ...base };
    Object.keys(over || {}).forEach((k) => {
      if (over[k] && typeof over[k] === "object" && !Array.isArray(over[k])) {
        out[k] = deepMerge(base[k] || {}, over[k]);
      } else {
        out[k] = over[k];
      }
    });
    return out;
  }

  function mountLanguagePicker(container) {
    if (!container) return;
    const wrap = document.createElement("label");
    wrap.className = "dv-lang-picker";
    wrap.style.cssText =
      "display:inline-flex;align-items:center;gap:6px;font-size:0.8125rem;color:var(--text-secondary,#94a3b8)";
    const span = document.createElement("span");
    span.setAttribute("data-i18n", "settings.language");
    span.textContent = t("settings.language");
    const sel = document.createElement("select");
    sel.id = "dvLocaleSelect";
    sel.style.cssText =
      "background:var(--surface,#1a1a1c);color:inherit;border:1px solid rgba(255,255,255,0.12);border-radius:8px;padding:4px 8px;font-size:0.8125rem";
    Object.keys(LOCALE_LABELS).forEach((code) => {
      const opt = document.createElement("option");
      opt.value = code;
      opt.textContent = LOCALE_LABELS[code];
      if (code === currentLocale) opt.selected = true;
      sel.appendChild(opt);
    });
    sel.addEventListener("change", () => {
      loadLocale(sel.value).then(() => applyDom());
    });
    wrap.appendChild(span);
    wrap.appendChild(sel);
    container.appendChild(wrap);
  }

  async function init(opts) {
    currentLocale = detectLocale();
    await loadLocale(currentLocale);
    if (opts && opts.languagePicker) {
      mountLanguagePicker(
        typeof opts.languagePicker === "string"
          ? document.querySelector(opts.languagePicker)
          : opts.languagePicker
      );
    }
    ready = true;
    readyWaiters.splice(0).forEach((fn) => fn());
  }

  function whenReady(fn) {
    if (ready) fn();
    else readyWaiters.push(fn);
  }

  function languageOptions() {
    const codes = ["en", "es", "fr", "de", "it", "pt", "ru", "ja", "ko", "zh", "ar", "hi"];
    return codes.map((code) => [t(`languages.${code}`), code]);
  }

  global.DeltaVoiceI18n = {
    init,
    t,
    whenReady,
    applyDom,
    loadLocale,
    getLocale: () => currentLocale,
    languageOptions,
    LOCALE_LABELS,
  };
})(typeof window !== "undefined" ? window : globalThis);
