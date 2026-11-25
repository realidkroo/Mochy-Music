// the wiki: one page per #name, the sidebar and search built from the articles, in-page jump pills, copy buttons, colours for json, and the little demos
(function () {
  var pages = Array.prototype.slice.call(document.querySelectorAll(".page"));
  var toc = document.getElementById("toc");
  var next = document.getElementById("next");
  var links = {};

  // keep old bookmarks working when a big page splits into focused pages
  var aliases = {
    "modules-floating-and-sheets": "modules-floating",
  };

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

  // build an "on this page" strip for pages with 3+ sections so long guides are easy to skim
  function slug(text) {
    return text.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "") || "section";
  }
  pages.forEach(function (page) {
    var heads = Array.prototype.slice.call(page.querySelectorAll(":scope > h2"));
    if (heads.length < 3) return;
    var nav = document.createElement("nav");
    nav.className = "onpage";
    nav.setAttribute("aria-label", "On this page");
    var label = document.createElement("small");
    label.textContent = "on this page";
    nav.appendChild(label);
    var seen = {};
    heads.forEach(function (h) {
      var base = slug(h.textContent);
      seen[base] = (seen[base] || 0) + 1;
      var secId = page.id + "--" + base + (seen[base] > 1 ? "-" + seen[base] : "");
      if (!h.id) h.id = secId;
      var a = document.createElement("a");
      a.href = "#" + h.id;
      a.className = "press";
      a.textContent = h.textContent;
      a.addEventListener("click", function (e) {
        e.preventDefault();
        h.scrollIntoView({ behavior: still ? "auto" : "smooth", block: "start" });
      });
      nav.appendChild(a);
    });
    var sub = page.querySelector(":scope > .sub") || page.querySelector(":scope > h1");
    if (sub && sub.nextSibling) page.insertBefore(nav, sub.nextSibling);
    else page.appendChild(nav);
  });

  var current = null;
  var leaving = 0;

  function resolveTarget(raw) {
    var clean = (raw || "").trim();
    if (!clean) return { page: pages[0], section: null };
    if (aliases[clean]) clean = aliases[clean];
    var el = document.getElementById(clean);
    if (el && el.classList.contains("page")) return { page: el, section: null };
    if (el) {
      var owner = el.closest(".page");
      if (owner) return { page: owner, section: el };
    }
    return { page: pages[0], section: null };
  }

  // the old page fades up and out first, then the new one rises in at the top
  function show(id) {
    var resolved = resolveTarget(id);
    var page = resolved.page;
    var section = resolved.section;
    Object.keys(links).forEach(function (k) { links[k].classList.toggle("on", k === page.id); });
    if (links[page.id] && links[page.id].scrollIntoView) {
      try { links[page.id].scrollIntoView({ block: "nearest" }); } catch (e) {}
    }
    document.title = page.dataset.title.replace(/&amp;/g, "&").replace(/^\d+\. /, "") + " · Mochy Wiki";
    document.getElementById("side").classList.remove("open");
    document.getElementById("tocToggle").setAttribute("aria-expanded", "false");
    clearTimeout(leaving);
    var old = current;
    current = page;
    if (!old || old === page || still) {
      swap(old, page, section);
      return;
    }
    old.classList.add("leaving");
    leaving = setTimeout(function () { swap(old, page, section); }, 160);
  }

  function swap(old, page, section) {
    pages.forEach(function (p) { p.classList.remove("leaving"); });
    if (old && old !== page) old.classList.remove("on");
    page.classList.add("on");
    page.querySelectorAll("img[loading]").forEach(function (img) { img.loading = "eager"; });
    var i = pages.indexOf(page);
    next.innerHTML = "";
    if (i > 0) next.appendChild(step(pages[i - 1], "previous"));
    else next.appendChild(document.createElement("span"));
    if (i < pages.length - 1) next.appendChild(step(pages[i + 1], "next"));
    next.classList.remove("in");
    void next.offsetWidth;
    next.classList.add("in");
    if (section) {
      requestAnimationFrame(function () { section.scrollIntoView({ behavior: "auto", block: "start" }); });
    } else {
      // straight to the top, not the smooth scroll the rest of the site uses, and again after the browser's own jump to the #name
      toTop();
      requestAnimationFrame(toTop);
    }
  }

  function toTop() { window.scrollTo({ top: 0, behavior: "instant" }); }

  function step(page, word) {
    var a = document.createElement("a");
    a.href = "#" + page.id;
    a.className = "press";
    a.innerHTML = "<small>" + word + "</small><b>" + page.dataset.title + "</b>";
    return a;
  }

  var still = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  // opening a link to a page, the browser jumps to its article once everything has loaded; the page should open at its title instead
  window.addEventListener("load", function () { if (!location.hash.includes("--") && window.scrollY < 400) toTop(); });
  window.addEventListener("hashchange", function () { show(location.hash.slice(1)); });
  show(location.hash.slice(1));

  // the hamburger on a phone opens the pages under the brand
  var burger = document.getElementById("tocToggle");
  burger.addEventListener("click", function () {
    burger.setAttribute("aria-expanded", String(document.getElementById("side").classList.toggle("open")));
  });

  // the fold button tucks the sidebar away to just the brand, and it stays that way next visit
  // the head already folded it before the first paint, so this only syncs the button
  var root = document.documentElement;
  var fold = document.getElementById("sideFold");
  function setFolded(folded) {
    root.classList.toggle("side-folded", folded);
    fold.setAttribute("aria-expanded", String(!folded));
    fold.setAttribute("aria-label", folded ? "Show the sidebar" : "Hide the sidebar");
  }
  setFolded(root.classList.contains("side-folded"));
  fold.addEventListener("click", function () {
    var folded = !root.classList.contains("side-folded");
    setFolded(folded);
    try { localStorage.setItem("wikiSideFolded", folded ? "1" : "0"); } catch (e) {}
  });
  // transitions only once the page has settled, so opening it folded doesn't play the fold
  requestAnimationFrame(function () { requestAnimationFrame(function () { root.classList.add("ready"); }); });

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

  // search: titles first, then headings, then the words inside; / jumps to it, arrows and enter pick
  var q = document.getElementById("q");
  var results = document.getElementById("results");
  var index = pages.map(function (p) {
    var headings = Array.prototype.slice.call(p.querySelectorAll("h2, h3")).map(function (h) { return h.textContent.toLowerCase(); }).join(" · ");
    return {
      id: p.id,
      group: p.dataset.group || "",
      title: p.dataset.title.replace(/&amp;/g, "&"),
      headings: headings,
      text: p.textContent.replace(/\s+/g, " ").toLowerCase(),
    };
  });
  var picked = 0;
  function search() {
    var term = q.value.trim().toLowerCase();
    if (!term) { results.classList.remove("open"); return; }
    var hits = index.map(function (e) {
      var inTitle = e.title.toLowerCase().indexOf(term) >= 0;
      var inHead = !inTitle && e.headings.indexOf(term) >= 0;
      var at = e.text.indexOf(term);
      if (!inTitle && !inHead && at < 0) return null;
      var snippet = at >= 0 ? "…" + e.text.slice(Math.max(0, at - 30), at + 64) + "…" : "";
      var score = inTitle ? 0 : (inHead ? 1 : 2);
      return { e: e, score: score, snippet: snippet };
    }).filter(Boolean).sort(function (a, b) { return a.score - b.score; }).slice(0, 10);
    picked = 0;
    results.innerHTML = hits.length ? "" : '<a><span>nothing found for that, try another word</span></a>';
    hits.forEach(function (h, i) {
      var a = document.createElement("a");
      a.href = "#" + h.e.id;
      if (i === 0) a.className = "sel";
      var b = document.createElement("b");
      b.textContent = h.e.title;
      if (h.e.group) {
        var em = document.createElement("em");
        em.textContent = h.e.group;
        b.appendChild(em);
      }
      var s = document.createElement("span");
      s.textContent = h.snippet;
      a.appendChild(b);
      a.appendChild(s);
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
      if (b.dataset.menuLayout) {
        var mock = document.getElementById("menuDemoMock");
        if (mock) mock.dataset.layout = b.dataset.menuLayout;
      }
      if (b.dataset.floatFill || b.dataset.floatCover) {
        var pill = document.getElementById("floatPill");
        if (pill) {
          if (b.dataset.floatFill) {
            pill.classList.toggle("solid", b.dataset.floatFill === "solid");
            pill.classList.toggle("sharp", b.dataset.floatFill === "sharp");
          }
          if (b.dataset.floatCover) {
            pill.classList.remove("cov-rounded", "cov-square");
            if (b.dataset.floatCover === "rounded") pill.classList.add("cov-rounded");
            if (b.dataset.floatCover === "square") pill.classList.add("cov-square");
          }
        }
      }
    });
  });

  // the states demo: a picked state is set on the demo and the css blends to it; "song" plays the pop again like an event
  document.querySelectorAll(".demo-row[data-target]").forEach(function (row) {
    row.addEventListener("click", function (e) {
      var b = e.target.closest(".opt");
      var target = document.getElementById(row.dataset.target);
      if (!b || !b.dataset.state || !target) return;
      var disc = target.querySelector(".disc");
      if (b.dataset.state !== "song") { target.dataset.state = b.dataset.state; return; }
      target.dataset.state = "playing";
      disc.classList.remove("pop");
      void disc.offsetWidth;
      disc.classList.add("pop");
    });
  });

  document.querySelectorAll(".switch").forEach(function (s) {
    s.addEventListener("click", function () {
      s.classList.toggle("on");
      if (s.dataset.floatTail !== undefined) {
        var pill = document.getElementById("floatPill");
        if (pill) pill.classList.toggle("tail", s.classList.contains("on"));
      }
      saver();
    });
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
