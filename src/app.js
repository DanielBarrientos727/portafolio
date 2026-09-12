const $ = (selector) => document.querySelector(selector);
const icon = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6"/></svg>';

const menuButton = $('.menu-button');
const menu = $('#nav-links');

menuButton.addEventListener('click', () => {
  const open = menu.classList.toggle('open');
  menuButton.setAttribute('aria-expanded', String(open));
  menuButton.textContent = open ? 'Cerrar' : 'Menú';
});

menu.querySelectorAll('a').forEach((link) => link.addEventListener('click', () => {
  menu.classList.remove('open');
  menuButton.setAttribute('aria-expanded', 'false');
  menuButton.textContent = 'Menú';
}));

function addLinks(target, links, compact = false) {
  $(target).innerHTML = links.map((link) => compact
    ? `<a class="text-link" href="${link.url}" target="_blank" rel="noopener noreferrer">${link.label} ↗</a>`
    : `<a class="network" href="${link.url}" target="_blank" rel="noopener noreferrer"><span>${icon}<strong>${link.label}</strong></span><span class="handle">${link.handle} ↗</span></a>`
  ).join('');
}

function renderTechGroup(target, items) {
  $(target).innerHTML = items.map((item) => `<span class="tech-item">${item}</span>`).join('');
}

async function loadSite() {
  try {
    const [profile, projects, links, documents] = await Promise.all([
      fetch('/data/profile.json').then((r) => r.json()),
      fetch('/data/projects.json').then((r) => r.json()),
      fetch('/data/links.json').then((r) => r.json()),
      fetch('/data/documents.json').then((r) => r.json())
    ]);

    document.title = profile.name;
    $('#name').textContent = profile.name;
    $('#role').textContent = profile.role;
    $('#intro').textContent = profile.intro;
    $('#about').textContent = profile.about;

    addLinks('#hero-links', links.filter((link) => link.featured), true);

    $('#now-list').innerHTML = profile.now.map((item) => `<li>${item}</li>`).join('');

    renderTechGroup('#tech-actual', profile.tech.actual);
    renderTechGroup('#tech-aprendiendo', profile.tech.aprendiendo);
    renderTechGroup('#tech-interes', profile.tech.interes);

    $('#education-list').innerHTML = profile.education.map((item) => `
      <li>
        <h3>${item.program}</h3>
        <span class="institution">${item.institution}</span>
        <span class="meta">${item.period} · ${item.status}</span>
      </li>
    `).join('');

    $('#projects-list').innerHTML = projects.map((project) => `<article class="project"><h3>${project.name}</h3><span class="project-meta">${project.tech.join(' · ')}</span><p>${project.description}</p><div class="project-footer"><span class="status">${project.status}</span><a href="${project.url}" target="_blank" rel="noopener noreferrer">GitHub ↗</a></div></article>`).join('') || '<p class="empty">No hay proyectos públicos configurados todavía.</p>';

    const docs = documents.filter((doc) => doc.file);
    $('#documents-list').innerHTML = docs.length
      ? docs.map((doc) => `<div class="document"><span><strong>${doc.name}</strong><small>${doc.type}</small></span><a href="${doc.file}" target="_blank" rel="noopener noreferrer">Ver / descargar ↗</a></div>`).join('')
      : '<p class="empty">Aún no hay documentos públicos configurados.</p>';

    addLinks('#links-list', links);
  } catch (error) {
    console.error('No se pudieron cargar los datos del sitio.', error);
  }
}

loadSite();
