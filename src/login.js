/**
 * Finquity — login.js
 * Handles authentication UI, validation, and API calls.
 *
 * Structure:
 *   1. Config
 *   2. DOM references
 *   3. Cursor
 *   4. Password toggle
 *   5. Validation
 *   6. Auth API
 *   7. UI state machine  (idle → loading → success | error)
 *   8. Event listeners
 *   9. Init
 */

'use strict';

/* ─────────────────────────────────────────────
   1. CONFIG
   ───────────────────────────────────────────── */
const CONFIG = {
  api: {
    baseUrl:  '/api',          // swap for your real base URL
    login:    '/api/auth/login',
    sso:      '/api/auth/sso',
    forgot:   '/api/auth/forgot-password',
  },
  redirect: {
    dashboard: 'Dashboard.html',
    delay:     3000,           // ms before redirect after success
  },
  validation: {
    minPasswordLength: 8,
  },
  storage: {
    tokenKey:  'fq_token',
    userKey:   'fq_user',
  },
};


/* ─────────────────────────────────────────────
   2. DOM REFERENCES
   ───────────────────────────────────────────── */
const DOM = {
  get form()         { return document.getElementById('loginForm');    },
  get emailInput()   { return document.getElementById('email');        },
  get pwdInput()     { return document.getElementById('password');     },
  get emailError()   { return document.getElementById('emailError');   },
  get pwdError()     { return document.getElementById('pwdError');     },
  get submitBtn()    { return document.getElementById('submitBtn');    },
  get arrowIcon()    { return document.getElementById('arrowIcon');    },
  get pwdToggle()    { return document.getElementById('pwdToggle');    },
  get eyeIcon()      { return document.getElementById('eyeIcon');      },
  get successWrap()  { return document.getElementById('successWrap'); },
  get progressFill() { return document.getElementById('progressFill'); },
  get cursor()       { return document.getElementById('cursor');       },
  get cursorRing()   { return document.getElementById('cursorRing');   },
  get globalError()  { return document.getElementById('globalError');  },
};


/* ─────────────────────────────────────────────
   3. CURSOR
   ───────────────────────────────────────────── */
const Cursor = {
  mx: 0, my: 0,
  rx: 0, ry: 0,

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

    const c = DOM.cursor;
    const r = DOM.cursorRing;
    if (c) { c.style.left = this.mx + 'px'; c.style.top = this.my + 'px'; }
    if (r) { r.style.left = this.rx + 'px'; r.style.top = this.ry + 'px'; }

    requestAnimationFrame(() => this._loop());
  },

  _bindHovers() {
    document.querySelectorAll('a, button, input').forEach(el => {
      el.addEventListener('mouseenter', () => {
        const r = DOM.cursorRing;
        const c = DOM.cursor;
        if (r) { r.style.width = '46px'; r.style.height = '46px'; }
        if (c) c.style.transform = 'translate(-50%,-50%) scale(1.4)';
      });
      el.addEventListener('mouseleave', () => {
        const r = DOM.cursorRing;
        const c = DOM.cursor;
        if (r) { r.style.width = '30px'; r.style.height = '30px'; }
        if (c) c.style.transform = 'translate(-50%,-50%) scale(1)';
      });
    });
  },
};


/* ─────────────────────────────────────────────
   4. PASSWORD TOGGLE
   ───────────────────────────────────────────── */
const PasswordToggle = {
  visible: false,

  init() {
    DOM.pwdToggle?.addEventListener('click', () => this.toggle());
  },

  toggle() {
    this.visible = !this.visible;
    DOM.pwdInput.type = this.visible ? 'text' : 'password';
    this._updateIcon();
  },

  _updateIcon() {
    if (!DOM.eyeIcon) return;
    DOM.eyeIcon.innerHTML = this.visible
      ? '<path d="M1 8s2.5-5 7-5 7 5 7 5-2.5 5-7 5-7-5-7-5z"/><line x1="2" y1="2" x2="14" y2="14"/>'
      : '<path d="M1 8s2.5-5 7-5 7 5 7 5-2.5 5-7 5-7-5-7-5z"/><circle cx="8" cy="8" r="2"/>';
  },
};


/* ─────────────────────────────────────────────
   5. VALIDATION
   ───────────────────────────────────────────── */
