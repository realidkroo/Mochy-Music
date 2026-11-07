// the little bits of feel the site borrows from the app: buttons that squish and spring back, phones that lean under a finger, the tour's bottom bar
(function () {
  var still = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  // pressBounce: down squishes, up springs back past and settles
  document.addEventListener("pointerdown", function (e) {
    var el = e.target.closest(".press");
    if (!el) return;
    el.classList.add("down");
    var up = function () {
      el.classList.remove("down");
      window.removeEventListener("pointerup", up);
      window.removeEventListener("pointercancel", up);
    };
    window.addEventListener("pointerup", up);
    window.addEventListener("pointercancel", up);
  });

  // press3D: a phone leans toward the pointer a few degrees, only while hovered, transform only
  if (!still && window.matchMedia("(hover: hover)").matches) {
    document.querySelectorAll(".tilt").forEach(function (el) {
      var frame = 0;
      el.addEventListener("pointermove", function (e) {
        if (frame) return;
        frame = requestAnimationFrame(function () {
          frame = 0;
          var r = el.getBoundingClientRect();
          var x = (e.clientX - r.left) / r.width - 0.5;
          var y = (e.clientY - r.top) / r.height - 0.5;
          el.style.transform = "perspective(900px) rotateY(" + (x * 10).toFixed(2) + "deg) rotateX(" + (-y * 10).toFixed(2) + "deg)";
        });
      });
      el.addEventListener("pointerleave", function () { el.style.transform = ""; });
    });
  }

  // the ring only turns while it's on screen
  var ring = document.querySelector(".ring");
  if (ring && "IntersectionObserver" in window) {
    new IntersectionObserver(function (entries) {
      ring.classList.toggle("paused", !entries[0].isIntersecting);
    }).observe(ring);
  }

  // the menu on a phone
  var nav = document.querySelector(".nav");
  var toggle = document.querySelector(".nav-toggle");
  if (nav && toggle) toggle.addEventListener("click", function () {
    toggle.setAttribute("aria-expanded", String(nav.classList.toggle("open")));
  });

  // the tour: tap a tab and the phone shows that page, the highlight sliding over the way the app's does
  document.querySelectorAll(".tour").forEach(function (tour) {
    var tabs = tour.querySelectorAll(".navbar button");
    var shots = tour.querySelectorAll(".phone img");
    var hl = tour.querySelector(".hl");
    var caption = tour.querySelector(".tour-caption");
    tabs.forEach(function (tab, i) {
      tab.addEventListener("click", function () {
        tabs.forEach(function (t, j) { t.classList.toggle("on", i === j); });
        shots.forEach(function (s, j) {
          if (j === i && s.dataset.src) { s.src = s.dataset.src; delete s.dataset.src; }
          s.classList.toggle("on", i === j);
        });
        hl.style.left = (6 + i * 64 + 4) + "px";
        if (caption) caption.textContent = tab.dataset.caption || "";
      });
    });
  });
})();
