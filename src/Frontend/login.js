/**
 * Finquity — login.js
 * Gère l'UI d'authentification (connexion / création de compte),
 * la validation des champs, et la persistance via localStorage.
 *
 * NB : Ce projet n'a pas de backend pour l'instant. Toute l'app
 * (Dashboard.html, import.html, ...) lit/écrit dans localStorage
 * sous les clés 'finquity_users', 'finquity_session', 'finquity_data'.
 * Ce fichier suit donc la même logique pour rester cohérent avec le reste
 * de l'application, plutôt que d'appeler une API /api/auth/... qui n'existe pas.
 *
 * Structure :
 *   1. Config
 *   2. DOM references
 *   3. Cursor (effet visuel)
 *   4. Validation
 *   5. Auth Store (localStorage)
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
  storage: {
    usersKey:   'finquity_users',
    sessionKey: 'finquity_session',
    dataKey:    'finquity_data',
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

  get eName()      { return document.getElementById('e-name');      },
  get eEmail()     { return document.getElementById('e-email');     },
  get ePass()      { return document.getElementById('e-pass');      },
  get eConfirm()   { return document.getElementById('e-confirm');   },

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
   4. VALIDATION
   ───────────────────────────────────────────── */
const Validator = {
  isEmailValid(value) {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim());
  },
  isPasswordValid(value) {
    return value.length >= CONFIG.validation.minPasswordLength;
  },
};


/* ─────────────────────────────────────────────
   5. AUTH STORE (localStorage)
   ───────────────────────────────────────────── */
const AuthStore = {
  _getUsers() {
    try { return JSON.parse(localStorage.getItem(CONFIG.storage.usersKey) || '[]'); }
    catch (_) { return []; }
  },
  _saveUsers(users) {
    localStorage.setItem(CONFIG.storage.usersKey, JSON.stringify(users));
  },
  _setSession(email, name) {
    localStorage.setItem(CONFIG.storage.sessionKey, JSON.stringify({ email, name }));
  },
  _getData() {
    try { return JSON.parse(localStorage.getItem(CONFIG.storage.dataKey) || '{}'); }
    catch (_) { return {}; }
  },

  /**
   * @returns {{ok:true,hasData:boolean}|{ok:false,message:string}}
   */
  signIn(email, password) {
    const users = this._getUsers();
    const user = users.find(u => u.email === email && u.password === password);
    if (!user) return { ok: false, message: 'Email ou mot de passe incorrect.' };

    this._setSession(user.email, user.name);
    const store = this._getData();
    return { ok: true, hasData: Boolean(store.bilan || store.cr) };
  },

  /**
   * @returns {{ok:true}|{ok:false,message:string}}
   */
  signUp({ name, email, password, company }) {
    const users = this._getUsers();
    if (users.find(u => u.email === email)) {
      return { ok: false, message: 'Un compte existe déjà avec cet email.' };
    }

    users.push({ name, email, password, company, createdAt: new Date().toISOString() });
    this._saveUsers(users);
    this._setSession(email, name);

    localStorage.setItem(CONFIG.storage.dataKey, JSON.stringify({
      meta: { company, sector: '', year: new Date().getFullYear(), lastUpdated: new Date().toISOString() },
    }));

    return { ok: true };
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
      const name = DOM.iName?.value.trim() ?? '';
      const confirm = DOM.iConfirm?.value ?? '';

      if (!name) { UI.fieldError(DOM.iName, DOM.eName); valid = false; }
      if (confirm !== pass) { UI.fieldError(DOM.iConfirm, DOM.eConfirm); valid = false; }
      if (!valid) return;

      this._signUp(name, email, pass);
    } else {
      if (!valid) return;
      this._signIn(email, pass);
    }
  },

  _signIn(email, pass) {
    const result = AuthStore.signIn(email, pass);
    if (!result.ok) { UI.globalError(result.message); return; }

    window.location.href = result.hasData
      ? CONFIG.redirect.afterSigninWithData
      : CONFIG.redirect.afterSigninNoData;
  },

  _signUp(name, email, pass) {
    const company = DOM.iCompany?.value.trim() ?? '';
    const result = AuthStore.signUp({ name, email, password: pass, company });
    if (!result.ok) { UI.globalError(result.message); return; }

    window.location.href = CONFIG.redirect.afterSignupNoData;
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