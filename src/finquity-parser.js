/**
 * FinQuity — Parser de fichiers financiers (Bilan & Compte de résultat)
 * Utilise SheetJS (XLSX). Compatible avec tout format de bilan/CR comptable FR.
 *
 * Usage :
 *   const result = FinquityParser.parse(arrayBuffer, filename);
 *   // result.type    → 'bilan' | 'cr' | 'unknown'
 *   // result.data    → objet normalisé (voir schéma ci-dessous)
 *   // result.raw     → toutes les lignes extraites
 *   // result.errors  → champs manquants
 *   // result.warnings→ avertissements non bloquants
 */

const FinquityParser = (() => {

  // ─── Normalisation des libellés ───────────────────────────────────────────
  // Supprime accents, passe en minuscules, retire ponctuation
  function norm(s) {
    if (s == null) return '';
    return String(s)
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/[^a-z0-9\s]/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
  }

  // Retourne true si le label normalisé contient l'un des mots-clés
  function matches(label, keywords) {
    const n = norm(label);
    return keywords.some(kw => n.includes(kw));
  }

  // ─── Cartes de mots-clés ──────────────────────────────────────────────────

  // Détection du type de document
  const DETECT_BILAN = ['actif immobili', 'capitaux propres', 'fonds propres', 'passif', 'bilan'];
  const DETECT_CR    = ['chiffre d affaires', 'produits d exploitation', 'charges d exploitation',
                        'resultat d exploitation', 'compte de resultat', 'produits financiers'];

  // Champs Bilan — côté ACTIF (colonnes paires : label | valeur | ... label | valeur)
  const BILAN_ACTIF = {
    immob_incorporel : ['incorporel', 'frais de developpement', 'logiciel', 'brevet', 'fonds commercial',
                        'immobilisation incorporelle'],
    immob_corporel   : ['terrain', 'construction', 'materiel industriel', 'materiel informatique',
                        'agencement', 'mobilier', 'immobilisation corporelle', 'materiel de transport'],
    immob_financier  : ['participation', 'pret', 'depot', 'immobilisation financiere', 'titre'],
    stocks           : ['stock', 'marchandise', 'matiere premiere', 'produit fini', 'en cours'],
    creances         : ['creance client', 'client', 'debiteur', 'effet a recevoir', 'autre creance'],
    treso            : ['disponibilite', 'banque', 'caisse', 'valeur mobiliere', 'placement court terme'],
  };

  // Champs Bilan — côté PASSIF
  const BILAN_PASSIF = {
    capital          : ['capital social', 'capital'],
    reserves         : ['reserve', 'report a nouveau', 'prime d emission', 'prime de fusion'],
    resultat_bilan   : ["resultat de l exercice", 'benefice', 'perte', 'resultat net'],
    dette_lt         : ['emprunt', 'dette financiere long', 'dette long terme', 'dette lt',
                        'emprunt obligataire', 'emprunt bancaire'],
    dette_ct_fourni  : ['fournisseur', 'dette fournisseur'],
    dette_ct_fiscale : ['dette fiscale', 'impot', 'tva', 'taxe'],
    dette_ct_sociale : ['dette sociale', 'organisme social', 'securite sociale', 'urssaf', 'salaire'],
    dette_ct_autre   : ['autre dette', 'avance client', 'produit constate d avance'],
  };

  // Champs Compte de résultat
  const CR_FIELDS = {
    ca               : ['chiffre d affaires', 'ventes de marchandises', 'production vendue',
                        'prestations de services', 'ca net', 'total produits d exploitation'],
    autres_produits  : ['autre produit d exploitation', 'subvention', 'reprise amortissement',
                        'production immobilisee', 'variation de stock production'],
    achats           : ['achat de marchandises', 'achat de matiere', 'variation de stock matiere',
                        'variation de stock marchandise', 'cout d achat', 'achat consomme'],
    charges_externes : ['charge externe', 'service exterieur', 'sous traitance', 'loyer',
                        'transport', 'honoraire', 'publicite', 'telecommunication'],
    impots_taxes     : ['impot et taxe', 'taxe professionnelle', 'cfe', 'cvae'],
    charges_personnel: ['salaire', 'charge de personnel', 'charge sociale', 'remuneration',
                        'traitement', 'masse salariale'],
    amortissements   : ['amortissement', 'depreciation', 'provision pour depreciation',
                        'dotation aux amortissements'],
    autres_charges   : ['autre charge d exploitation', 'valeur nette comptable', 'charge exceptionnelle'],
    produits_fin     : ['produit financier', 'interet recu', 'dividende', 'produit de participation'],
    charges_fin      : ['charge financiere', 'interet verse', 'interet et charge assimilee',
                        'charge d interet', 'frais financier', 'agios'],
    produits_excep   : ['produit exceptionnel'],
    charges_excep    : ['charge exceptionnelle'],
    participation    : ['participation des salaries'],
    impot_res        : ['impot sur les benefices', 'impot sur les societes', 'is ', 'impot sur le revenu'],
  };

  // ─── Conversion robuste vers nombre ─────────────────────────────────────
  // Accepte les nombres JS, mais aussi les chaînes formatées à la française
  // ex : "1 234 567,00", "1.234.567,00", "850 000 €"
  function toNum(v) {
    if (v == null) return null;
    if (typeof v === 'number') return isFinite(v) ? v : null;
    if (typeof v === 'string') {
      const s = v.trim().replace(/[€%\s]/g, '');
      if (s === '' || s === '-') return null;
      // Format FR : virgule = décimale, point = séparateur de milliers
      const normalized = (s.includes(',') && s.includes('.'))
        ? s.replace(/\./g, '').replace(',', '.')   // "1.234,56" → "1234.56"
        : s.replace(',', '.');                      // "1234,56"  → "1234.56"
      const n = parseFloat(normalized);
      return isFinite(n) ? n : null;
    }
    return null;
  }

  // ─── Extraction des lignes d'une feuille ─────────────────────────────────

  function extractRows(worksheet) {
    const range = XLSX.utils.decode_range(worksheet['!ref'] || 'A1:A1');
    const rows = [];
    for (let r = range.s.r; r <= range.e.r; r++) {
      const row = [];
      for (let c = range.s.c; c <= range.e.c; c++) {
        const cell = worksheet[XLSX.utils.encode_cell({ r, c })];
        // Préférer la valeur calculée (v) à la formule brute
        row.push(cell ? cell.v : null);
      }
      rows.push(row);
    }
    return rows;
  }

  // ─── Détection du type de document ───────────────────────────────────────

  function detectType(rows) {
    let bilanScore = 0, crScore = 0;
    for (const row of rows) {
      for (const cell of row) {
        if (cell == null) continue;
        const n = norm(cell);
        if (DETECT_BILAN.some(k => n.includes(k))) bilanScore++;
        if (DETECT_CR.some(k => n.includes(k))) crScore++;
      }
    }
    if (bilanScore === 0 && crScore === 0) return 'unknown';
    return bilanScore >= crScore ? 'bilan' : 'cr';
  }

  // ─── Parser Bilan ─────────────────────────────────────────────────────────
  // Stratégie : chercher les paires (label, valeur) dans chaque colonne.
  // Un bilan FR classique a ACTIF en colonnes A/B et PASSIF en colonnes E/F,
  // mais certains formats ont tout en deux colonnes. On scanne toutes les cols.

  function parseBilan(rows) {
    // Accumulateurs par champ
    const acc = {};
    Object.keys(BILAN_ACTIF).forEach(k => acc[k] = 0);
    Object.keys(BILAN_PASSIF).forEach(k => acc[k] = 0);

    // Lignes de section en cours (pour éviter de cumuler les sous-totaux)
    // On ignore les lignes dont le label contient "total"
    const isTotalRow = (label) => norm(label).includes('total');

    for (const row of rows) {
      // Chercher toutes les paires (label, valeur) dans la ligne
      // (plusieurs paires possibles : ACTIF + PASSIF côte à côte)
      // Stratégie bilan FR : label | Brut | Amort | Net  → on prend la DERNIÈRE valeur (Net)
      const pairs = [];
      for (let i = 0; i < row.length; i++) {
        const label = row[i];
        if (label == null || typeof label !== 'string' || label.trim() === '') continue;
        if (isTotalRow(label)) continue;
        // Chercher la dernière valeur numérique dans les 6 colonnes suivantes
        let lastNum = null;
        const limit = Math.min(row.length, i + 7);
        for (let j = i + 1; j < limit; j++) {
          // Arrêter si on rencontre un autre label (nouvelle paire)
          if (row[j] != null && typeof row[j] === 'string' && row[j].trim() !== '' && j > i + 1) break;
          const n = toNum(row[j]);
          if (n !== null && n !== 0) lastNum = n;
        }
        if (lastNum !== null) {
          pairs.push({ label, val: lastNum, col: i });
        }
      }

      for (const { label, val } of pairs) {
        // Tenter de matcher avec ACTIF ou PASSIF
        let matched = false;
        for (const [key, keywords] of Object.entries(BILAN_ACTIF)) {
          if (matches(label, keywords)) {
            acc[key] += val;
            matched = true;
            break;
          }
        }
        if (!matched) {
          for (const [key, keywords] of Object.entries(BILAN_PASSIF)) {
            if (matches(label, keywords)) {
              acc[key] += val;
              break;
            }
          }
        }
      }
    }

    // Agréger en champs FinQuity
    const immob        = acc.immob_incorporel + acc.immob_corporel + acc.immob_financier;
    const actif_circ   = acc.stocks + acc.creances;
    const treso        = acc.treso;
    const cp           = acc.capital + acc.reserves + acc.resultat_bilan;
    const dette_lt     = acc.dette_lt;
    const dette_ct     = acc.dette_ct_fourni + acc.dette_ct_fiscale +
                         acc.dette_ct_sociale + acc.dette_ct_autre;

    return {
      type: 'bilan',
      fields: { immob, actif_circ, treso, cp, dette_lt, dette_ct },
      detail: acc,
    };
  }

  // ─── Parser Compte de résultat ────────────────────────────────────────────

  function parseCR(rows) {
    const acc = {};
    Object.keys(CR_FIELDS).forEach(k => acc[k] = 0);

    const isTotalRow = (label) => {
      const n = norm(label);
      return n.includes('total') || n.includes('resultat') || n.startsWith('total');
    };

    for (const row of rows) {
      for (let i = 0; i < row.length; i++) {
        const label = row[i];
        if (label == null || typeof label !== 'string' || label.trim() === '') continue;
        if (isTotalRow(label)) continue;
        // Chercher la première valeur numérique dans les 6 colonnes suivantes (exercice N)
        let firstNum = null;
        const limit = Math.min(row.length, i + 7);
        for (let j = i + 1; j < limit; j++) {
          if (row[j] != null && typeof row[j] === 'string' && row[j].trim() !== '' && j > i + 1) break;
          const n = toNum(row[j]);
          if (n !== null && n !== 0) { firstNum = n; break; }
        }
        if (firstNum !== null) {
          for (const [key, keywords] of Object.entries(CR_FIELDS)) {
            if (matches(label, keywords)) {
              acc[key] += Math.abs(firstNum);
              break;
            }
          }
        }
      }
    }

    // Calculer les agrégats FinQuity
    const ca             = acc.ca;
    const cogs           = acc.achats;
    const opex           = acc.charges_externes + acc.impots_taxes + acc.charges_personnel + acc.autres_charges;
    const amort          = acc.amortissements;
    const interet        = acc.charges_fin;
    const impot          = acc.impot_res;
    const ebitda         = ca - cogs - opex;
    const ebit           = ebitda - amort;
    const rn             = ebit - interet - impot;

    return {
      type: 'cr',
      fields: { ca, cogs, opex, amort, interet, impot, ebitda, ebit, rn },
      detail: acc,
    };
  }

  // ─── Validation ──────────────────────────────────────────────────────────

  function validate(type, fields) {
    const errors = [], warnings = [];

    if (type === 'bilan') {
      const { immob, actif_circ, treso, cp, dette_lt, dette_ct } = fields;
      const totalActif  = immob + actif_circ + treso;
      const totalPassif = cp + dette_lt + dette_ct;
      if (totalActif === 0)   errors.push('Aucun actif détecté');
      if (totalPassif === 0)  errors.push('Aucun passif détecté');
      if (cp === 0)           warnings.push('Capitaux propres non détectés — vérifier le fichier');
      if (dette_lt === 0)     warnings.push('Aucune dette LT détectée');
      const ecart = Math.abs(totalActif - totalPassif);
      if (ecart > 0 && totalActif > 0) {
        const pct = (ecart / totalActif * 100).toFixed(1);
        if (parseFloat(pct) > 5) {
          warnings.push(`Actif (${totalActif.toLocaleString('fr-FR')}€) ≠ Passif (${totalPassif.toLocaleString('fr-FR')}€) — écart ${pct}%`);
        }
      }
    }

    if (type === 'cr') {
      const { ca, cogs, opex, interet, impot } = fields;
      if (ca === 0)       errors.push('Chiffre d\'affaires non détecté');
      if (cogs === 0)     warnings.push('Coût des ventes non détecté');
      if (opex === 0)     warnings.push('Charges d\'exploitation non détectées');
    }

    return { errors, warnings };
  }

  // ─── Point d'entrée principal ─────────────────────────────────────────────

  function parse(arrayBuffer, filename = '') {
    let workbook;
    try {
      workbook = XLSX.read(arrayBuffer, { type: 'array' });
    } catch (e) {
      return { ok: false, error: 'Fichier illisible : ' + e.message };
    }

    // Choisir la feuille : préférer la première non vide
    const sheetName = workbook.SheetNames[0];
    if (!sheetName) return { ok: false, error: 'Aucune feuille trouvée dans le fichier' };

    const worksheet = workbook.Sheets[sheetName];
    const rows      = extractRows(worksheet);
    const type      = detectType(rows);

    if (type === 'unknown') {
      return {
        ok: false,
        error: 'Type de document non reconnu. Le fichier doit être un Bilan ou un Compte de résultat.',
        raw: rows,
      };
    }

    const parsed = type === 'bilan' ? parseBilan(rows) : parseCR(rows);
    const { errors, warnings } = validate(type, parsed.fields);

    // Charger l'existant depuis localStorage pour merger bilan + CR
    let existing = {};
    try { existing = JSON.parse(localStorage.getItem('finquity_data') || '{}'); } catch(_) {}

    const result = {
      ok: errors.length === 0,
      type,
      sheetName,
      filename,
      parsedAt: new Date().toISOString(),
      fields: parsed.fields,
      detail: parsed.detail,
      raw: rows,
      errors,
      warnings,
    };

    // Merger dans le store global (bilan + CR coexistent)
    // On exclut 'raw' pour ne pas dépasser la limite de localStorage
    const { raw: _raw, ...resultToStore } = result;
    const store = {
      ...existing,
      [type]: resultToStore,
      meta: {
        ...(existing.meta || {}),
        lastUpdated: result.parsedAt,
        company: existing.meta?.company || '',
        sector:  existing.meta?.sector  || '',
        year:    existing.meta?.year    || new Date().getFullYear(),
      },
    };

    const _json = JSON.stringify(store);
    try { localStorage.setItem('finquity_data', _json); } catch(e) {}
    // Fallback : window.name persiste entre pages du même onglet (file://)
    try { window.name = _json; } catch(e) {}

    return result;
  }

  // ─── Helpers pour les autres pages ────────────────────────────────────────

  // Récupère les données FinQuity depuis localStorage
  function load() {
    // 1. Hash de l'URL (passé par goAnalyse — fiable pour file://)
    try {
      const hash = window.location.hash;
      if (hash.startsWith('#d=')) {
        const json = decodeURIComponent(escape(atob(hash.slice(3))));
        const parsed = JSON.parse(json);
        // Re-sauvegarder localement pour les navigations suivantes
        try { localStorage.setItem('finquity_data', json); } catch(_) {}
        try { window.name = json; } catch(_) {}
        return parsed;
      }
    } catch(_) {}
    // 2. localStorage
    try {
      const ls = localStorage.getItem('finquity_data');
      if (ls) return JSON.parse(ls);
    } catch(_) {}
    // 3. window.name
    try {
      if (window.name) return JSON.parse(window.name);
    } catch(_) {}
    return {};
  }

  // Récupère un objet fusionné bilan+CR prêt pour les calculs
  function getFinancialData() {
    const store = load();
    const bilan = store.bilan?.fields || {};
    const cr    = store.cr?.fields    || {};
    return {
      // Bilan
      immob:     bilan.immob     || 0,
      actif_circ:bilan.actif_circ|| 0,
      treso:     bilan.treso     || 0,
      cp:        bilan.cp        || 0,
      dette_lt:  bilan.dette_lt  || 0,
      dette_ct:  bilan.dette_ct  || 0,
      // CR
      ca:        cr.ca      || 0,
      cogs:      cr.cogs    || 0,
      opex:      cr.opex    || 0,
      amort:     cr.amort   || 0,
      interet:   cr.interet || 0,
      impot:     cr.impot   || 0,
      ebitda:    cr.ebitda  || 0,
      ebit:      cr.ebit    || 0,
      rn:        cr.rn      || 0,
      // Meta
      meta: store.meta || {},
    };
  }

  return { parse, load, getFinancialData };

})();