// the wiki: one page per #name, the sidebar and search built from the articles, copy buttons, colours for json, and the little demos
(function () {
  var pages = Array.prototype.slice.call(document.querySelectorAll(".page"));
  var toc = document.getElementById("toc");
  var next = document.getElementById("next");
  var links = {};

  // the sidebar, grouped the way the articles say
  var group = null;
  pages.forEach(function (page) {
    if (page.dataset.group !== group) {
      group = page.dataset.group;
      var head = document.createElement("li");
      head.className = "group";
      head.textContent = group;
      toc.appendChild(head);
    }
    var li = document.createElement("li");
    var a = document.createElement("a");
    a.href = "#" + page.id;
    a.innerHTML = page.dataset.title;
    li.appendChild(a);
    toc.appendChild(li);
    links[page.id] = a;
  });

  function show(id) {
    var page = document.getElementById(id) || pages[0];
    pages.forEach(function (p) { p.classList.toggle("on", p === page); });
    Object.keys(links).forEach(function (k) { links[k].classList.toggle("on", k === page.id); });
    document.title = page.dataset.title.replace(/&amp;/g, "&").replace(/^\d+\. /, "") + " · Mochy Wiki";
    var i = pages.indexOf(page);
    next.innerHTML = "";
    if (i > 0) next.appendChild(step(pages[i - 1], "previous"));
    else next.appendChild(document.createElement("span"));
    if (i < pages.length - 1) next.appendChild(step(pages[i + 1], "next"));
    page.querySelectorAll("img[loading]").forEach(function (img) { img.loading = "eager"; });
    // after the browser's own jump to the #name, so the page opens at its title
    requestAnimationFrame(function () { window.scrollTo(0, 0); });
    document.getElementById("side").classList.remove("open");
    document.getElementById("tocToggle").setAttribute("aria-expanded", "false");
  }

  function step(page, word) {
    var a = document.createElement("a");
    a.href = "#" + page.id;
    a.className = "press";
    a.innerHTML = "<small>" + word + "</small><b>" + page.dataset.title + "</b>";
    return a;
  }

  window.addEventListener("hashchange", function () { show(location.hash.slice(1)); });
  show(location.hash.slice(1));

  // the hamburger on a phone opens the pages under the brand
  var burger = document.getElementById("tocToggle");
  burger.addEventListener("click", function () {
    burger.setAttribute("aria-expanded", String(document.getElementById("side").classList.toggle("open")));
  });

  // the fold button tucks the sidebar away to just the brand, and it stays that way next visit
  var wiki = document.querySelector(".wiki");
  var fold = document.getElementById("sideFold");
  function setFolded(folded) {
    wiki.classList.toggle("folded", folded);
    fold.setAttribute("aria-expanded", String(!folded));
    fold.setAttribute("aria-label", folded ? "Show the sidebar" : "Hide the sidebar");
  }
  try { setFolded(localStorage.getItem("wikiSideFolded") === "1"); } catch (e) {}
  fold.addEventListener("click", function () {
    var folded = !wiki.classList.contains("folded");
    setFolded(folded);
    try { localStorage.setItem("wikiSideFolded", folded ? "1" : "0"); } catch (e) {}
  });

  // json and code get a little colour, done once, no library
  document.querySelectorAll("pre code").forEach(function (code) {
    var text = code.textContent;
    var html = text.replace(/&/g, "&amp;").replace(/</g, "&lt;");
    if (code.classList.contains("json")) {
      html = html
        .replace(/("(?:[^"\\]|\\.)*")(\s*:)/g, '<span class="k">$1</span>$2')
        .replace(/:\s*("(?:[^"\\]|\\.)*")/g, function (m, s) { return m.replace(s, '<span class="s">' + s + "</span>"); })
        .replace(/([\[,]\s*)("(?:[^"\\]|\\.)*")/g, '$1<span class="s">$2</span>')
        .replace(/\b(-?\d+(?:\.\d+)?)\b(?![^<]*<\/span>)/g, '<span class="n">$1</span>');
    } else {
      html = html.replace(/(\/\/.*|\/\*.*?\*\/)/g, '<span class="c">$1</span>');
    }
    code.innerHTML = html;
    var button = document.createElement("button");
    button.type = "button";
    button.className = "copy press";
    button.textContent = "Copy";
    button.addEventListener("click", function () {
      var done = function () { button.textContent = "Copied!"; setTimeout(function () { button.textContent = "Copy"; }, 1300); };
      if (navigator.clipboard) navigator.clipboard.writeText(text).then(done, function () {});
    });
    code.parentNode.appendChild(button);
  });

  // search: titles first, then the words inside; / jumps to it, arrows and enter pick
  var q = document.getElementById("q");
  var results = document.getElementById("results");
  var index = pages.map(function (p) { return { id: p.id, title: p.dataset.title.replace(/&amp;/g, "&"), text: p.textContent.replace(/\s+/g, " ").toLowerCase() }; });
  var picked = 0;
  function search() {
    var term = q.value.trim().toLowerCase();
    if (!term) { results.classList.remove("open"); return; }
    var hits = index.map(function (e) {
      var inTitle = e.title.toLowerCase().indexOf(term) >= 0;
      var at = e.text.indexOf(term);
      if (!inTitle && at < 0) return null;
      var snippet = at >= 0 ? "…" + e.text.slice(Math.max(0, at - 30), at + 60) + "…" : "";
      return { e: e, score: inTitle ? 0 : 1, snippet: snippet };
    }).filter(Boolean).sort(function (a, b) { return a.score - b.score; }).slice(0, 8);
    picked = 0;
    results.innerHTML = hits.length ? "" : '<a><span>nothing found for that, try another word</span></a>';
    hits.forEach(function (h, i) {
      var a = document.createElement("a");
      a.href = "#" + h.e.id;
      if (i === 0) a.className = "sel";
      a.innerHTML = "<b></b><span></span>";
      a.firstChild.textContent = h.e.title;
      a.lastChild.textContent = h.snippet;
      a.addEventListener("click", function () { results.classList.remove("open"); q.value = ""; });
      results.appendChild(a);
    });
    results.classList.add("open");
  }
  q.addEventListener("input", search);
  q.addEventListener("keydown", function (e) {
    var items = results.querySelectorAll("a[href]");
    if (e.key === "ArrowDown" || e.key === "ArrowUp") {
      e.preventDefault();
      picked = (picked + (e.key === "ArrowDown" ? 1 : -1) + items.length) % Math.max(items.length, 1);
      items.forEach(function (a, i) { a.classList.toggle("sel", i === picked); });
    } else if (e.key === "Enter" && items[picked]) {
      location.hash = items[picked].getAttribute("href");
      results.classList.remove("open");
      q.value = "";
      q.blur();
    } else if (e.key === "Escape") {
      results.classList.remove("open");
      q.blur();
    }
  });
  document.addEventListener("keydown", function (e) {
    if (e.key === "/" && document.activeElement !== q) { e.preventDefault(); q.focus(); }
  });
  document.addEventListener("click", function (e) { if (!e.target.closest(".search")) results.classList.remove("open"); });

  // the demos: option rows pick one, switches flip, like the app's own
  document.querySelectorAll(".demo-row[data-group]").forEach(function (row) {
    row.addEventListener("click", function (e) {
      var b = e.target.closest(".opt");
      if (!b) return;
      row.querySelectorAll(".opt").forEach(function (o) { o.classList.toggle("on", o === b); });
      if (b.dataset.bar) document.getElementById("glassBar").className = "bar " + b.dataset.bar;
    });
  });
  document.querySelectorAll(".switch").forEach(function (s) {
    s.addEventListener("click", function () { s.classList.toggle("on"); saver(); });
  });

  // what data saver does with the two switches as they're set
  function saver() {
    var box = document.getElementById("saverDemo");
    if (!box) return;
    var on = box.querySelector('[data-k="on"]').classList.contains("on");
    var mobile = box.querySelector('[data-k="mobile"]').classList.contains("on");
    var mobileRow = box.querySelector('[data-k="mobile"]').parentNode;
    mobileRow.style.opacity = on ? "1" : "0.4";
    document.getElementById("saverText").textContent = !on
      ? "off: everything plays at the quality you picked."
      : (mobile ? "on mobile data: " : "always: ") + "youtube plays its lightest stream, modules play High instead of lossless, and canvas covers and video backgrounds stay off.";
  }
  saver();
})();
