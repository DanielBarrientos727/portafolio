/* =========================================================
   PORTAFOLIO PERSONAL
   Daniel Alejandro Barrientos Soto

   JavaScript principal del sitio.

   Responsabilidades:
   - Controlar el menú móvil.
   - Cargar información desde archivos JSON.
   - Renderizar formación, tecnologías, proyectos,
     documentos y enlaces.
   - Manejar errores básicos de carga.
   ========================================================= */


/* =========================================================
   UTILIDAD PARA SELECCIONAR ELEMENTOS
   ========================================================= */

/**
 * Atajo para document.querySelector().
 *
 * @param {string} selector
 * @returns {Element|null}
 */
const $ = (selector) => document.querySelector(selector);


/* =========================================================
   ICONO DE ENLACE
   ========================================================= */

const externalIcon = `
  <svg
    viewBox="0 0 24 24"
    aria-hidden="true"
    focusable="false"
  >
    <path d="M5 12h14M13 6l6 6-6 6"></path>
  </svg>
`;


/* =========================================================
   MENÚ DE NAVEGACIÓN
   ========================================================= */

const menuButton = $(".menu-button");
const menu = $("#nav-links");


/**
 * Cierra el menú móvil.
 */
function closeMenu() {
  if (!menu || !menuButton) {
    return;
  }

  menu.classList.remove("open");

  menuButton.setAttribute(
    "aria-expanded",
    "false"
  );

  menuButton.textContent = "Menú";
}


/**
 * Abre o cierra el menú móvil.
 */
function toggleMenu() {
  if (!menu || !menuButton) {
    return;
  }

  const isOpen = menu.classList.toggle("open");

  menuButton.setAttribute(
    "aria-expanded",
    String(isOpen)
  );

  menuButton.textContent = isOpen
    ? "Cerrar"
    : "Menú";
}


if (menuButton) {
  menuButton.addEventListener(
    "click",
    toggleMenu
  );
}


if (menu) {
  menu.querySelectorAll("a").forEach((link) => {
    link.addEventListener(
      "click",
      closeMenu
    );
  });
}


/* =========================================================
   SEGURIDAD BÁSICA PARA TEXTO DINÁMICO
   ========================================================= */

/**
 * Escapa caracteres especiales antes de introducir
 * contenido proveniente de JSON mediante innerHTML.
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
   ENLACES DESTACADOS
   ========================================================= */

/**
 * Renderiza enlaces destacados del hero.
 *
 * @param {string} selector
 * @param {Array} links
 */
function addFeaturedLinks(selector, links = []) {
  const target = $(selector);

  if (!target) {
    return;
  }

  target.innerHTML = links
    .filter((link) => link && link.url && link.label)
    .map((link) => {
      const label = escapeHtml(link.label);
      const url = escapeHtml(link.url);

      return `
        <a
          class="text-link"
          href="${url}"
          target="_blank"
          rel="noopener noreferrer"
        >
          ${label} ↗
        </a>
      `;
    })
    .join("");
}


/* =========================================================
   ENLACES DE INTERNET
   ========================================================= */

/**
 * Renderiza la lista completa de enlaces.
 *
 * @param {string} selector
 * @param {Array} links
 */
function addNetworkLinks(selector, links = []) {
  const target = $(selector);

  if (!target) {
    return;
  }

  const validLinks = links.filter(
    (link) =>
      link &&
      link.url &&
      link.label
  );

  if (!validLinks.length) {
    target.innerHTML = `
      <p class="empty">
        Aún no hay enlaces públicos configurados.
      </p>
    `;

    return;
  }

  target.innerHTML = validLinks
    .map((link) => {
      const label = escapeHtml(link.label);
      const handle = escapeHtml(link.handle ?? "");
      const url = escapeHtml(link.url);

      return `
        <a
          class="network"
          href="${url}"
          target="_blank"
          rel="noopener noreferrer"
        >
          <span>
            ${externalIcon}
            <strong>${label}</strong>
          </span>

          <span class="handle">
            ${handle} ↗
          </span>
        </a>
      `;
    })
    .join("");
}


