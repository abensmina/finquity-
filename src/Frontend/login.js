/**
 * Finquity — login.js
 * Gère l'écran de connexion / création de compte.
 * Les comptes sont gérés par le serveur Java (AuthService) via l'API :
 *   POST /api/auth/login     { email, password }
 *   POST /api/auth/register  { companyName, siren, sector, username, email, password }
 *   GET  /api/company/statements  (pour savoir si des comptes ont déjà été importés)
 * La session est gardée par un cookie, envoyé automatiquement par fetch().
 *
 * Structure :
 *   1. Config
 *   2. DOM references
 *   3. Cursor (effet visuel)
 *   4. Validation
 *   5. Api (appels au serveur Java)
 *   6. UI state (erreurs, mode signin/signup)
 *   7. Handlers
 *   8. Init
 */

'use strict';

/* ─────────────────────────────────────────────
   1. CONFIG
   ───────────────────────────────────────────── */
const CONFIG = {
  redirect: {
    afterSignupNoData: 'import.html',
    afterSigninWithData: 'Dashboard.html',
    afterSigninNoData: 'import.html',
  },
  validation: {
    minPasswordLength: 8,
  },
};


/* ─────────────────────────────────────────────
   2. DOM REFERENCES
   ───────────────────────────────────────────── */
const DOM = {
  get btnSignin()  { return document.getElementById('btn-signin');  },
  get btnSignup()  { return document.getElementById('btn-signup');  },
  get formTitle()  { return document.getElementById('form-title');  },
  get formSub()    { return document.getElementById('form-sub');    },
  get formError()  { return document.getElementById('form-error');  },
  get forgot()     { return document.getElementById('forgot');      },
  get btnSubmit()  { return document.getElementById('btn-submit');  },

  get iName()      { return document.getElementById('i-name');      },
  get iEmail()     { return document.getElementById('i-email');     },
  get iPass()      { return document.getElementById('i-pass');      },
  get iConfirm()   { return document.getElementById('i-confirm');   },
  get iCompany()   { return document.getElementById('i-company');   },
  get iSiren()     { return document.getElementById('i-siren');     },
  get iSector()    { return document.getElementById('i-sector');    },

  get eName()      { return document.getElementById('e-name');      },
  get eEmail()     { return document.getElementById('e-email');     },
  get ePass()      { return document.getElementById('e-pass');      },
  get eConfirm()   { return document.getElementById('e-confirm');   },
  get eCompany()   { return document.getElementById('e-company');   },
  get eSiren()     { return document.getElementById('e-siren');     },

  get cursor()     { return document.getElementById('cursor');      },
  get cursorRing() { return document.getElementById('cursorRing');  },
};


/* ─────────────────────────────────────────────
   3. CURSOR (effet visuel, indépendant de l'auth)
   ───────────────────────────────────────────── */
const Cursor = {
  mx: 0, my: 0, rx: 0, ry: 0,

  init() {
    document.addEventListener('mousemove', e => {
      this.mx = e.clientX;
      this.my = e.clientY;
    });
    this._loop();
    this._bindHovers();
  },

  _loop() {
    this.rx += (this.mx - this.rx) * 0.12;
    this.ry += (this.my - this.ry) * 0.12;
    const c = DOM.cursor, r = DOM.cursorRing;
    if (c) { c.style.left = this.mx + 'px'; c.style.top = this.my + 'px'; }
    if (r) { r.style.left = this.rx + 'px'; r.style.top = this.ry + 'px'; }
    requestAnimationFrame(() => this._loop());
  },

  _bindHovers() {
    document.querySelectorAll('a, button, input').forEach(el => {
      el.addEventListener('mouseenter', () => {
        const r = DOM.cursorRing;
        if (r) { r.style.width = '44px'; r.style.height = '44px'; }
      });
      el.addEventListener('mouseleave', () => {
        const r = DOM.cursorRing;
        if (r) { r.style.width = '30px'; r.style.height = '30px'; }
      });
    });
  },
};


/* ─────────────────────────────────────────────
   4. VALIDATION (le serveur revérifie tout)
   ───────────────────────────────────────────── */
const Validator = {
  isEmailValid(value) {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim());
  },
  isPasswordValid(value) {
    return value.length >= CONFIG.validation.minPasswordLength;
  },
  isSirenValid(value) {
    return /^\d{9}$/.test(value.replace(/\s/g, ''));
  },
};


/* ─────────────────────────────────────────────
   5. API (appels au serveur Java)
   ───────────────────────────────────────────── */
const Api = {
  // Envoie une requête JSON. Renvoie les données, ou lance une erreur avec le message du serveur.
  async request(method, url, body) {
    let res;
    try {
      res = await fetch(url, {
        method,
        headers: body ? { 'Content-Type': 'application/json' } : {},
        body: body ? JSON.stringify(body) : undefined,
      });
    } catch (_) {
      throw new Error('Serveur injoignable. Lancez « mvn spring-boot:run » puis ouvrez http://localhost:8080/login.html');
    }
    const text = await res.text();
    const data = text ? JSON.parse(text) : null;
    if (!res.ok) throw new Error((data && data.error) || 'Erreur ' + res.status);
    return data;
  },

  login(email, password) {
    return this.request('POST', '/api/auth/login', { email, password });
  },

  register({ companyName, siren, sector, username, email, password }) {
    return this.request('POST', '/api/auth/register', { companyName, siren, sector, username, email, password });
  },

  // true si l'entreprise a déjà au moins une année de comptes enregistrée
  async hasStatements() {
    try {
      const list = await this.request('GET', '/api/company/statements');
      return Array.isArray(list) && list.length > 0;
    } catch (_) {
      return false;
    }
  },
};