const Validator = {
  rules: {
    email(value) {
      if (!value.trim())                               return 'Email address is required.';
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value))  return 'Please enter a valid email address.';
      return null;
    },
    password(value) {
      if (!value)                                                       return 'Password is required.';
      if (value.length < CONFIG.validation.minPasswordLength)
        return `Password must be at least ${CONFIG.validation.minPasswordLength} characters.`;
      return null;
    },
  },

  /**
   * Validate a single field.
   * @param {'email'|'password'} field
   * @param {string} value
   * @returns {string|null} error message, or null if valid
   */
  validateField(field, value) {
    return this.rules[field]?.(value) ?? null;
  },

  /**
   * Validate the full form. Shows/hides error messages.
   * @returns {boolean} true if valid
   */
  validateForm() {
    const email    = DOM.emailInput?.value ?? '';
    const password = DOM.pwdInput?.value   ?? '';

    const emailErr = this.validateField('email',    email);
    const pwdErr   = this.validateField('password', password);

    this._setFieldError('email',    emailErr);
    this._setFieldError('password', pwdErr);

    return !emailErr && !pwdErr;
  },

  /** Show or clear error for one field */
  _setFieldError(field, message) {
    const input = field === 'email' ? DOM.emailInput : DOM.pwdInput;
    const errEl = field === 'email' ? DOM.emailError  : DOM.pwdError;

    if (!input || !errEl) return;

    if (message) {
      input.classList.add('error');
      errEl.textContent = message;
      errEl.classList.add('show');
    } else {
      input.classList.remove('error');
      errEl.classList.remove('show');
    }
  },

  /** Clear all field errors */
  clearFieldError(field) {
    this._setFieldError(field, null);
  },
};


/* ─────────────────────────────────────────────
   6. AUTH API
   ───────────────────────────────────────────── */
const AuthAPI = {
  /**
   * POST /api/auth/login
   * @param {{ email: string, password: string }} credentials
   * @returns {Promise<{ token: string, user: object }>}
   */
  async login({ email, password }) {
    const res = await fetch(CONFIG.api.login, {
      method:  'POST',
      headers: { 'Content-Type': 'application/json' },
      body:    JSON.stringify({ email, password }),
    });

    const data = await res.json().catch(() => ({}));

    if (!res.ok) {
      // Normalise backend error messages
      const message = data?.message ?? data?.error ?? this._httpError(res.status);
      throw new AuthError(message, res.status);
    }

    return data;   // { token, user }
  },

  /** Stub: redirect to SSO provider */
  redirectToSSO() {
    window.location.href = CONFIG.api.sso;
  },

  /** POST /api/auth/forgot-password */
  async forgotPassword(email) {
    const res = await fetch(CONFIG.api.forgot, {
      method:  'POST',
      headers: { 'Content-Type': 'application/json' },
      body:    JSON.stringify({ email }),
    });
    if (!res.ok) throw new Error('Could not send reset link. Please try again.');
    return true;
  },

  /** Human-readable HTTP error fallback */
  _httpError(status) {
    const map = {
      400: 'Invalid request. Please check your inputs.',
      401: 'Incorrect email or password.',
      403: 'Your account has been suspended. Please contact support.',
      404: 'No account found with this email address.',
      429: 'Too many attempts. Please wait a moment and try again.',
      500: 'Server error. Please try again later.',
    };
    return map[status] ?? `Unexpected error (${status}). Please try again.`;
  },
};

/** Typed auth error for UI differentiation */
class AuthError extends Error {
  constructor(message, status) {
    super(message);
    this.name   = 'AuthError';
    this.status = status;
  }
}


/* ─────────────────────────────────────────────
   7. UI STATE MACHINE
   States: idle → loading → success | error
   ───────────────────────────────────────────── */
