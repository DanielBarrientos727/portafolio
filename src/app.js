/* =========================================================
   PORTAFOLIO PERSONAL
   Daniel Alejandro Barrientos Soto

   JavaScript principal del sitio.

   Responsabilidades:
   - Controlar el menú móvil (hamburguesa con Font Awesome).
   - Cargar información desde archivos JSON.
   - Renderizar formación, tecnologías, proyectos,
     documentos y enlaces.
   - Manejar errores básicos de carga.
   ========================================================= */


/* Atajo para no escribir document.querySelector todo el tiempo */
const $ = (selector) => document.querySelector(selector);


/* Ícono de flecha para enlaces externos (usado en "Internet") */
const externalIcon = `
  <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
    <path d="M5 12h14M13 6l6 6-6 6"></path>
  </svg>
`;


/* =========================================================
   MENÚ DE NAVEGACIÓN (hamburguesa)
   ========================================================= */

const menuButton = $(".menu-button");
const menu = $("#nav-links");

/* Guardamos el ícono y el texto del botón por separado.
   Antes se hacía menuButton.textContent = "..." y eso borraba
   el ícono de Font Awesome cada vez que se abría/cerraba el
   menú. Actualizando solo el ícono y el <span> se evita eso. */
const menuIcon = menuButton?.querySelector("i");
const menuLabel = menuButton?.querySelector("span");

/**
 * Aplica el estado abierto/cerrado al menú, al botón,
 * al ícono (barras ↔ equis) y al texto del botón.
 *
 * @param {boolean} isOpen
 */
function setMenuState(isOpen) {
  if (!menu || !menuButton) return;

  menu.classList.toggle("open", isOpen);
  menuButton.setAttribute("aria-expanded", String(isOpen));

  if (menuIcon) {
    menuIcon.className = isOpen ? "fa-solid fa-xmark" : "fa-solid fa-bars";
  }

  if (menuLabel) {
    menuLabel.textContent = isOpen ? "Cerrar" : "Menú";
  }
}

function closeMenu() {
  setMenuState(false);
}

function toggleMenu() {
  if (!menu) return;
  setMenuState(!menu.classList.contains("open"));
}

if (menuButton) {
  menuButton.addEventListener("click", toggleMenu);
}

/* Al hacer clic en un enlace del menú (móvil), se cierra solo */
if (menu) {
  menu.querySelectorAll("a").forEach((link) => {
    link.addEventListener("click", closeMenu);
  });
}


/* =========================================================
   SEGURIDAD BÁSICA PARA TEXTO DINÁMICO
   ========================================================= */

/**
 * Escapa caracteres especiales antes de insertar texto que
 * viene de los archivos JSON, para evitar HTML/JS no deseado.
 *
 * @param {unknown} value
 * @returns {string}
 */
function escapeHtml(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}


/* =========================================================
   ENLACES DESTACADOS DEL HERO
   ========================================================= */

/**
 * Renderiza los enlaces marcados como "featured" en la portada.
 *
 * @param {string} selector
 * @param {Array} links
 */
function addFeaturedLinks(selector, links = []) {
  const target = $(selector);
  if (!target) return;

  const validLinks = links.filter(
    (link) => link && link.url && link.label && link.featured === true
  );

  target.innerHTML = validLinks
    .map((link) => {
      const label = escapeHtml(link.label);
      const url = escapeHtml(link.url);

      return `<a class="text-link" href="${url}" target="_blank" rel="noopener noreferrer">${label} ↗</a>`;
    })
    .join("");
}


/* =========================================================
   ENLACES DE INTERNET
   ========================================================= */

/**
 * Renderiza todos los enlaces públicos (redes, sitios, etc.).
 *
 * @param {string} selector
 * @param {Array} links
 */
