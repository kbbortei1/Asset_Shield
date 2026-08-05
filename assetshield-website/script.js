/* AssetShield marketing site: light interactions only. */
(function () {
  'use strict';

  // Year in footer
  var y = document.getElementById('year');
  if (y) y.textContent = new Date().getFullYear();

  // Sticky-nav shadow once scrolled
  var nav = document.getElementById('nav');
  var onScroll = function () {
    if (nav) nav.classList.toggle('is-stuck', window.scrollY > 8);
  };
  onScroll();
  window.addEventListener('scroll', onScroll, { passive: true });

  // Mobile nav toggle
  var toggle = document.getElementById('navToggle');
  var links = document.getElementById('navLinks');
  if (toggle && links) {
    toggle.addEventListener('click', function () {
      var open = links.classList.toggle('is-open');
      toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
    });
    // Close after tapping a link
    links.addEventListener('click', function (e) {
      if (e.target.closest('a')) {
        links.classList.remove('is-open');
        toggle.setAttribute('aria-expanded', 'false');
      }
    });
  }

  // Reveal-on-scroll
  var items = document.querySelectorAll('.reveal');
  if ('IntersectionObserver' in window && items.length) {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (en) {
        if (en.isIntersecting) { en.target.classList.add('is-in'); io.unobserve(en.target); }
      });
    }, { threshold: 0.12, rootMargin: '0px 0px -40px 0px' });
    items.forEach(function (el) { io.observe(el); });
  } else {
    items.forEach(function (el) { el.classList.add('is-in'); });
  }

  // Screenshot carousel: arrow controls + mouse drag-to-scroll (touch swipes natively)
  var track = document.getElementById('showTrack');
  if (track) {
    var step = function () {
      var card = track.querySelector('.shotcard');
      var gap = parseInt(getComputedStyle(track).columnGap || '30', 10) || 30;
      return card ? card.getBoundingClientRect().width + gap : 250;
    };
    var prev = document.getElementById('showPrev');
    var next = document.getElementById('showNext');
    if (prev) prev.addEventListener('click', function () { track.scrollBy({ left: -step(), behavior: 'smooth' }); });
    if (next) next.addEventListener('click', function () { track.scrollBy({ left: step(), behavior: 'smooth' }); });

    // Drag with a mouse (pointer) on desktop; leave touch to the browser.
    var down = false, startX = 0, startLeft = 0;
    track.addEventListener('pointerdown', function (e) {
      if (e.pointerType !== 'mouse') return;
      down = true; startX = e.clientX; startLeft = track.scrollLeft;
      track.classList.add('is-grabbing');
    });
    window.addEventListener('pointermove', function (e) {
      if (!down) return;
      track.scrollLeft = startLeft - (e.clientX - startX);
    });
    window.addEventListener('pointerup', function () {
      if (!down) return;
      down = false; track.classList.remove('is-grabbing');
    });
  }

  // Download button: no live link yet. Set window.ASSETSHIELD_APK_URL (or edit
  // downloadBtn's href in index.html) once the APK/EAS link is ready.
  var dl = document.getElementById('downloadBtn');
  if (dl) {
    var url = window.ASSETSHIELD_APK_URL || dl.getAttribute('href');
    if (!url || url === '#') {
      dl.addEventListener('click', function (e) {
        e.preventDefault();
        alert(dl.getAttribute('data-fallback') || 'Download coming soon.');
      });
    }
  }
})();
