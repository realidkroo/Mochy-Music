// the homepage's motion: each sheet's pose from the scroll, the strip that slides sideways, the top bar, reveals and leaning phones
(function () {
  var root = document.documentElement;
  var still = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  var wide = window.matchMedia("(min-width: 981px)");

  // the menu on a phone, closed again by picking a link
  var topnav = document.getElementById("topnav");
  var nav = topnav.querySelector(".nav");
  var toggle = topnav.querySelector(".nav-toggle");
  toggle.addEventListener("click", function () {
    toggle.setAttribute("aria-expanded", String(nav.classList.toggle("open")));
  });
  nav.querySelectorAll(".nav-links a").forEach(function (a) {
    a.addEventListener("click", function () { nav.classList.remove("open"); toggle.setAttribute("aria-expanded", "false"); });
  });

  var items = Array.prototype.slice.call(document.querySelectorAll(".stack > *"));
  var hero = document.querySelector(".hero");
  var rail = document.querySelector(".rail");
  var track = rail ? rail.querySelector(".rail-track") : null;
  var cards = track ? Array.prototype.slice.call(track.children) : [];
  var railIndex = items.indexOf(rail);
  var travel = 0;
  var railHeight = 0;
  var cardMid = [];

  function clamp(v) { return v < 0 ? 0 : v > 1 ? 1 : v; }
  function ease(t) { return 1 - Math.pow(1 - t, 3); }
  function n(v) { return Math.round(v * 100) / 100; }

  // a style only written when it changed, so a still page costs nothing and a moving one never restyles a whole sheet
  function put(el, prop, value) {
    if (!el) return;
    var seen = el._seen || (el._seen = {});
    if (seen[prop] === value) return;
    seen[prop] = value;
    el.style[prop] = value;
  }
  function px(el, name, fallback) {
    var v = parseFloat(getComputedStyle(el).getPropertyValue(name));
    return isNaN(v) ? fallback : v;
  }

  // each sheet's moving pieces, found once; the dark shade that falls over a covered sheet is its own layer
  var parts = items.map(function (el) {
    var shade = null;
    if (el.classList.contains("scene")) {
      shade = document.createElement("div");
      shade.className = "shade";
      el.appendChild(shade);
    }
    return {
      el: el,
      body: el.querySelector(".scene-body"),
      shade: shade,
      words: el.querySelector(".words, .rail-head"),
      flip: el.classList.contains("flip"),
      head: el === rail,
      slots: Array.prototype.map.call(el.querySelectorAll(".slot"), function (s) { return { el: s }; }),
      ghost: el.querySelector(".ghost"),
      ring: el.querySelector(".ring-wrap"),
      copy: el.querySelector(".hero-copy"),
      mascot: el.querySelector(".mascot-wrap"),
      cue: el.querySelector(".cue")
    };
  });

  // how far the strip travels sideways, how long it stays pinned, and each phone's own rise and lean
  function measure() {
    parts.forEach(function (p) {
      p.slots.forEach(function (s) {
        s.rise = px(s.el, "--rise", 240);
        s.lift = px(s.el, "--lift", 60);
        s.turn = px(s.el, "--turn", 6);
      });
    });
    if (!rail) return;
    if (!wide.matches || still) {
      rail.style.height = "";
      track.style.transform = "";
      cards.forEach(function (c) { put(c, "transform", ""); put(c.querySelector(".phone"), "transform", ""); });
      travel = 0;
      return;
    }
    travel = Math.max(0, track.scrollWidth - window.innerWidth);
    railHeight = Math.round(window.innerHeight * 1.35 + travel);
    rail.style.height = railHeight + "px";
    cardMid = cards.map(function (c) { return c.offsetLeft + c.offsetWidth / 2; });
  }

  // one sheet's pose for how far in (i) and how covered (c) it is
  function pose(p, i, c, stacked) {
    var out = 1 - i;
    put(p.body, "transform", c ? "scale(" + n(1 - c * 0.07) + ")" : "");
    if (p.shade) put(p.shade, "opacity", c ? String(n(c * 0.62)) : "0");
    if (p.words) {
      var move = p.head || !stacked ? "translate3d(0," + n(out * (p.head ? 80 : 50)) + "px,0)" : "translate3d(" + n(out * (p.flip ? 110 : -110)) + "px,0,0)";
      put(p.words, "transform", out ? move : "");
      put(p.words, "opacity", out ? String(n(i)) : "");
    }
    p.slots.forEach(function (s) {
      var y = out * s.rise - c * s.lift;
      put(s.el, "transform", y || out ? "translate3d(0," + n(y) + "px,0) rotate(" + n(out * s.turn) + "deg)" : "");
    });
    if (p.ghost) put(p.ghost, "transform", c ? "translate3d(" + n(c * -16) + "%,0,0)" : "");
    if (p.ring) put(p.ring, "transform", c ? "rotate(" + n(c * 120) + "deg)" : "");
    if (p.copy) {
      put(p.copy, "transform", c ? "translate3d(0," + n(c * -90) + "px,0)" : "");
      put(p.copy, "opacity", c ? String(n(clamp(1 - c * 1.3))) : "");
    }
    if (p.mascot) put(p.mascot, "transform", c ? "translate3d(0," + n(c * -70) + "px,0) scale(" + n(1 + c * 0.05) + ")" : "");
    if (p.cue) put(p.cue, "opacity", c ? String(n(clamp(1 - c * 4))) : "");
  }

  // every read first, then every write, once a frame at most
  var queued = false;
  function frame() {
    queued = false;
    var vh = window.innerHeight;
    var vw = window.innerWidth;
    var tops = items.map(function (el) { return el.getBoundingClientRect().top; });
    var stacked = wide.matches;
    parts.forEach(function (p, i) {
      var next = tops[i + 1];
      var covering = stacked && next !== undefined && p.el.classList.contains("scene");
      pose(p, ease(clamp((vh - tops[i]) / (vh * 0.6))), covering ? clamp(1 - next / vh) : 0, stacked);
    });
    // the hero's marquee, ring and cue rest once a sheet fully covers them
    if (hero) hero.classList.toggle("resting", stacked && tops[1] !== undefined && tops[1] <= 0);
    if (travel) {
      var x = clamp(-tops[railIndex] / (railHeight - vh)) * travel;
      track.style.transform = "translate3d(" + (-x).toFixed(1) + "px, 0, 0)";
      cards.forEach(function (c, i) {
        var d = n(Math.min(1, Math.abs(cardMid[i] - x - vw / 2) / vw * 1.6));
        put(c, "transform", "scale(" + n(1 - d * 0.06) + ")");
        put(c._phone || (c._phone = c.querySelector(".phone")), "transform", "translate(-50%, calc(36% + " + n(d * 50) + "px)) rotate(" + n(d * 4) + "deg)");
      });
    }
    topnav.classList.toggle("solid", window.scrollY > 24);
  }
  function queue() {
    if (queued) return;
    queued = true;
    requestAnimationFrame(frame);
  }

  if (still) {
    window.addEventListener("scroll", function () { topnav.classList.toggle("solid", window.scrollY > 24); }, { passive: true });
  } else {
    measure();
    frame();
    window.addEventListener("scroll", queue, { passive: true });
    window.addEventListener("resize", function () { measure(); queue(); });
    window.addEventListener("load", function () { measure(); queue(); });
    wide.addEventListener("change", function () { measure(); queue(); });
  }

  // what comes after the sheets rises in the first time it's seen
  var reveals = document.querySelectorAll(".reveal");
  if (still || !("IntersectionObserver" in window)) {
    reveals.forEach(function (el) { el.classList.add("in"); });
  } else {
    var seen = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) {
        if (!e.isIntersecting) return;
        e.target.classList.add("in");
        seen.unobserve(e.target);
      });
    }, { threshold: 0.18 });
    reveals.forEach(function (el) { seen.observe(el); });
  }

  // press3D: a phone leans toward the pointer a few degrees, only while hovered
  if (!still && window.matchMedia("(hover: hover)").matches) {
    document.querySelectorAll(".tilt").forEach(function (el) {
      var pending = 0;
      el.addEventListener("pointermove", function (e) {
        if (pending) return;
        pending = requestAnimationFrame(function () {
          pending = 0;
          var r = el.getBoundingClientRect();
          var x = (e.clientX - r.left) / r.width - 0.5;
          var y = (e.clientY - r.top) / r.height - 0.5;
          el.style.transform = "perspective(900px) rotateY(" + (x * 10).toFixed(2) + "deg) rotateX(" + (-y * 10).toFixed(2) + "deg)";
        });
      });
      el.addEventListener("pointerleave", function () { el.style.transform = ""; });
    });
  }
})();