/* ─────────────────────────────────────────────
   6. UI STATE (mode signin/signup + erreurs)
   ───────────────────────────────────────────── */
const UI = {
  mode: 'signin',

  setMode(mode) {
    this.mode = mode;
    const isSignup = mode === 'signup';

    DOM.btnSignin?.classList.toggle('active', !isSignup);
    DOM.btnSignup?.classList.toggle('active', isSignup);

    if (DOM.formTitle) DOM.formTitle.innerHTML = isSignup ? 'Créer un <em>compte</em>' : 'Bon <em>retour</em>';
    if (DOM.formSub)   DOM.formSub.textContent = isSignup
      ? 'Rejoignez Finquity et lancez votre première analyse.'
      : 'Connectez-vous pour accéder à vos analyses.';
    if (DOM.btnSubmit) DOM.btnSubmit.textContent = isSignup ? 'Créer mon compte' : 'Se connecter';

    document.querySelectorAll('.signup-only').forEach(el => el.classList.toggle('show', isSignup));
    if (DOM.forgot) DOM.forgot.style.display = isSignup ? 'none' : '';

    this.clearErrors();
  },

  clearErrors() {
    document.querySelectorAll('.field-err').forEach(e => e.classList.remove('show'));
    document.querySelectorAll('input').forEach(i => i.classList.remove('err'));
    DOM.formError?.classList.remove('show');
  },

  fieldError(inputEl, errEl) {
    inputEl?.classList.add('err');
    errEl?.classList.add('show');
  },

  globalError(message) {
    if (!DOM.formError) return;
    DOM.formError.textContent = message;
    DOM.formError.classList.add('show');
  },

  setLoading(isLoading) {
    if (DOM.btnSubmit) DOM.btnSubmit.disabled = isLoading;
  },
};


/* ─────────────────────────────────────────────
   7. HANDLERS
   ───────────────────────────────────────────── */
const Handlers = {
  handleSubmit() {
    UI.clearErrors();

    const email = DOM.iEmail?.value.trim() ?? '';
    const pass  = DOM.iPass?.value ?? '';
    let valid = true;

    if (!Validator.isEmailValid(email)) { UI.fieldError(DOM.iEmail, DOM.eEmail); valid = false; }
    if (!Validator.isPasswordValid(pass)) { UI.fieldError(DOM.iPass, DOM.ePass); valid = false; }

    if (UI.mode === 'signup') {
      const name    = DOM.iName?.value.trim() ?? '';
      const confirm = DOM.iConfirm?.value ?? '';
      const company = DOM.iCompany?.value.trim() ?? '';
      const siren   = (DOM.iSiren?.value ?? '').replace(/\s/g, '');
      const sector  = DOM.iSector?.value.trim() ?? '';

      if (!name) { UI.fieldError(DOM.iName, DOM.eName); valid = false; }
      if (confirm !== pass) { UI.fieldError(DOM.iConfirm, DOM.eConfirm); valid = false; }
      if (!company) { UI.fieldError(DOM.iCompany, DOM.eCompany); valid = false; }
      if (!Validator.isSirenValid(siren)) { UI.fieldError(DOM.iSiren, DOM.eSiren); valid = false; }
      if (!valid) return;

      this._signUp({ companyName: company, siren, sector, username: name, email, password: pass });
    } else {
      if (!valid) return;
      this._signIn(email, pass);
    }
  },

  async _signIn(email, pass) {
    UI.setLoading(true);
    try {
      await Api.login(email, pass);
      const hasData = await Api.hasStatements();
      window.location.href = hasData ? CONFIG.redirect.afterSigninWithData : CONFIG.redirect.afterSigninNoData;
    } catch (err) {
      UI.globalError(err.message);
    } finally {
      UI.setLoading(false);
    }
  },

  async _signUp(form) {
    UI.setLoading(true);
    try {
      await Api.register(form);   // le serveur connecte directement le nouvel utilisateur
      window.location.href = CONFIG.redirect.afterSignupNoData;
    } catch (err) {
      UI.globalError(err.message);
    } finally {
      UI.setLoading(false);
    }
  },
};


/* ─────────────────────────────────────────────
   8. INIT
   ───────────────────────────────────────────── */
document.addEventListener('DOMContentLoaded', () => {
  Cursor.init();
  UI.setMode('signin');

  DOM.btnSignin?.addEventListener('click', () => UI.setMode('signin'));
  DOM.btnSignup?.addEventListener('click', () => UI.setMode('signup'));
  DOM.btnSubmit?.addEventListener('click', () => Handlers.handleSubmit());

  document.addEventListener('keydown', e => {
    if (e.key === 'Enter') Handlers.handleSubmit();
  });
});
