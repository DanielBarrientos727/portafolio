
/*
 * Daniel Alejandro Barrientos Soto
 * JavaScript principal del portafolio.
 */

"use strict";

// ── Utilidades ─────────────────────────────────────

const $ = (selector, parent = document) =>
    parent.querySelector(selector);

const isObject = value =>
    typeof value === "object" &&
    value !== null &&
    !Array.isArray(value);

const isNonEmptyString = value =>
    typeof value === "string" &&
    value.trim().length > 0;

function createElement(tag, className = "", text = "") {
    const element = document.createElement(tag);

    if (className) element.className = className;
    if (text) element.textContent = text;

    return element;
}

// Comprueba que los enlaces utilicen protocolos permitidos.
function getSafeUrl(value) {
    if (!isNonEmptyString(value)) return null;

    try {
        const url = new URL(value.trim(), document.baseURI);
        const protocols = ["http:", "https:", "mailto:", "tel:"];

        if (!protocols.includes(url.protocol)) return null;

        return {
            href: url.href,
            external:
                ["http:", "https:"].includes(url.protocol) &&
                url.origin !== window.location.origin
        };
    } catch {
        return null;
    }
}

function configureLink(link, value) {
    const safeUrl = getSafeUrl(value);

    if (!safeUrl) return false;

    link.href = safeUrl.href;

    if (safeUrl.external) {
        link.target = "_blank";
        link.rel = "noopener noreferrer";
    }

    return true;
}

function getStringList(values) {
    if (!Array.isArray(values)) return [];

    return values
        .filter(isNonEmptyString)
        .map(value => value.trim());
}

// ── Navegación móvil ───────────────────────────────

const menuButton = $(".menu-button");
const menu = $("#nav-links");
const menuLabel = menuButton?.querySelector("span");

function setMenuOpen(open) {
    if (!menu || !menuButton) return;

    menu.classList.toggle("open", open);
    menuButton.setAttribute("aria-expanded", String(open));

    menuButton.setAttribute(
        "aria-label",
        open ? "Cerrar menú de navegación" : "Abrir menú de navegación"
    );

    if (menuLabel) {
        menuLabel.textContent = open ? "Cerrar" : "Menú";
    }
}

if (menuButton && menu) {

    menuButton.addEventListener("click", () => {
        setMenuOpen(!menu.classList.contains("open"));
    });

    // Cierra el menú al seleccionar una sección.
    menu.addEventListener("click", event => {
        if (event.target.closest("a")) {
            setMenuOpen(false);
        }
    });

    // Cierra el menú al hacer clic fuera.
    document.addEventListener("click", event => {
        if (
            menu.classList.contains("open") &&
            !menu.contains(event.target) &&
            !menuButton.contains(event.target)
        ) {
            setMenuOpen(false);
        }
    });

    // Permite cerrar el menú con Escape.
    document.addEventListener("keydown", event => {
        if (event.key === "Escape" && menu.classList.contains("open")) {
            setMenuOpen(false);
            menuButton.focus();
        }
    });

    // Cierra el menú al cambiar a escritorio.
    window.matchMedia("(min-width: 761px)")
        .addEventListener("change", () => setMenuOpen(false));

    setMenuOpen(false);
}

// ── Presentación ───────────────────────────────────

function setText(selector, value) {
    const target = $(selector);

    if (target && isNonEmptyString(value)) {
        target.textContent = value.trim();
    }
}

function renderItems(selector, items, tag, className = "") {
    const target = $(selector);
    const values = getStringList(items);

    if (!target || values.length === 0) return;

    const fragment = document.createDocumentFragment();

    values.forEach(value => {
        fragment.append(createElement(tag, className, value));
    });

    target.replaceChildren(fragment);
}