function addNetworkLinks(selector, links = []) {
  const target = $(selector);
  if (!target) return;

  const validLinks = links.filter((link) => link && link.url && link.label);

  if (!validLinks.length) {
    target.innerHTML = `<p class="empty">Aún no hay enlaces públicos configurados.</p>`;
    return;
  }

  target.innerHTML = validLinks
    .map((link) => {
      const label = escapeHtml(link.label);
      const handle = escapeHtml(link.handle ?? "");
      const url = escapeHtml(link.url);

      return `
        <a class="network" href="${url}" target="_blank" rel="noopener noreferrer">
          <span>${externalIcon}<strong>${label}</strong></span>
          <span class="handle">${handle} ↗</span>
        </a>
      `;
    })
    .join("");
}


/* =========================================================
   TECNOLOGÍAS
   ========================================================= */

/**
 * Renderiza un grupo de tecnologías (base actual, en
 * desarrollo, áreas de interés).
 *
 * @param {string} selector
 * @param {Array} items
 */
function renderTechGroup(selector, items = []) {
  const target = $(selector);
  if (!target) return;

  if (!Array.isArray(items) || items.length === 0) {
    target.textContent = "Por definir.";
    return;
  }

  target.innerHTML = items
    .map((item) => `<span class="tech-item">${escapeHtml(item)}</span>`)
    .join("");
}


/* =========================================================
   SECCIÓN "AHORA"
   ========================================================= */

/**
 * Renderiza las actividades actuales.
 *
 * @param {Array} items
 */
function renderNow(items = []) {
  const target = $("#now-list");
  if (!target) return;

  if (!Array.isArray(items) || items.length === 0) {
    target.innerHTML = `<li>Información pendiente de actualizar.</li>`;
    return;
  }

  target.innerHTML = items.map((item) => `<li>${escapeHtml(item)}</li>`).join("");
}


/* =========================================================
   FORMACIÓN ACADÉMICA
   ========================================================= */

/**
 * Renderiza la línea de tiempo de formación académica.
 *
 * @param {Array} education
 */
function renderEducation(education = []) {
  const target = $("#education-list");
  if (!target) return;

  if (!Array.isArray(education) || education.length === 0) {
    target.innerHTML = `
      <li>
        <h3>Formación en actualización</h3>
        <span class="institution">Información académica pendiente.</span>
      </li>
    `;
    return;
  }

  target.innerHTML = education
    .map((item) => {
      const program = escapeHtml(item.program);
      const institution = escapeHtml(item.institution);
      const period = escapeHtml(item.period);
      const status = escapeHtml(item.status);

      return `
        <li>
          <h3>${program}</h3>
          <span class="institution">${institution}</span>
          <span class="meta">${period} · ${status}</span>
        </li>
      `;
    })
    .join("");
}


/* =========================================================
   PROYECTOS
   Nota: hoy el sitio no tiene una sección con id="projects-list",
   así que esta función no hace nada por ahora (el "if" de abajo
   la protege). Cuando agregues esa sección, empieza a funcionar
   sola, sin tocar este archivo.
   ========================================================= */

/**
 * Renderiza los proyectos públicos. Solo se muestran los que
 * existen realmente en projects.json, nunca proyectos inventados.
 *
 * @param {Array} projects
 */
function renderProjects(projects = []) {
  const target = $("#projects-list");
  if (!target) return;

  if (!Array.isArray(projects) || projects.length === 0) {
    target.innerHTML = `<p class="empty">Todavía no hay proyectos públicos configurados.</p>`;
    return;
  }

  target.innerHTML = projects
    .map((project) => {
      const name = escapeHtml(project.name);
      const description = escapeHtml(project.description);
      const status = escapeHtml(project.status);

      const technologies = Array.isArray(project.tech)
        ? project.tech.map((tech) => escapeHtml(tech)).join(" · ")
        : "";

      const technologyMarkup = technologies
        ? `<span class="project-meta">${technologies}</span>`
        : "";

      const githubMarkup = project.url
        ? `<a href="${escapeHtml(project.url)}" target="_blank" rel="noopener noreferrer">GitHub ↗</a>`
        : "";

      return `
        <article class="project">
          <h3>${name}</h3>
          ${technologyMarkup}
          <p>${description}</p>
          <div class="project-footer">
            <span class="status">${status}</span>
            ${githubMarkup}
          </div>
        </article>
      `;
    })
    .join("");
}


