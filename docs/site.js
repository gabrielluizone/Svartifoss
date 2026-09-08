(function () {
  'use strict';

  var motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
  var reduceMotion = motionQuery.matches;
  function t(key) {
    return window.SvartifossI18n ? window.SvartifossI18n.t(key) : key;
  }
  function scrollBehavior() { return reduceMotion ? 'instant' : 'smooth'; }
  function onMediaChange(query, callback) {
    if (query.addEventListener) query.addEventListener('change', callback);
    else query.addListener(callback);
  }

  // The menu stays visible without JavaScript; only a working toggle collapses it.
  var nav = document.querySelector('.nav');
  var navToggle = document.querySelector('.nav-toggle');
  var narrowNav = window.matchMedia('(max-width: 1100px)');
  function closeNav(restoreFocus) {
    if (!nav || !navToggle) return;
    nav.classList.remove('is-open');
    navToggle.setAttribute('aria-expanded', 'false');
    if (restoreFocus) navToggle.focus();
  }
  if (nav && navToggle) {
    nav.classList.add('nav-ready');
    navToggle.addEventListener('click', function () {
      var isOpen = nav.classList.toggle('is-open');
      navToggle.setAttribute('aria-expanded', String(isOpen));
    });
    nav.querySelectorAll('a[href^="#"]').forEach(function (link) {
      link.addEventListener('click', function () {
        var wasOpen = narrowNav.matches && nav.classList.contains('is-open');
        closeNav(false);
        // Continue keyboard navigation at the destination after its menu link disappears.
        var target = document.getElementById(link.getAttribute('href').slice(1));
        if (wasOpen && target) {
          if (!target.hasAttribute('tabindex')) target.setAttribute('tabindex', '-1');
          target.focus({ preventScroll: true });
        }
      });
    });
    document.addEventListener('click', function (event) {
      if (!nav.contains(event.target)) closeNav(false);
    });
    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape' && nav.classList.contains('is-open')) {
        event.preventDefault();
        closeNav(true);
      }
    });
    onMediaChange(narrowNav, function () { if (!narrowNav.matches) closeNav(false); });
  }

  // Identify the linked section currently being read without moving keyboard focus.
  var navSections = Array.prototype.map.call(document.querySelectorAll('.nav-menu a[href^="#"]'), function (link) {
    return { link: link, section: document.getElementById(link.getAttribute('href').slice(1)) };
  }).filter(function (item) { return item.section; });
  var navFramePending = false;
  function updateCurrentSection() {
    navFramePending = false;
    if (lightbox && lightbox.open) return;
    var marker = (nav ? nav.getBoundingClientRect().bottom : 0) + Math.min(window.innerHeight * 0.2, 160);
    navSections.forEach(function (item) {
      var bounds = item.section.getBoundingClientRect();
      if (bounds.top <= marker && bounds.bottom > marker) item.link.setAttribute('aria-current', 'location');
      else item.link.removeAttribute('aria-current');
    });
  }
  function scheduleCurrentSection() {
    if (navFramePending) return;
    navFramePending = true;
    window.requestAnimationFrame(updateCurrentSection);
  }
  window.addEventListener('scroll', scheduleCurrentSection, { passive: true });
  window.addEventListener('resize', scheduleCurrentSection);
  window.addEventListener('load', scheduleCurrentSection);
  scheduleCurrentSection();

  // Accessible screenshot preview. Source labels are kept separate from button labels.
  var lightbox = document.getElementById('image-lightbox');
  var lightboxImage = document.getElementById('lightbox-image');
  var lightboxCaption = document.getElementById('lightbox-caption');
  var lightboxClose = lightbox && lightbox.querySelector('.lightbox-close');
  var lightboxOpener = null;
  var bodyScrollState = null;
  var zoomSurfaces = [];

  function imageForSurface(surface) {
    return surface.querySelector('img.is-active') || surface.querySelector('img');
  }
  function labelForSurface(surface, image) {
    if (surface.hasAttribute('data-slideshow')) {
      var slides = Array.prototype.slice.call(surface.querySelectorAll('img'));
      return t(surface.getAttribute('data-preview-label') || 'Svartifoss preview') +
        ' · ' + (slides.indexOf(image) + 1) + '/' + slides.length;
    }
    return image.getAttribute('alt') || t(surface.getAttribute('data-preview-label') || 'Svartifoss preview');
  }
  function updateSurfaceLabel(surface) {
    var img = imageForSurface(surface);
    if (!img) return;
    surface.setAttribute('aria-label', t('Open enlarged image') + ': ' + labelForSurface(surface, img));
    surface.setAttribute('data-zoom-label', t('Expand'));
  }
  function lockBodyScroll() {
    if (bodyScrollState) return;
    var body = document.body;
    bodyScrollState = {
      x: window.scrollX, y: window.scrollY,
      position: body.style.position, top: body.style.top, left: body.style.left,
      width: body.style.width, overflow: body.style.overflow,
      paddingRight: body.style.paddingRight
    };
    var scrollbar = window.innerWidth - document.documentElement.clientWidth;
    var padding = parseFloat(window.getComputedStyle(body).paddingRight) || 0;
    body.style.position = 'fixed';
    body.style.top = -bodyScrollState.y + 'px';
    body.style.left = -bodyScrollState.x + 'px';
    body.style.width = '100%';
    body.style.overflow = 'hidden';
    if (scrollbar > 0) body.style.paddingRight = (padding + scrollbar) + 'px';
  }
  function restoreBodyScroll() {
    if (!bodyScrollState) return;
    var previous = bodyScrollState;
    bodyScrollState = null;
    ['position', 'top', 'left', 'width', 'overflow', 'paddingRight'].forEach(function (key) {
      document.body.style[key] = previous[key];
    });
    window.scrollTo({ left: previous.x, top: previous.y, behavior: 'instant' });
  }
  function openLightbox(surface) {
    if (!lightbox || !lightboxImage || lightbox.open) return;
    var img = imageForSurface(surface);
    if (!img) return;
    closeNav(false);
    lightboxOpener = surface;
    var label = labelForSurface(surface, img);
    lightboxImage.src = img.currentSrc || img.src;
    lightboxImage.alt = label;
    lightboxImage.setAttribute('data-i18n-dynamic', '');
    if (lightboxCaption) {
      lightboxCaption.setAttribute('data-i18n-dynamic', '');
      lightboxCaption.textContent = label;
    }
    lockBodyScroll();
    if (typeof lightbox.showModal === 'function') lightbox.showModal();
    else lightbox.setAttribute('open', '');
    if (lightboxClose) lightboxClose.focus({ preventScroll: true });
  }
  function finishLightboxClose() {
    restoreBodyScroll();
    if (lightboxImage) lightboxImage.removeAttribute('src');
    if (lightboxOpener && lightboxOpener.isConnected) lightboxOpener.focus({ preventScroll: true });
    lightboxOpener = null;
  }
  function closeLightbox() {
    if (!lightbox || !lightbox.open) return;
    if (typeof lightbox.close === 'function') lightbox.close();
    else {
      lightbox.removeAttribute('open');
      finishLightboxClose();
    }
  }
  document.querySelectorAll('.hero-art, .slideshow, .face-tile, .mural-cell, .spec-card, .film-card, .control-shot, .content-shot, .compare-track').forEach(function (surface) {
    if (surface.closest('[aria-hidden="true"]') || !imageForSurface(surface)) return;
    surface.setAttribute('data-preview-label', surface.getAttribute('data-i18n-aria-label-source') ||
      surface.getAttribute('aria-label') || 'Svartifoss preview');
    surface.classList.add('zoom-surface');
    surface.setAttribute('tabindex', '0');
    surface.setAttribute('role', 'button');
    updateSurfaceLabel(surface);
    zoomSurfaces.push(surface);
    surface.addEventListener('click', function () { openLightbox(surface); });
    surface.addEventListener('keydown', function (event) {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        openLightbox(surface);
      }
    });
  });
  if (lightbox) {
    lightbox.addEventListener('click', function (event) {
      if (event.target === lightbox) closeLightbox();
    });
    lightbox.addEventListener('close', finishLightboxClose);
    lightbox.addEventListener('keydown', function (event) {
      if (event.key === 'Escape' && typeof lightbox.close !== 'function') closeLightbox();
    });
  }
  if (lightboxClose) lightboxClose.addEventListener('click', closeLightbox);

  // Slides pause while being inspected, in background tabs, and for reduced motion.
  var slideshows = [];
  function initSlideshow(el, intervalMs) {
    if (!el) return;
    var slides = el.querySelectorAll('img');
    var frame = el.closest('.sync-frame');
    if (slides.length < 2 || !frame) return;
    var index = Math.max(0, Array.prototype.findIndex.call(slides, function (img) {
      return img.classList.contains('is-active');
    }));
    var timer = null;
    var userPaused = false;
    var motionOverride = false;
    var hovered = false;
    var focused = el.contains(document.activeElement);
    var toggle = document.createElement('button');
    toggle.type = 'button';
    toggle.className = 'slideshow-toggle';
    toggle.setAttribute('data-i18n-dynamic', '');
    frame.appendChild(toggle);

    function isPaused() { return userPaused || (reduceMotion && !motionOverride); }
    function updateToggle() {
      toggle.textContent = t(isPaused() ? 'Play slideshow' : 'Pause slideshow');
      toggle.setAttribute('aria-pressed', String(isPaused()));
    }
    function schedule() {
      window.clearTimeout(timer);
      timer = null;
      if (isPaused() || hovered || focused || document.hidden || (lightbox && lightbox.open)) return;
      timer = window.setTimeout(function () {
        var next = (index + 1) % slides.length;
        // Keep the current frame until the next image is ready.
        if (slides[next].complete && slides[next].naturalWidth > 0) {
          slides[index].classList.remove('is-active');
          index = next;
          slides[index].classList.add('is-active');
          updateSurfaceLabel(el);
        }
        schedule();
      }, intervalMs);
    }
    toggle.addEventListener('click', function () {
      var playing = isPaused();
      userPaused = !playing;
      motionOverride = playing;
      updateToggle();
      schedule();
    });
    el.addEventListener('pointerenter', function (event) {
      if (event.pointerType === 'touch') return;
      hovered = true;
      schedule();
    });
    el.addEventListener('pointerleave', function () { hovered = false; schedule(); });
    el.addEventListener('focusin', function () { focused = true; schedule(); });
    el.addEventListener('focusout', function (event) {
      focused = el.contains(event.relatedTarget);
      schedule();
    });
    document.addEventListener('visibilitychange', schedule);
    if (lightbox) lightbox.addEventListener('close', schedule);
    slideshows.push({
      updateLabel: updateToggle,
      motionChanged: function () { motionOverride = false; updateToggle(); schedule(); }
    });
    updateToggle();
    schedule();
  }
  initSlideshow(document.querySelector('[data-slideshow="watch"]'), 4000);
  initSlideshow(document.querySelector('[data-slideshow="phone"]'), 4800);
  onMediaChange(motionQuery, function () {
    reduceMotion = motionQuery.matches;
    slideshows.forEach(function (slideshow) { slideshow.motionChanged(); });
  });

  // Commit the comparison only after all four images load; stale requests never win.
  var FACE_DISPLAY_NAMES = {
    classic: 'Classic', expressive: 'Expressive', poster: 'Poster', studio: 'Studio',
    material: 'Material', immersive: 'Immersive', carousel: 'Carousel', chat: 'Chat',
    split: 'Split', note: 'Note', verse: 'Verse', metadata: 'Metadata', artist: 'Artist',
    ribbon: 'Ribbon', frame: 'Frame'
  };
  var compareButtons = document.querySelectorAll('.compare-face-swatch');
  var compareTracks = document.querySelectorAll('.compare-track');
  var compareContainer = document.querySelector('.compare-tracks');
  var compareStatus = document.getElementById('compare-status');
  var compareRequest = 0;
  var compareFace = 'classic';
  var compareMessage = '';
  function updateCompareStatus(message) {
    compareMessage = message;
    if (!compareStatus) return;
    compareStatus.setAttribute('data-i18n-dynamic', '');
    compareStatus.textContent = message ? t(message) + (message === 'Preview updated' ? ': ' + FACE_DISPLAY_NAMES[compareFace] : '') : '';
  }
  function updateCompareLabels() {
    compareTracks.forEach(function (track) {
      var img = track.querySelector('img');
      var caption = track.querySelector('figcaption');
      var title = caption && caption.querySelector('strong');
      var artist = caption && caption.querySelector('span:not(.dot)');
      if (!img) return;
      img.setAttribute('data-i18n-dynamic', '');
      img.alt = FACE_DISPLAY_NAMES[compareFace] + ' · ' +
        (title ? title.textContent : '') + (artist ? ' · ' + artist.textContent.trim() : '');
      updateSurfaceLabel(track);
    });
  }
  function preloadImage(src) {
    return new Promise(function (resolve, reject) {
      var img = new Image();
      img.onload = function () {
        if (typeof img.decode === 'function') img.decode().then(function () { resolve(img); }, reject);
        else resolve(img);
      };
      img.onerror = reject;
      img.src = src;
    });
  }
  if (compareButtons.length && compareTracks.length) {
    var initialFace = document.querySelector('.compare-face-swatch.is-active');
    if (initialFace) compareFace = initialFace.getAttribute('data-face');
    updateCompareLabels();
    compareButtons.forEach(function (button) {
      button.addEventListener('click', function () {
        var face = button.getAttribute('data-face');
        if (!Object.prototype.hasOwnProperty.call(FACE_DISPLAY_NAMES, face)) return;
        var request = ++compareRequest;
        compareButtons.forEach(function (item) { item.classList.remove('is-loading'); });
        if (face === compareFace) {
          if (compareContainer) compareContainer.removeAttribute('aria-busy');
          updateCompareStatus('');
          return;
        }
        button.classList.add('is-loading');
        if (compareContainer) compareContainer.setAttribute('aria-busy', 'true');
        updateCompareStatus('Loading preview');
        var images = Array.prototype.map.call(compareTracks, function (track) {
          return preloadImage('images/compare-' + face + '-' + track.getAttribute('data-track') + '.png');
        });
        Promise.all(images).then(function (loaded) {
          if (request !== compareRequest) return;
          compareFace = face;
          compareTracks.forEach(function (track, index) { track.querySelector('img').src = loaded[index].src; });
          compareButtons.forEach(function (item) {
            var active = item === button;
            item.classList.toggle('is-active', active);
            item.classList.remove('is-loading');
            item.setAttribute('aria-pressed', String(active));
          });
          if (compareContainer) compareContainer.removeAttribute('aria-busy');
          updateCompareLabels();
          updateCompareStatus('Preview updated');
        }).catch(function () {
          if (request !== compareRequest) return;
          button.classList.remove('is-loading');
          if (compareContainer) compareContainer.removeAttribute('aria-busy');
          updateCompareStatus('Preview unavailable');
        });
      });
    });
  }

  // Horizontal strips keep native wheel/touch scrolling and support deliberate mouse drags.
  function enableMouseDrag(strip) {
    var pointer = null;
    var startX = 0;
    var startScroll = 0;
    var dragging = false;
    var suppressClick = false;
    var resetClickTimer = null;
    strip.addEventListener('pointerdown', function (event) {
      if (event.pointerType !== 'mouse' || event.button !== 0) return;
      window.clearTimeout(resetClickTimer);
      pointer = event.pointerId;
      startX = event.clientX;
      startScroll = strip.scrollLeft;
      dragging = false;
      suppressClick = false;
    });
    strip.addEventListener('pointermove', function (event) {
      if (event.pointerId !== pointer) return;
      if (!event.buttons) { endDrag(); return; }
      var delta = event.clientX - startX;
      if (!dragging && Math.abs(delta) < 7) return;
      if (!dragging) {
        dragging = true;
        strip.classList.add('dragging');
        strip.setPointerCapture(pointer);
      }
      event.preventDefault();
      strip.scrollLeft = startScroll - delta;
    });
    function endDrag() {
      if (pointer === null) return;
      suppressClick = dragging;
      var endedPointer = pointer;
      pointer = null;
      dragging = false;
      strip.classList.remove('dragging');
      if (strip.hasPointerCapture(endedPointer)) strip.releasePointerCapture(endedPointer);
      resetClickTimer = window.setTimeout(function () { suppressClick = false; }, 0);
    }
    strip.addEventListener('pointerup', endDrag);
    strip.addEventListener('pointercancel', endDrag);
    strip.addEventListener('lostpointercapture', endDrag);
    strip.addEventListener('pointerleave', function () { if (!dragging) endDrag(); });
    strip.addEventListener('dragstart', function (event) { event.preventDefault(); });
    strip.addEventListener('click', function (event) {
      if (!suppressClick) return;
      event.preventDefault();
      event.stopImmediatePropagation();
      suppressClick = false;
    }, true);
  }
  document.querySelectorAll('.filmstrip-wrap').forEach(function (wrap) {
    var strip = wrap.querySelector('.filmstrip');
    var previous = wrap.querySelector('.film-nav.prev');
    var next = wrap.querySelector('.film-nav.next');
    if (!strip) return;
    enableMouseDrag(strip);
    if (!previous || !next) return;
    function step() {
      var card = strip.querySelector('.film-card, .face-tile');
      var gap = parseFloat(window.getComputedStyle(strip).gap) || 18;
      return card ? (card.offsetWidth + gap) * 2 : 300;
    }
    function updateNavState() {
      previous.disabled = strip.scrollLeft <= 1;
      next.disabled = strip.scrollLeft >= strip.scrollWidth - strip.clientWidth - 1;
    }
    previous.addEventListener('click', function () { strip.scrollBy({ left: -step(), behavior: scrollBehavior() }); });
    next.addEventListener('click', function () { strip.scrollBy({ left: step(), behavior: scrollBehavior() }); });
    strip.addEventListener('scroll', updateNavState, { passive: true });
    window.addEventListener('resize', updateNavState);
    window.addEventListener('load', updateNavState);
    if (document.fonts && document.fonts.ready) document.fonts.ready.then(updateNavState);
    updateNavState();
  });
  function setText(key, value) {
    document.querySelectorAll('[data-stat="' + key + '"]').forEach(function (el) {
      el.setAttribute('data-i18n-dynamic', '');
      el.textContent = value;
    });
  }
  var releaseVersion = '';
  var latestReleaseUrl = 'https://github.com/gabrielluizone/Svartifoss/releases/latest';
  document.querySelectorAll('[data-dl]').forEach(function (link) { link.href = latestReleaseUrl; });
  setText('version', t('See releases'));

  // GitHub's latest endpoint excludes prereleases; exact asset names match the app updater.
  fetch('https://api.github.com/repos/gabrielluizone/Svartifoss/releases/latest')
    .then(function (response) {
      if (!response.ok) throw new Error('Release unavailable');
      return response.json();
    })
    .then(function (release) {
      if (!release || release.draft || release.prerelease || !release.tag_name) return;
      releaseVersion = release.tag_name;
      setText('version', releaseVersion);
      ['mobile', 'wear'].forEach(function (module) {
        var asset = (release.assets || []).find(function (item) {
          return item.name === module + '-release.apk' &&
            typeof item.browser_download_url === 'string' &&
            item.browser_download_url.indexOf('https://github.com/gabrielluizone/Svartifoss/releases/download/') === 0;
        });
        if (asset) document.querySelectorAll('[data-dl="' + module + '"]').forEach(function (link) {
          link.href = asset.browser_download_url;
        });
      });
    })
    .catch(function () { setText('version', t('See releases')); });

  document.addEventListener('svartifoss:languagechange', function () {
    updateCompareLabels();
    updateCompareStatus(compareMessage);
    zoomSurfaces.forEach(updateSurfaceLabel);
    slideshows.forEach(function (slideshow) { slideshow.updateLabel(); });
    setText('version', releaseVersion || t('See releases'));
  });
})();