function renderProfile(profile) {
    if (!isObject(profile)) return;

    if (isNonEmptyString(profile.name)) {
        const name = profile.name.trim();

        document.title = `${name} | Portafolio`;
        setText("#name", name);
    }

    setText("#role", profile.role);
    setText("#intro", profile.intro);
    setText("#about", profile.about);

    renderItems("#now-list", profile.now, "li");

    if (isObject(profile.tech)) {
        renderItems("#tech-actual", profile.tech.actual, "span", "tech-item");

        renderItems(
            "#tech-aprendiendo",
            profile.tech.aprendiendo,
            "span",
            "tech-item"
        );

        renderItems(
            "#tech-interes",
            profile.tech.interes,
            "span",
            "tech-item"
        );
    }
}

// ── Enlaces destacados ────────────────────────────

function renderFeaturedLinks(links) {
    const target = $("#hero-links");

    if (!target || !Array.isArray(links)) return;

    const featured = links.filter(item =>
        isObject(item) &&
        item.featured === true &&
        isNonEmptyString(item.label) &&
        getSafeUrl(item.url)
    );

    if (featured.length === 0) return;

    const fragment = document.createDocumentFragment();

    featured.forEach(item => {
        const link = createElement(
            "a",
            "text-link",
            `${item.label.trim()} ↗`
        );

        if (configureLink(link, item.url)) {
            fragment.append(link);
        }
    });

    target.replaceChildren(fragment);
}

// ── Enlaces de contacto ────────────────────────────

function addLinkIcon(parent) {
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

function renderNetworkLinks(links) {
    const target = $("#links-list");

    if (!target || !Array.isArray(links)) return;

    const validLinks = links.filter(item =>
        isObject(item) &&
        isNonEmptyString(item.label) &&
        getSafeUrl(item.url)
    );

    if (validLinks.length === 0) return;

    const fragment = document.createDocumentFragment();

    validLinks.forEach(item => {
        const link = createElement("a", "network");
        const main = createElement("span");

        const label = createElement(
            "strong",
            "",
            item.label.trim()
        );

        const handle = createElement(
            "span",
            "handle",
            isNonEmptyString(item.handle)
                ? `${item.handle.trim()} ↗`
                : "Abrir ↗"
        );

        addLinkIcon(main);
        main.append(label);

        link.append(main, handle);

        if (configureLink(link, item.url)) {
            fragment.append(link);
        }
    });

    target.replaceChildren(fragment);
}

// ── Carga de archivos JSON ─────────────────────────

async function fetchJson(path) {
    const response = await fetch(path, {
        cache: "no-cache"
    });

    if (!response.ok) {
        throw new Error(`Error ${response.status}: ${path}`);
    }

    return response.json();
}

// ── Errores de carga ───────────────────────────────

function showLoadError(errors) {
    if (errors.length === 0) return;

    errors.forEach(error => console.warn(error));

    const hero = $(".hero");

    if (!hero || $(".load-error", hero)) return;

    const message = createElement(
        "p",
        "load-error",
        "No se pudo cargar parte del contenido. La información disponible sigue visible."
    );

    message.setAttribute("role", "status");
    hero.append(message);
}

// ── Inicialización ─────────────────────────────────

async function loadSite() {

    const sources = [
        {
            key: "profile",
            path: "./data/profile.json",
            validate: isObject
        },
        {
            key: "links",
            path: "./data/links.json",
            validate: Array.isArray
        }
    ];

    const results = await Promise.allSettled(
        sources.map(source => fetchJson(source.path))
    );

    const data = {};
    const errors = [];

    results.forEach((result, index) => {
        const source = sources[index];

        if (result.status === "rejected") {
            errors.push(result.reason);
            return;
        }

        if (!source.validate(result.value)) {
            errors.push(
                new TypeError(`Formato no válido: ${source.path}`)
            );
            return;
        }

        data[source.key] = result.value;
    });

    if (data.profile) {
        renderProfile(data.profile);
    }

    if (data.links) {
        renderFeaturedLinks(data.links);
        renderNetworkLinks(data.links);
    }

    showLoadError(errors);
}

// ── Inicio ─────────────────────────────────────────

loadSite().catch(error => {
    console.error("Error al iniciar el sitio:", error);
});