/* =========================================================
   DOCUMENTOS
   ========================================================= */

/**
 * Renderiza los documentos públicos (CV, certificados, etc.).
 *
 * @param {Array} docs
 */
function renderDocuments(docs = []) {
  const target = $("#documents-list");
  if (!target) return;

  /* Usamos "doc" y no "document" para no tapar el "document"
     global del navegador (el que usa $() en todo este archivo). */
  const validDocs = Array.isArray(docs)
    ? docs.filter((doc) => doc && doc.file && doc.name)
    : [];

  if (!validDocs.length) {
    target.innerHTML = `<p class="empty">Aún no hay documentos públicos configurados.</p>`;
    return;
  }

  target.innerHTML = validDocs
    .map((doc) => {
      const name = escapeHtml(doc.name);
      const type = escapeHtml(doc.type ?? "");
      const file = escapeHtml(doc.file);

      return `
        <div class="document">
          <span><strong>${name}</strong><small>${type}</small></span>
          <a href="${file}" target="_blank" rel="noopener noreferrer">Ver / descargar ↗</a>
        </div>
      `;
    })
    .join("");
}


/* =========================================================
   CARGA DE ARCHIVOS JSON
   ========================================================= */

/**
 * Descarga un archivo JSON y valida que la respuesta HTTP
 * haya sido correcta. Las rutas son relativas al index.html.
 *
 * @param {string} path
 * @returns {Promise<any>}
 */
async function fetchJson(path) {
  const response = await fetch(path, { cache: "no-cache" });

  if (!response.ok) {
    throw new Error(`No se pudo cargar ${path}: HTTP ${response.status}`);
  }

  return response.json();
}


/* =========================================================
   MENSAJE DE ERROR
   ========================================================= */

/**
 * Muestra un aviso en el hero cuando los datos no se
 * pudieron cargar (por ejemplo, sin conexión).
 */
function showLoadError() {
  const hero = $(".hero");
  if (!hero || $(".load-error")) return;

  const message = document.createElement("p");
  message.className = "load-error";
  message.textContent = "No se pudieron cargar algunos datos del sitio.";

  hero.appendChild(message);
}


/* =========================================================
   CARGA PRINCIPAL DEL SITIO
   ========================================================= */

async function loadSite() {
  try {
    /* Las cuatro fuentes de datos se piden al mismo tiempo */
    const [profile, projects, links, documents] = await Promise.all([
      fetchJson("./data/profile.json"),
      fetchJson("./data/projects.json"),
      fetchJson("./data/links.json"),
      fetchJson("./data/documents.json"),
    ]);

    /* --- Información principal --- */
    if (profile?.name) {
      document.title = `${profile.name} | Portafolio`;
    }

    const name = $("#name");
    const role = $("#role");
    const intro = $("#intro");
    const about = $("#about");

    if (name) name.textContent = profile?.name ?? "Daniel Alejandro Barrientos Soto";
    if (role) role.textContent = profile?.role ?? "Estudiante de Ingeniería de Sistemas y Computación";
    if (intro) intro.textContent = profile?.intro ?? "";
    if (about) about.textContent = profile?.about ?? "";

    /* --- Resto de secciones --- */
    addFeaturedLinks("#hero-links", Array.isArray(links) ? links : []);
    renderNow(profile?.now ?? []);

    renderTechGroup("#tech-actual", profile?.tech?.actual ?? []);
    renderTechGroup("#tech-aprendiendo", profile?.tech?.aprendiendo ?? []);
    renderTechGroup("#tech-interes", profile?.tech?.interes ?? []);

    renderEducation(profile?.education ?? []);
    renderProjects(projects);
    renderDocuments(documents);

    addNetworkLinks("#links-list", Array.isArray(links) ? links : []);

  } catch (error) {
    console.error("No se pudieron cargar los datos del sitio.", error);
    showLoadError();
  }
}

loadSite();