/* =========================================================
   TECNOLOGÍAS
   ========================================================= */

/**
 * Renderiza un grupo de tecnologías.
 *
 * @param {string} selector
 * @param {Array} items
 */
function renderTechGroup(selector, items = []) {
  const target = $(selector);

  if (!target) {
    return;
  }

  if (!Array.isArray(items) || items.length === 0) {
    target.textContent = "Por definir.";
    return;
  }

  target.innerHTML = items
    .map(
      (item) => `
        <span class="tech-item">
          ${escapeHtml(item)}
        </span>
      `
    )
    .join("");
}


/* =========================================================
   LISTA "AHORA"
   ========================================================= */

/**
 * Renderiza lo que Daniel está haciendo actualmente.
 *
 * @param {Array} items
 */
function renderNow(items = []) {
  const target = $("#now-list");

  if (!target) {
    return;
  }

  if (!Array.isArray(items) || items.length === 0) {
    target.innerHTML = `
      <li>
        Información pendiente de actualizar.
      </li>
    `;

    return;
  }

  target.innerHTML = items
    .map(
      (item) => `
        <li>
          ${escapeHtml(item)}
        </li>
      `
    )
    .join("");
}


/* =========================================================
   FORMACIÓN
   ========================================================= */

/**
 * Renderiza la trayectoria académica.
 *
 * @param {Array} education
 */
function renderEducation(education = []) {
  const target = $("#education-list");

  if (!target) {
    return;
  }

  if (!Array.isArray(education) || education.length === 0) {
    target.innerHTML = `
      <li>
        <h3>Formación en actualización</h3>
        <span class="institution">
          Información académica pendiente.
        </span>
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

          <span class="institution">
            ${institution}
          </span>

          <span class="meta">
            ${period} · ${status}
          </span>
        </li>
      `;
    })
    .join("");
}


/* =========================================================
   PROYECTOS
   ========================================================= */

/**
 * Renderiza proyectos.
 *
 * Los proyectos deben existir realmente en projects.json.
 * No se generan proyectos ficticios para rellenar la página.
 *
 * @param {Array} projects
 */
