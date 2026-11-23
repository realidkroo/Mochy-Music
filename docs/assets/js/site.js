// buttons that spring back and the tour bar
(function () {
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

  // tap a tab, show that page, slide the highlight
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
