/* ==========================================================================
   CODEX MUSIC - MINIMAL & PERFECTLY RESPONSIVE CONTROLLER
   ========================================================================== */

document.addEventListener('DOMContentLoaded', () => {
  initMobileNavToggle();
  initSPATabs();
  initModGuiReplica();
  initSpectrumVisualizer();
  initVersionsFilter();
  initDocsSearch();
  initCodeCopyButtons();
});

/* ── 1. Mobile Navigation Hamburger Menu ──────────────────────────────── */
function initMobileNavToggle() {
  const toggleBtn = document.getElementById('navToggleBtn');
  const navMenu = document.getElementById('navMenu');

  if (!toggleBtn || !navMenu) return;

  toggleBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    navMenu.classList.toggle('mobile-open');
    const icon = toggleBtn.querySelector('i');
    if (icon) {
      icon.className = navMenu.classList.contains('mobile-open') ? 'ri-close-line' : 'ri-menu-3-line';
    }
  });

  // Close menu when clicking outside or selecting a tab link
  document.addEventListener('click', (e) => {
    if (!navMenu.contains(e.target) && !toggleBtn.contains(e.target)) {
      navMenu.classList.remove('mobile-open');
      const icon = toggleBtn.querySelector('i');
      if (icon) icon.className = 'ri-menu-3-line';
    }
  });

  const links = navMenu.querySelectorAll('a');
  links.forEach(l => {
    l.addEventListener('click', () => {
      navMenu.classList.remove('mobile-open');
      const icon = toggleBtn.querySelector('i');
      if (icon) icon.className = 'ri-menu-3-line';
    });
  });
}

/* ── 2. SPA Tab Navigation ────────────────────────────────────────────── */
function initSPATabs() {
  const navLinks = document.querySelectorAll('[data-tab-target]');
  const tabPages = document.querySelectorAll('.tab-page');

  navLinks.forEach(link => {
    link.addEventListener('click', (e) => {
      e.preventDefault();
      const targetId = link.getAttribute('data-tab-target');

      document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
      document.querySelectorAll(`[data-tab-target="${targetId}"]`).forEach(l => l.classList.add('active'));

      tabPages.forEach(page => {
        if (page.id === targetId) {
          page.classList.add('active');
        } else {
          page.classList.remove('active');
        }
      });

      window.scrollTo({ top: 0, behavior: 'smooth' });
    });
  });
}

/* ── 3. Interactive Mod GUI Replica Controller ────────────────────────── */
function initModGuiReplica() {
  const sidebarBtns = document.querySelectorAll('.mod-sidebar-btn');
  const viewPanels = document.querySelectorAll('.mod-view-panel');

  sidebarBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      const targetView = btn.getAttribute('data-gui-view');

      sidebarBtns.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');

      viewPanels.forEach(panel => {
        if (panel.id === targetView) {
          panel.classList.add('active');
        } else {
          panel.classList.remove('active');
        }
      });
    });
  });

  // Track Selector inside Mod GUI
  const cardItems = document.querySelectorAll('.mod-card-item');
  const titleDisplay = document.getElementById('guiPlayingTitle');
  const artistDisplay = document.getElementById('guiPlayingArtist');

  cardItems.forEach(card => {
    card.addEventListener('click', () => {
      cardItems.forEach(c => c.classList.remove('active'));
      card.classList.add('active');

      const title = card.getAttribute('data-track-title');
      const artist = card.getAttribute('data-track-artist');

      if (titleDisplay && title) titleDisplay.textContent = title;
      if (artistDisplay && artist) artistDisplay.textContent = artist;
    });
  });
}

/* ── 4. Calming Audio Spectrum Visualizer Animation ──────────────────── */
let isAudioPlaying = true;

function initSpectrumVisualizer() {
  const container = document.getElementById('modSpectrumContainer');
  const playBtn = document.getElementById('guiPlayBtn');

  if (!container) return;

  container.innerHTML = '';
  const barCount = 36;
  const bars = [];

  for (let i = 0; i < barCount; i++) {
    const bar = document.createElement('div');
    bar.className = 'mod-spectrum-bar';
    container.appendChild(bar);
    bars.push(bar);
  }

  function renderSpectrum() {
    if (isAudioPlaying) {
      const time = Date.now() * 0.005;
      bars.forEach((bar, idx) => {
        const wave = Math.sin(time + idx * 0.3) * 0.4 + Math.cos(time * 0.7 - idx * 0.2) * 0.4 + 0.5;
        const heightPct = Math.max(10, Math.min(100, wave * 85 + Math.random() * 8));
        bar.style.height = `${heightPct}%`;
      });
    } else {
      bars.forEach(bar => {
        bar.style.height = '10%';
      });
    }
    requestAnimationFrame(renderSpectrum);
  }

  renderSpectrum();

  if (playBtn) {
    playBtn.addEventListener('click', () => {
      isAudioPlaying = !isAudioPlaying;
      const icon = playBtn.querySelector('i');
      if (icon) {
        icon.className = isAudioPlaying ? 'ri-pause-fill' : 'ri-play-fill';
      }
    });
  }
}

/* ── 5. Download Version Category Filter ──────────────────────────────── */
function initVersionsFilter() {
  const btns = document.querySelectorAll('.filter-btn');
  const cards = document.querySelectorAll('.version-card');

  btns.forEach(btn => {
    btn.addEventListener('click', () => {
      btns.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');

      const cat = btn.getAttribute('data-cat');

      cards.forEach(card => {
        const cardCat = card.getAttribute('data-category');
        if (cat === 'all' || cardCat === cat) {
          card.style.display = 'flex';
        } else {
          card.style.display = 'none';
        }
      });
    });
  });
}

/* ── 6. Documentation Live Search Filter ──────────────────────────────── */
function initDocsSearch() {
  const searchInput = document.getElementById('docsSearchInput');
  const links = document.querySelectorAll('.docs-link-list li');
  const sections = document.querySelectorAll('.docs-section');

  if (!searchInput) return;

  searchInput.addEventListener('input', (e) => {
    const q = e.target.value.toLowerCase().trim();

    links.forEach(item => {
      const txt = item.textContent.toLowerCase();
      item.style.display = txt.includes(q) ? 'block' : 'none';
    });

    if (q.length > 0) {
      sections.forEach(section => {
        const text = section.textContent.toLowerCase();
        section.style.display = text.includes(q) ? 'block' : 'none';
      });
    } else {
      sections.forEach(section => section.style.display = 'block');
    }
  });
}

/* ── 7. One-Click Code Snippet Copy Controller ────────────────────────── */
function initCodeCopyButtons() {
  const copyBtns = document.querySelectorAll('.copy-btn');

  copyBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      const targetId = btn.getAttribute('data-copy-target');
      const codeEl = document.getElementById(targetId);

      if (codeEl) {
        navigator.clipboard.writeText(codeEl.textContent).then(() => {
          const orig = btn.innerHTML;
          btn.innerHTML = `<i class="ri-check-line"></i> Copied!`;
          btn.style.color = '#10B981';
          setTimeout(() => {
            btn.innerHTML = orig;
            btn.style.color = '';
          }, 2000);
        });
      }
    });
  });
}