function renderProjects(projects = []) {
  const target = $("#projects-list");

  if (!target) {
    return;
  }

  if (!Array.isArray(projects) || projects.length === 0) {
    target.innerHTML = `
      <p class="empty">
        Todavía no hay proyectos públicos configurados.
      </p>
    `;

    return;
  }

  target.innerHTML = projects
    .map((project) => {
      const name = escapeHtml(project.name);
      const description = escapeHtml(project.description);
      const status = escapeHtml(project.status);
      const url = escapeHtml(project.url);

      const technologies = Array.isArray(project.tech)
        ? project.tech
            .map((tech) => escapeHtml(tech))
            .join(" · ")
        : "";

      const technologyMarkup = technologies
        ? `
          <span class="project-meta">
            ${technologies}
          </span>
        `
        : "";

      const githubMarkup = project.url
        ? `
          <a
            href="${url}"
            target="_blank"
            rel="noopener noreferrer"
          >
            GitHub ↗
          </a>
        `
        : "";

      return `
        <article class="project">

          <h3>
            ${name}
          </h3>

          ${technologyMarkup}

          <p>
            ${description}
          </p>

          <div class="project-footer">

            <span class="status">
              ${status}
            </span>

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
 * Renderiza documentos disponibles.
 *
 * @param {Array} documents
 */
function renderDocuments(documents = []) {
  const target = $("#documents-list");

  if (!target) {
    return;
  }

  const validDocuments = documents.filter(
    (document) =>
      document &&
      document.file &&
      document.name
  );

  if (!validDocuments.length) {
    target.innerHTML = `
      <p class="empty">
        Aún no hay documentos públicos configurados.
      </p>
    `;

    return;
  }

  target.innerHTML = validDocuments
    .map((document) => {
      const name = escapeHtml(document.name);
      const type = escapeHtml(document.type ?? "");
      const file = escapeHtml(document.file);

      return `
        <div class="document">

          <span>
            <strong>
              ${name}
            </strong>

            <small>
              ${type}
            </small>
          </span>

          <a
            href="${file}"
            target="_blank"
            rel="noopener noreferrer"
          >
            Ver / descargar ↗
          </a>

        </div>
      `;
    })
    .join("");
}


/* =========================================================
   CARGA DE ARCHIVOS JSON
   ========================================================= */

/**
 * Descarga un archivo JSON y comprueba
 * que la respuesta HTTP haya sido correcta.
 *
 * @param {string} path
 * @returns {Promise<any>}
 */
async function fetchJson(path) {
  const response = await fetch(path, {
    cache: "no-cache"
  });

  if (!response.ok) {
    throw new Error(
      `No se pudo cargar ${path}: HTTP ${response.status}`
    );
  }

  return response.json();
}


/* =========================================================
   MENSAJE DE ERROR
   ========================================================= */

/**
 * Muestra un mensaje cuando falla la carga
 * de los datos del sitio.
 */
function showLoadError() {
  const hero = $(".hero");

  if (!hero) {
    return;
  }

  const existingError = $(".load-error");

  if (existingError) {
    return;
  }

  const message = document.createElement("p");

  message.className = "load-error";

  message.textContent =
    "No se pudieron cargar algunos datos del sitio.";

  hero.appendChild(message);
}


/* =========================================================
   CARGA PRINCIPAL DEL SITIO
   ========================================================= */

async function loadSite() {
  try {

    /* -----------------------------------------------------
       Cargar todos los datos en paralelo
       ----------------------------------------------------- */

    const [
      profile,
      projects,
      links,
      documents
    ] = await Promise.all([
      fetchJson("/data/profile.json"),
      fetchJson("/data/projects.json"),
      fetchJson("/data/links.json"),
      fetchJson("/data/documents.json")
    ]);


    /* -----------------------------------------------------
       Información básica
       ----------------------------------------------------- */

    if (profile?.name) {
      document.title =
        `${profile.name} | Portafolio`;
    }


    const name = $("#name");
    const role = $("#role");
    const intro = $("#intro");
    const about = $("#about");


    if (name) {
      name.textContent =
        profile?.name ?? "Daniel Alejandro Barrientos Soto";
    }


    if (role) {
      role.textContent =
        profile?.role ?? "Estudiante de Ingeniería de Sistemas y Computación";
    }


    if (intro) {
      intro.textContent =
        profile?.intro ?? "";
    }


    if (about) {
      about.textContent =
        profile?.about ?? "";
    }


    /* -----------------------------------------------------
       Secciones dinámicas
       ----------------------------------------------------- */

    addFeaturedLinks(
      "#hero-links",
      links.filter((link) => link.featured)
    );


    renderNow(
      profile?.now ?? []
    );


    renderTechGroup(
      "#tech-actual",
      profile?.tech?.actual ?? []
    );


    renderTechGroup(
      "#tech-aprendiendo",
      profile?.tech?.aprendiendo ?? []
    );


    renderTechGroup(
      "#tech-interes",
      profile?.tech?.interes ?? []
    );


    renderEducation(
      profile?.education ?? []
    );


    renderProjects(
      projects
    );


    renderDocuments(
      documents
    );


    addNetworkLinks(
      "#links-list",
      links
    );

  } catch (error) {

    console.error(
      "No se pudieron cargar los datos del sitio.",
      error
    );

    showLoadError();
  }
}


/* =========================================================
   INICIALIZACIÓN
   ========================================================= */

loadSite();