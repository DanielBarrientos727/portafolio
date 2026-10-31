/*
 * Daniel Alejandro Barrientos Soto
 * JavaScript principal del sitio.
 *
 * Funciones:
 * - Controlar la navegación móvil.
 * - Cargar contenido desde archivos JSON.
 * - Actualizar las secciones que tienen datos dinámicos.
 *
 * Sin frameworks ni dependencias externas.
 */

"use strict";

/* -------------------------------------------------------------------------- */
/* Selectores y utilidades                                                     */
/* -------------------------------------------------------------------------- */

const $ = (selector, parent = document) => parent.querySelector(selector);

const isObject = (value) =>
    typeof value === "object" && value !== null && !Array.isArray(value);

const isNonEmptyString = (value) =>
    typeof value === "string" && value.trim().length > 0;

function createElement(tagName, className, text) {
    const element = document.createElement(tagName);

    if (className) {
        element.className = className;
    }

    if (text !== undefined && text !== null) {
        element.textContent = String(text);
    }

    return element;
}

/*
 * Acepta enlaces HTTP(S), correo, teléfono y rutas relativas.
 * Rechaza protocolos ejecutables como javascript: y data:.
 */
function getSafeUrl(value) {
    if (!isNonEmptyString(value)) {
        return null;
    }

    try {
        const url = new URL(value.trim(), document.baseURI);
        const allowedProtocols = ["http:", "https:", "mailto:", "tel:"];

        if (!allowedProtocols.includes(url.protocol)) {
            return null;
        }

        return {
            href: url.href,
            isExternal: ["http:", "https:"].includes(url.protocol)
                && url.origin !== window.location.origin
        };
    } catch {
        return null;
    }
}

function configureLink(link, value) {
    const safeUrl = getSafeUrl(value);

    if (!safeUrl) {
        return false;
    }

    link.href = safeUrl.href;

    if (safeUrl.isExternal) {
        link.target = "_blank";
        link.rel = "noopener noreferrer";
    }

    return true;
}

function appendExternalArrow(parent) {
    const namespace = "http://www.w3.org/2000/svg";
    const svg = document.createElementNS(namespace, "svg");
    const path = document.createElementNS(namespace, "path");

    svg.setAttribute("viewBox", "0 0 24 24");
    svg.setAttribute("aria-hidden", "true");
    svg.setAttribute("focusable", "false");

    path.setAttribute("d", "M5 12h14M13 6l6 6-6 6");

    svg.append(path);
    parent.append(svg);
}

function getStringList(values) {
    if (!Array.isArray(values)) {
        return [];
    }

    return values
        .filter(isNonEmptyString)
        .map((value) => value.trim());
}

/* -------------------------------------------------------------------------- */
/* Navegación móvil                                                            */
/* -------------------------------------------------------------------------- */

const menuButton = $(".menu-button");
const menu = $("#nav-links");
const menuLabel = menuButton ? $("span", menuButton) : null;

function setMenuOpen(isOpen) {
    if (!menu || !menuButton) {
        return;
    }

    menu.classList.toggle("open", isOpen);

    menuButton.setAttribute("aria-expanded", String(isOpen));

    menuButton.setAttribute(
        "aria-label",
        isOpen ? "Cerrar menú de navegación" : "Abrir menú de navegación"
    );

    if (menuLabel) {
        menuLabel.textContent = isOpen ? "Cerrar" : "Menú";
    }
}

function closeMenu() {
    setMenuOpen(false);
}

if (menuButton && menu) {

    menuButton.addEventListener("click", () => {
        const isOpen = menu.classList.contains("open");

        setMenuOpen(!isOpen);
    });

    menu.addEventListener("click", (event) => {
        if (event.target.closest("a")) {
            closeMenu();
        }
    });

    document.addEventListener("click", (event) => {
        const clickedInsideMenu = menu.contains(event.target);
        const clickedButton = menuButton.contains(event.target);

        if (
            menu.classList.contains("open")
            && !clickedInsideMenu
            && !clickedButton
        ) {
            closeMenu();
        }
    });

    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape" && menu.classList.contains("open")) {
            closeMenu();
            menuButton.focus();
        }
    });

    /* El menú móvil no debe permanecer abierto al volver al escritorio. */
    const desktopLayout = window.matchMedia("(min-width: 761px)");

    desktopLayout.addEventListener("change", closeMenu);

    setMenuOpen(false);
}

/* -------------------------------------------------------------------------- */
/* Presentación                                                                */
/* -------------------------------------------------------------------------- */

function setText(selector, value) {
    const target = $(selector);

    if (target && isNonEmptyString(value)) {
        target.textContent = value.trim();
    }
}

function renderProfile(profile) {
    if (!isObject(profile)) {
        return;
    }

    const name = isNonEmptyString(profile.name)
        ? profile.name.trim()
        : null;

    if (name) {
        document.title = `${name} | Sitio personal`;
        setText("#name", name);
    }

    setText("#role", profile.role);
    setText("#intro", profile.intro);
    setText("#about", profile.about);

    renderNow(profile.now);

    if (isObject(profile.tech)) {
        renderTechGroup("#tech-actual", profile.tech.actual);
        renderTechGroup("#tech-aprendiendo", profile.tech.aprendiendo);
        renderTechGroup("#tech-interes", profile.tech.interes);
    }

    renderEducation(profile.education);
}