const UI = {

  /** Persist auth data to session storage */
  _storeSession({ token, user }) {
    sessionStorage.setItem(CONFIG.storage.tokenKey, token);
    sessionStorage.setItem(CONFIG.storage.userKey, JSON.stringify(user));
  },

  /** Transition: idle → loading */
  setLoading() {
    const btn = DOM.submitBtn;
    if (!btn) return;
    btn.classList.add('loading');
    btn.disabled = true;
    if (DOM.arrowIcon) DOM.arrowIcon.style.display = 'none';
    this._clearGlobalError();
  },

  /** Transition: loading → idle (on validation fail or recoverable error) */
  setIdle() {
    const btn = DOM.submitBtn;
    if (!btn) return;
    btn.classList.remove('loading');
    btn.disabled = false;
    if (DOM.arrowIcon) DOM.arrowIcon.style.display = '';
  },

  /** Transition: loading → success */
  setSuccess(data) {
    this._storeSession(data);

    if (DOM.form)        DOM.form.style.display    = 'none';
    if (DOM.successWrap) DOM.successWrap.classList.add('show');

    // Kick off progress bar → redirect
    requestAnimationFrame(() => {
      if (DOM.progressFill) DOM.progressFill.style.width = '100%';
    });
    setTimeout(() => {
      window.location.href = CONFIG.redirect.dashboard;
    }, CONFIG.redirect.delay);
  },

  /** Transition: loading → error (shows banner above form) */
  setError(message) {
    this.setIdle();
    this._showGlobalError(message);
  },

  _showGlobalError(message) {
    let el = DOM.globalError;
    if (!el) {
      // Create banner if it doesn't exist yet in the HTML
      el = document.createElement('div');
      el.id = 'globalError';
      el.style.cssText = [
        'margin-bottom:18px',
        'padding:13px 16px',
        'border:1px solid rgba(224,112,112,0.35)',
        'border-radius:2px',
        'background:rgba(224,112,112,0.06)',
        'font-size:13px',
        'font-weight:300',
        'color:#e07070',
        'letter-spacing:0.02em',
        'line-height:1.5',
        'animation:fade-up 0.3s forwards',
      ].join(';');
      DOM.form?.prepend(el);
    }
    el.textContent = message;
    el.style.display = 'block';
  },

  _clearGlobalError() {
    const el = DOM.globalError;
    if (el) el.style.display = 'none';
  },
};


/* ─────────────────────────────────────────────
   8. EVENT LISTENERS
   ───────────────────────────────────────────── */
const Events = {
  init() {
    this._bindForm();
    this._bindInlineValidation();
    this._bindForgotPassword();
    this._bindSSO();
  },

  _bindForm() {
    DOM.form?.addEventListener('submit', async e => {
      e.preventDefault();

      if (!Validator.validateForm()) return;

      UI.setLoading();

      try {
        const data = await AuthAPI.login({
          email:    DOM.emailInput.value.trim(),
          password: DOM.pwdInput.value,
        });
        UI.setSuccess(data);
      } catch (err) {
        // 401 → field-level hint; others → banner
        if (err instanceof AuthError && err.status === 401) {
          Validator._setFieldError('password', 'Incorrect email or password.');
          UI.setIdle();
        } else {
          UI.setError(err.message);
        }
      }
    });
  },

  /** Validate each field on blur; clear error on input */
  _bindInlineValidation() {
    DOM.emailInput?.addEventListener('blur', () => {
      const err = Validator.validateField('email', DOM.emailInput.value);
      Validator._setFieldError('email', err);
    });
    DOM.emailInput?.addEventListener('input', () => {
      Validator.clearFieldError('email');
      UI._clearGlobalError();
    });

    DOM.pwdInput?.addEventListener('blur', () => {
      const err = Validator.validateField('password', DOM.pwdInput.value);
      Validator._setFieldError('password', err);
    });
    DOM.pwdInput?.addEventListener('input', () => {
      Validator.clearFieldError('password');
      UI._clearGlobalError();
    });
  },

  _bindForgotPassword() {
    document.querySelector('.forgot')?.addEventListener('click', async e => {
      e.preventDefault();
      const email = DOM.emailInput?.value.trim();
      if (!email || Validator.validateField('email', email)) {
        Validator._setFieldError('email', 'Enter your email first.');
        DOM.emailInput?.focus();
        return;
      }
      try {
        await AuthAPI.forgotPassword(email);
        UI._showGlobalError('Password reset link sent — check your inbox.');
        // Recolour banner to gold for success tone
        const el = DOM.globalError;
        if (el) el.style.color = '#c9a96e';
      } catch (err) {
        UI._showGlobalError(err.message);
      }
    });
  },

  _bindSSO() {
    document.querySelector('.btn-sso')?.addEventListener('click', () => {
      AuthAPI.redirectToSSO();
    });
  },
};


/* ─────────────────────────────────────────────
   9. INIT
   ───────────────────────────────────────────── */
document.addEventListener('DOMContentLoaded', () => {
  Cursor.init();
  PasswordToggle.init();
  Events.init();
});