// the homepage's motion: each sheet's --in and --cover from the scroll, the strip that slides sideways, the top bar, reveals and leaning phones
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

  // a custom property only written when it changed, so a still page costs no style work
  function set(el, name, value) {
    var v = Math.round(value * 1000) / 1000;
    var seen = el._seen || (el._seen = {});
    if (seen[name] === v) return;
    seen[name] = v;
    el.style.setProperty(name, v);
  }

  // how far the strip travels sideways, and so how long it stays pinned
  function measure() {
    if (!rail) return;
    if (!wide.matches || still) {
      rail.style.height = "";
      track.style.transform = "";
      travel = 0;
      return;
    }
    travel = Math.max(0, track.scrollWidth - window.innerWidth);
    railHeight = Math.round(window.innerHeight * 1.35 + travel);
    rail.style.height = railHeight + "px";
    cardMid = cards.map(function (c) { return c.offsetLeft + c.offsetWidth / 2; });
  }

  // every read first, then every write, once a frame at most
  var queued = false;
  function frame() {
    queued = false;
    var vh = window.innerHeight;
    var vw = window.innerWidth;
    var tops = items.map(function (el) { return el.getBoundingClientRect().top; });
    var stacked = wide.matches;
    items.forEach(function (el, i) {
      set(el, "--in", ease(clamp((vh - tops[i]) / (vh * 0.6))));
      var next = tops[i + 1];
      var covering = stacked && next !== undefined && el.classList.contains("scene");
      set(el, "--cover", covering ? clamp(1 - next / vh) : 0);
    });
    // the hero's ring and cue rest once a sheet fully covers them
    if (hero) hero.classList.toggle("resting", stacked && tops[1] !== undefined && tops[1] <= 0);
    if (travel) {
      var p = clamp(-tops[railIndex] / (railHeight - vh));
      var x = p * travel;
      track.style.transform = "translate3d(" + (-x).toFixed(1) + "px, 0, 0)";
      cards.forEach(function (c, i) { set(c, "--d", Math.min(1, Math.abs(cardMid[i] - x - vw / 2) / vw * 1.6)); });
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