/* -------------------------------------------------------------------------- */
/* Enlaces principales                                                         */
/* -------------------------------------------------------------------------- */

function renderFeaturedLinks(links) {
    const target = $("#hero-links");

    if (!target || !Array.isArray(links)) {
        return;
    }

    const featuredLinks = links.filter((item) =>
        isObject(item)
        && item.featured === true
        && isNonEmptyString(item.label)
        && getSafeUrl(item.url)
    );

    if (featuredLinks.length === 0) {
        return;
    }

    const fragment = document.createDocumentFragment();

    featuredLinks.forEach((item) => {
        const link = createElement(
            "a",
            "text-link",
            `${item.label.trim()} ↗`
        );

        if (configureLink(link, item.url)) {
            fragment.append(link);
        }
    });

    if (fragment.childNodes.length > 0) {
        target.replaceChildren(fragment);
    }
}

function renderNetworkLinks(links) {
    const target = $("#links-list");

    if (!target || !Array.isArray(links)) {
        return;
    }

    const validLinks = links.filter((item) =>
        isObject(item)
        && isNonEmptyString(item.label)
        && getSafeUrl(item.url)
    );

    if (validLinks.length === 0) {
        return;
    }

    const fragment = document.createDocumentFragment();

    validLinks.forEach((item) => {
        const link = createElement("a", "network");
        const main = createElement("span");

        const label = createElement(
            "strong",
            "",
            item.label.trim()
        );

        const handleText = isNonEmptyString(item.handle)
            ? `${item.handle.trim()} ↗`
            : "Abrir ↗";

        const handle = createElement(
            "span",
            "handle",
            handleText
        );

        appendExternalArrow(main);

        main.append(label);
        link.append(main, handle);

        if (configureLink(link, item.url)) {
            fragment.append(link);
        }
    });

    if (fragment.childNodes.length > 0) {
        target.replaceChildren(fragment);
    }
}

/* -------------------------------------------------------------------------- */
/* Sección «Ahora»                                                             */
/* -------------------------------------------------------------------------- */

function renderNow(items) {
    const target = $("#now-list");
    const validItems = getStringList(items);

    if (!target || validItems.length === 0) {
        return;
    }

    const fragment = document.createDocumentFragment();

    validItems.forEach((item) => {
        fragment.append(
            createElement("li", "", item)
        );
    });

    target.replaceChildren(fragment);
}

/* -------------------------------------------------------------------------- */
/* Tecnologías                                                                 */
/* -------------------------------------------------------------------------- */

function renderTechGroup(selector, items) {
    const target = $(selector);
    const validItems = getStringList(items);

    if (!target || validItems.length === 0) {
        return;
    }

    const fragment = document.createDocumentFragment();

    validItems.forEach((item) => {
        fragment.append(
            createElement("span", "tech-item", item)
        );
    });

    target.replaceChildren(fragment);
}

/* -------------------------------------------------------------------------- */
/* Formación académica                                                         */
/* -------------------------------------------------------------------------- */

function renderEducation(education) {
    const target = $("#education-list");

    if (!target || !Array.isArray(education)) {
        return;
    }

    const validEntries = education.filter((item) =>
        isObject(item) && isNonEmptyString(item.program)
    );

    if (validEntries.length === 0) {
        return;
    }

    const fragment = document.createDocumentFragment();

    validEntries.forEach((item) => {
        const entry = createElement("li");

        const program = createElement(
            "h3",
            "",
            item.program.trim()
        );

        entry.append(program);

        if (isNonEmptyString(item.institution)) {
            entry.append(
                createElement(
                    "span",
                    "institution",
                    item.institution.trim()
                )
            );
        }

        const metadata = [item.period, item.status]
            .filter(isNonEmptyString)
            .map((value) => value.trim());

        if (metadata.length > 0) {
            entry.append(
                createElement(
                    "span",
                    "meta",
                    metadata.join(" · ")
                )
            );
        }

        fragment.append(entry);
    });

    target.replaceChildren(fragment);
}

/* -------------------------------------------------------------------------- */
/* Proyectos                                                                   */
/* -------------------------------------------------------------------------- */

