# Sitio personal — Daniel Alejandro Barrientos Soto

Sitio estático de una sola página, hecho con HTML, CSS y JavaScript (sin frameworks, sin build step). El contenido vive en `data/` como JSON, separado del diseño en `index.html` / `src/`.

Principios: **KISS** (sin nada que no aporte), **FOSS** (sin dependencias ni servicios propietarios), y honestidad — el sitio solo muestra formación, proyectos y enlaces reales.

## Ejecutar localmente

Con Node.js instalado:

```bash
npm run dev
```

O cualquier servidor estático desde la raíz del proyecto (hace falta un servidor, no `file://`, para que el navegador cargue los JSON de `data/`).

## Desplegar

**Vercel:** sube el repositorio, importa el proyecto, tipo de framework "Other", sin build command, y despliega.

**GitHub Pages:** activa Pages apuntando a la rama principal, carpeta raíz.

No hay base de datos ni backend — es puramente estático.

## Actualizar contenido

Todo el contenido editable vive en `data/`:

- **`profile.json`** — nombre, biografía, "ahora", formación (`education`) y tecnologías por categoría (`tech.actual` / `tech.aprendiendo` / `tech.interes`).
- **`projects.json`** — proyectos públicos reales, con su estado (`Activo`, `En desarrollo`, `Experimental`, `Aprendizaje`, `Archivado`).
- **`links.json`** — redes y contacto. `featured: true` hace que el enlace aparezca también en el hero.
- **`documents.json`** — CV y certificaciones descargables.

Para añadir una certificación, coloca el PDF en `public/certifications/` y agrega una entrada:

```json
{
  "name": "Nombre de la certificación",
  "type": "PDF",
  "file": "/public/certifications/archivo.pdf"
}
```

Un documento sin campo `file` simplemente no se muestra — así nunca hay enlaces rotos ni promesas vacías.

## Estructura

```text
.
├── index.html
├── src/
│   ├── styles.css
│   └── app.js
├── data/
│   ├── profile.json
│   ├── projects.json
│   ├── links.json
│   └── documents.json
├── public/
│   ├── cv/
│   ├── certifications/
│   └── favicon.svg
├── package.json
└── vercel.json
```
