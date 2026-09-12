# Portafolio personal — Daniel Alejandro Barrientos Soto

Portafolio personal de Daniel Alejandro Barrientos Soto, estudiante de
Ingeniería de Sistemas y Computación y Tecnólogo en Análisis y Desarrollo
de Software.

El sitio está construido desde cero con tecnologías web fundamentales:

- HTML
- CSS
- JavaScript
- JSON

No utiliza frameworks ni requiere un proceso de compilación.

La información del sitio está separada de la estructura y del diseño mediante
archivos JSON ubicados en `data/`.

---

## Filosofía

El proyecto sigue tres principios:

**KISS** — mantener el sistema simple y evitar complejidad innecesaria.

**FOSS** — priorizar herramientas, formatos y tecnologías abiertas.

**Honestidad** — mostrar únicamente formación, conocimientos, proyectos y
enlaces que representen realmente mi situación actual.

Este sitio es también un registro de aprendizaje y evolución profesional.

---

## Estructura del proyecto

```text
.
├── index.html
├── package.json
├── vercel.json
│
├── data/
│   ├── profile.json
│   ├── projects.json
│   ├── links.json
│   └── documents.json
│
├── src/
│   ├── app.js
│   └── styles.css
│
└── public/
    ├── cv/
    ├── certifications/
    └── favicon.svg