function renderProjects(projects) {
    /* Coincide con el contenedor .projects-list del HTML actual. */
    const target = $("#projects-list") || $(".projects-list");

    if (!target || !Array.isArray(projects)) {
        return;
    }

    const validProjects = projects.filter((item) =>
        isObject(item) && isNonEmptyString(item.name)
    );

    if (validProjects.length === 0) {
        return;
    }

    const fragment = document.createDocumentFragment();

    validProjects.forEach((project) => {
        const article = createElement("article", "project-card");

        const title = createElement(
            "h3",
            "",
            project.name.trim()
        );

        article.append(title);

        if (isNonEmptyString(project.description)) {
            article.append(
                createElement(
                    "p",
                    "",
                    project.description.trim()
                )
            );
        }

        const technologies = getStringList(project.tech);

        if (technologies.length > 0) {
            article.append(
                createElement(
                    "p",
                    "project-technologies",
                    technologies.join(" · ")
                )
            );
        }

        const footer = createElement("div", "project-footer");
        let hasFooterContent = false;

        if (isNonEmptyString(project.status)) {
            footer.append(
                createElement(
                    "span",
                    "status",
                    project.status.trim()
                )
            );

            hasFooterContent = true;
        }

        if (isNonEmptyString(project.url)) {
            const projectLink = createElement(
                "a",
                "",
                "Ver proyecto ↗"
            );

            if (configureLink(projectLink, project.url)) {
                footer.append(projectLink);
                hasFooterContent = true;
            }
        }

        if (hasFooterContent) {
            article.append(footer);
        }

        fragment.append(article);
    });

    target.replaceChildren(fragment);
}

/* -------------------------------------------------------------------------- */
/* Documentos                                                                  */
/* -------------------------------------------------------------------------- */

function renderDocuments(documents) {
    /* El HTML actual utiliza .documents y contiene el CV como respaldo. */
    const target = $("#documents-list") || $(".documents");

    if (!target || !Array.isArray(documents)) {
        return;
    }

    const validDocuments = documents.filter((item) =>
        isObject(item)
        && isNonEmptyString(item.name)
        && getSafeUrl(item.file)
    );

    if (validDocuments.length === 0) {
        return;
    }

    const fragment = document.createDocumentFragment();

    validDocuments.forEach((item) => {
        const link = createElement("a", "document-link");
        const details = createElement("span");

        const name = createElement(
            "strong",
            "",
            item.name.trim()
        );

        const description = createElement(
            "small",
            "",
            isNonEmptyString(item.type)
                ? item.type.trim()
                : "Documento"
        );

        const arrow = createElement("span", "", "↗");

        details.append(name, description);
        link.append(details, arrow);

        if (item.download === true) {
            link.download = "";
        }

        if (configureLink(link, item.file)) {
            fragment.append(link);
        }
    });

    if (fragment.childNodes.length > 0) {
        target.replaceChildren(fragment);
    }
}

/* -------------------------------------------------------------------------- */
/* Carga de datos                                                              */
/* -------------------------------------------------------------------------- */

async function fetchJson(path) {
    const response = await fetch(path, {
        cache: "no-cache"
    });

    if (!response.ok) {
        throw new Error(
            `Error HTTP ${response.status}: ${path}`
        );
    }

    return response.json();
}

/* -------------------------------------------------------------------------- */
/* Mensajes de error                                                           */
/* -------------------------------------------------------------------------- */

function showLoadError(failedFiles) {
    const hero = $(".hero");

    if (
        !hero
        || failedFiles.length === 0
        || $(".load-error", hero)
    ) {
        return;
    }

    const message = createElement(
        "p",
        "load-error",
        "No se pudieron actualizar algunos contenidos. La información disponible en la página sigue visible."
    );

    message.setAttribute("role", "status");
    message.setAttribute("aria-live", "polite");

    hero.append(message);

    failedFiles.forEach(({ path, error }) => {
        console.warn(`No se pudo cargar ${path}.`, error);
    });
}

/* -------------------------------------------------------------------------- */
/* Inicialización                                                              */
/* -------------------------------------------------------------------------- */

async function loadSite() {
    const sources = [
        {
            key: "profile",
            path: "./data/profile.json",
            validate: isObject
        },
        {
            key: "projects",
            path: "./data/projects.json",
            validate: Array.isArray
        },
        {
            key: "links",
            path: "./data/links.json",
            validate: Array.isArray
        },
        {
            key: "documents",
            path: "./data/documents.json",
            validate: Array.isArray
        }
    ];

    const results = await Promise.allSettled(
        sources.map(({ path }) => fetchJson(path))
    );

    const data = {};
    const failedFiles = [];

    results.forEach((result, index) => {
        const source = sources[index];

        if (result.status === "rejected") {
            failedFiles.push({
                path: source.path,
                error: result.reason
            });

            return;
        }

        if (!source.validate(result.value)) {
            failedFiles.push({
                path: source.path,
                error: new TypeError(
                    "El archivo tiene una estructura inesperada."
                )
            });

            return;
        }

        data[source.key] = result.value;
    });

    /* Cada archivo se procesa de forma independiente. */
    if (data.profile) {
        renderProfile(data.profile);
    }

    if (data.projects) {
        renderProjects(data.projects);
    }

    if (data.links) {
        renderFeaturedLinks(data.links);
        renderNetworkLinks(data.links);
    }

    if (data.documents) {
        renderDocuments(data.documents);
    }

    showLoadError(failedFiles);
}

/* -------------------------------------------------------------------------- */
/* Inicio del sitio                                                            */
/* -------------------------------------------------------------------------- */

loadSite().catch((error) => {
    console.error(
        "Ocurrió un error al iniciar el sitio.",
        error
    );
});