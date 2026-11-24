// links come from API assets; the page never builds a download URL by hand
(function () {
  "use strict";

  var API = "https://api.github.com/repos/realidkroo/Mochy-Music/releases?per_page=30";
  var COMMITS_API = "https://api.github.com/repos/realidkroo/Mochy-Music/commits/";
  var REPO = "https://github.com/realidkroo/Mochy-Music";
  var FALLBACK = REPO + "/releases/latest";
  var KEY = "mochy_releases_v1";
  var TTL = 60 * 60 * 1000;
  var LIMIT_TEXT = "Release data is unavailable right now.";
  var GH_PROFILE_SRC = "(?:https?:\\/\\/)?(?:www\\.)?github\\.com\\/([A-Za-z0-9-]+)(?:\\/)?(?![A-Za-z0-9_/-])";
  var ghProfile = function (value) {
    return new RegExp("^" + GH_PROFILE_SRC + "$", "i").exec(String(value || ""));
  };

  function byId(id) { return document.getElementById(id); }
  function text(id, value) { var n = byId(id); if (n) n.textContent = value == null ? "" : String(value); }
  function setMeta(value, label) { var n = byId("dlMeta"); if (!n) return; n.textContent = value; n.setAttribute("aria-label", label); }
  function show(id, on) { var n = byId(id); if (n) n.hidden = !on; }

  var REVEAL = ".rel-head > *, .rel-grid > *, .rel-cta, .rel-nightly, .rel-cmp, .rel-sections > *, .rel-older, .rel-timeline > article, .rel-bottom";
  var revealIO = null;
  function observeReveals() {
    if (!("IntersectionObserver" in window)) return;
    // reduced motion needs no reveal, so no observer
    if (window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    document.documentElement.classList.add("rel-motion");
    if (!revealIO) {
      revealIO = new IntersectionObserver(function (entries) {
        entries.forEach(function (entry) {
          if (!entry.isIntersecting) return;
          entry.target.classList.add("in");
          revealIO.unobserve(entry.target);
        });
      }, { rootMargin: "0px 0px -10% 0px", threshold: 0.08 });
    }
    Array.prototype.forEach.call(document.querySelectorAll(REVEAL), function (el) {
      if (!el.classList.contains("in")) revealIO.observe(el);
    });
  }
  function initNav() {
    var topnav = byId("topnav");
    if (!topnav) return;
    var nav = topnav.querySelector(".nav");
    var toggle = topnav.querySelector(".nav-toggle");
    if (nav && toggle) {
      toggle.addEventListener("click", function () {
        var open = nav.classList.toggle("open");
        toggle.setAttribute("aria-expanded", String(open));
      });
      Array.prototype.forEach.call(nav.querySelectorAll(".nav-links a"), function (a) {
        a.addEventListener("click", function () {
          nav.classList.remove("open");
          toggle.setAttribute("aria-expanded", "false");
        });
      });
    }
    function sync() { topnav.classList.toggle("solid", window.scrollY > 24); }
    window.addEventListener("scroll", sync, { passive: true });
    sync();
  }

  // private mode can throw on localStorage; a miss just means a fresh fetch
  function cacheRead() {
    try {
      var raw = localStorage.getItem(KEY);
      var box = raw ? JSON.parse(raw) : null;
      return box && Array.isArray(box.data) ? box : null;
    } catch (e) { return null; }
  }
  function cacheWrite(data) {
    try { localStorage.setItem(KEY, JSON.stringify({ ts: Date.now(), data: data })); } catch (e) {}
  }

  function version(r) {
    return String((r && r.tag_name) || "").replace(/^(mochy|musicfy)-/i, "");
  }
  function isNightly(r) {
    return !!(r && !r.draft && r.prerelease && /nightly/i.test(String(r.name || "") + " " + String(r.tag_name || "")));
  }
  function isMusicfy(r) {
    return /musicfy/i.test(String((r && r.name) || "") + " " + String((r && r.tag_name) || ""));
  }
  function stable(list) {
    return (list || []).filter(function (r) { return r && !r.draft && !isNightly(r); });
  }
  function nightlyOf(list) {
    return (list || []).filter(isNightly)[0] || null;
  }
  function apks(r) {
    return ((r && r.assets) || []).filter(function (a) {
      return a && /\.apk$/i.test(String(a.name || "")) && a.browser_download_url;
    });
  }
  function pickAsset(r, kind) {
    var found = apks(r).filter(function (a) { return new RegExp(kind, "i").test(String(a.name)); });
    if (!found.length) return null;
    var foss = found.filter(function (a) { return /foss/i.test(String(a.name)); });
    return foss[0] || found[0];
  }
  function sizeOf(a) {
    return a && typeof a.size === "number" ? (a.size / 1048576).toFixed(1) + " MB" : "";
  }
  function totalCount(list) {
    return (list || []).reduce(function (sum, r) {
      return sum + apks(r).reduce(function (n, a) { return n + Number(a.download_count || 0); }, 0);
    }, 0);
  }
  function compact(n) {
    if (n >= 1e6) return (n / 1e6).toFixed(1).replace(/\.0$/, "") + "M";
    if (n >= 1e3) return Math.round(n / 1e3) + "k";
    return String(n);
  }
  function renderTotal() {
    text("dlTotal", totalCount(dl.list).toLocaleString("en-GB") + " downloads across " + dl.list.length + " releases shown");
  }
  function dateOf(value) {
    var d = new Date(value);
    if (isNaN(d.getTime())) return "";
    return d.toLocaleDateString("en-GB", { day: "2-digit", month: "short", year: "numeric" });
  }
  function commitRef(r) {
    return String((r && r.target_commitish) || "");
  }
  function commitOf(r) {
    var ref = commitRef(r);
    return /^[0-9a-f]{7,40}$/i.test(ref) ? ref.slice(0, 7) : "";
  }
  function slug(tag) {
    return String(tag || "release").replace(/[^a-z0-9]+/gi, "-").replace(/^-|-$/g, "");
  }
  function stripMd(raw) {
    return String(raw || "").replace(/<[^>]+>/g, " ").replace(/^#{1,6}\s*/gm, "")
      .replace(/[*_`>]/g, "").replace(/\[([^\]]+)\]\([^)]+\)/g, "$1").replace(/\s+/g, " ").trim()
      .replace(new RegExp(GH_PROFILE_SRC, "gi"), "@$1");
  }
  function fetchPage(page) {
    var url = API + (page > 1 ? "&page=" + page : "");
    return fetch(url, { headers: { Accept: "application/vnd.github+json" } }).then(function (res) {
      if (!res.ok) throw new Error("HTTP " + res.status);
      return res.json();
    });
  }
  function loadReleases() {
    var cached = cacheRead();
    if (cached && Date.now() - cached.ts < TTL) return Promise.resolve(cached.data);
    return fetchPage(1).then(function (data) {
      cacheWrite(data);
      return data;
    }).catch(function () {
      return cached ? cached.data : null;
    });
  }

  // skip code, links and tags so only bare @handles become anchors
  function linkMentions(html) {
    return html.split(/(<code\b[^>]*>[\s\S]*?<\/code>|<pre\b[^>]*>[\s\S]*?<\/pre>|<a\b[^>]*>[\s\S]*?<\/a>)/gi).map(function (part) {
      if (/^<(code|pre|a)\b/i.test(part)) return part;
      return part.split(/(<[^>]+>)/).map(function (bit) {
        if (bit.charAt(0) === "<") return bit;
        return bit.replace(/(^|[^\w@./-])(@[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?)/g, function (_, before, mention) {
          return before + '<a class="rel-user" href="https://github.com/' + mention.slice(1) + '" target="_blank" rel="noopener noreferrer">' + mention + "</a>";
        });
      }).join("");
    }).join("");
  }
  function renderMarkdown(raw, node) {
    node.textContent = "";
    if (window.marked && window.DOMPurify) {
      var html;
      try { html = window.marked.parse(String(raw || "")); } catch (e) { html = ""; }
      node.innerHTML = linkMentions(window.DOMPurify.sanitize(html));
      Array.prototype.forEach.call(node.querySelectorAll("a"), function (a) {
        a.setAttribute("target", "_blank");
        a.setAttribute("rel", "noopener noreferrer");
        // a GitHub profile URL reads better as an @handle
        var m = ghProfile(a.getAttribute("href"));
        if (m && ghProfile(a.textContent)) {
          a.textContent = "@" + m[1];
          a.className = "rel-user";
          a.setAttribute("title", "GitHub: @" + m[1]);
        }
      });
    } else {
      // the CDN can fail; keep notes readable as plain text
      node.style.whiteSpace = "pre-wrap";
      node.textContent = stripMd(raw);
    }
  }
  // one long note should not bury every release below it
  function clampBody(body, holder) {
    if (body.scrollHeight <= 240) return;
    body.classList.add("clamped");
    var btn = document.createElement("button");
    btn.type = "button";
    btn.className = "rel-show-all";
    btn.textContent = "show all";
    btn.addEventListener("click", function () {
      body.classList.remove("clamped");
      if (btn.parentNode) btn.parentNode.removeChild(btn);
    });
    holder.appendChild(btn);
  }

  function chip(label, href, kind) {
    var a = document.createElement("a");
    a.className = "rel-chip press" + (kind ? " " + kind : "");
    a.textContent = label;
    a.href = href;
    return a;
  }
  function notesLink(r) {
    var a = chip("notes", "changelog.html#" + slug(r.tag_name), "notes");
    a.title = "Read release notes";
    return a;
  }
  function fallbackChip(r) {
    return chip("View on GitHub", (r && r.html_url) || FALLBACK);
  }
  function assetChips(r) {
    var box = document.createElement("div");
    box.className = "rel-body-chips";
    box.setAttribute("role", "group");
    box.setAttribute("aria-label", "Release links");
    var arm = pickAsset(r, "arm64");
    var uni = pickAsset(r, "universal");
    box.appendChild(notesLink(r));
    if (arm) box.appendChild(chip("arm64", arm.browser_download_url, "apk mono"));
    if (uni) box.appendChild(chip("universal", uni.browser_download_url, "apk mono"));
    if (!arm || !uni) box.appendChild(fallbackChip(r));
    return box;
  }
  function olderRow(r) {
    var row = document.createElement("div");
    row.className = "rel-row";
    var id = document.createElement("div");
    id.className = "rel-row-id";
    var v = document.createElement("span");
    v.className = "rel-ver";
    v.textContent = version(r);
    id.appendChild(v);
    var d = document.createElement("span");
    d.className = "rel-sub";
    d.textContent = dateOf(r.published_at);
    id.appendChild(d);
    if (isMusicfy(r)) {
      var b = document.createElement("span");
      b.className = "rel-badge quiet";
      b.textContent = "Musicfy";
      id.appendChild(b);
    }
    var actions = document.createElement("div");
    actions.className = "rel-row-actions";
    actions.appendChild(notesLink(r));
    var arm = pickAsset(r, "arm64");
    var uni = pickAsset(r, "universal");
    if (arm) actions.appendChild(chip("arm64", arm.browser_download_url, "apk"));
    if (uni) actions.appendChild(chip("Universal", uni.browser_download_url, "apk"));
    if (!arm || !uni) actions.appendChild(fallbackChip(r));
    row.appendChild(id);
    row.appendChild(actions);
    return row;
  }

  /* ---------- download page ---------- */
  var dl = { channel: "stable", selected: "arm64", list: [], page: 1, commits: {}, entered: false };

  function currentRelease() {
    if (dl.channel === "nightly") return nightlyOf(dl.list);
    return stable(dl.list)[0] || null;
  }
  function fillVariant(kind, r) {
    var a = pickAsset(r, kind);
    text("file-" + kind, a ? a.name : "no apk in this release");
    text("meta-" + kind, a ? sizeOf(a) + " · " + Number(a.download_count || 0).toLocaleString("en-GB") + " downloads" : "");
    var card = byId("variant-" + kind);
    if (card) {
      var on = dl.selected === kind;
      card.classList.toggle("picked", on);
      card.setAttribute("aria-pressed", on ? "true" : "false");
    }
  }
  function renderSelected(r) {
    if (!r) return;
    var a = pickAsset(r, dl.selected);
    text("selected-name", (dl.channel === "nightly" ? "Nightly" : version(r)) + " · " + (dl.selected === "arm64" ? "arm64" : "Universal"));
    text("selected-file", a ? a.name : "no apk in this release");
    var btn = byId("downloadBtn");
    if (btn) {
      btn.href = a ? a.browser_download_url : ((r && r.html_url) || FALLBACK);
      btn.textContent = a ? "Download " + (dl.selected === "arm64" ? "arm64" : "Universal") : "View on GitHub";
    }
  }
  function buildDate(r) {
    return dateOf(r.published_at || (r.assets && r.assets[0] && r.assets[0].updated_at));
  }
  // nightly tags carry a branch, so resolve its sha once
  function resolveCommit(r) {
    var ref = commitRef(r);
    if (!ref || dl.commits[ref] || commitOf(r)) return;
    dl.commits[ref] = "";
    fetch(COMMITS_API + encodeURIComponent(ref), { headers: { Accept: "application/vnd.github+json" } })
      .then(function (res) {
        if (!res.ok) throw new Error("HTTP " + res.status);
        return res.json();
      })
      .then(function (data) {
        var sha = String((data && data.sha) || "");
        if (!/^[0-9a-f]{7,40}$/i.test(sha)) throw new Error("bad sha");
        dl.commits[ref] = sha.slice(0, 7);
        if (dl.channel === "nightly" && currentRelease() === r) {
          setMeta(buildDate(r) + " · #" + dl.commits[ref], "Nightly build from " + buildDate(r) + ", commit " + dl.commits[ref]);
        }
      })
      .catch(function () {
        dl.commits[ref] = "unknown";
        if (dl.channel === "nightly" && currentRelease() === r) {
          setMeta(buildDate(r) + " · #—", "Nightly build from " + buildDate(r) + ", commit unknown");
        }
      });
  }

  function renderDownload(data) {
    dl.list = data || [];
    var list = stable(dl.list);
    var r = currentRelease();
    var nightlyOn = dl.channel === "nightly";
    if (!r || (!nightlyOn && !list.length)) {
      text("dlErrorText", !r && nightlyOn ? "No nightly build is published right now." : LIMIT_TEXT);
      show("dlSkeleton", false);
      show("dlError", true);
      show("dlContent", false);
      return;
    }
    show("dlSkeleton", false);
    show("dlError", false);
    show("dlContent", true);
    if (!dl.entered) { var box = byId("dlContent"); if (box) box.classList.add("rel-enter"); dl.entered = true; }
    show("nightlyOnly", nightlyOn);
    observeReveals();
    var meta;
    if (nightlyOn) {
      var sha = dl.commits[commitRef(r)] || commitOf(r) || "…";
      meta = buildDate(r) + " · #" + sha;
      setMeta(meta, "Nightly build from " + buildDate(r) + ", commit " + sha);
    } else {
      meta = version(r) + " · " + dateOf(r.published_at);
      setMeta(meta, "Mochy " + version(r) + ", published " + dateOf(r.published_at));
    }
    if (nightlyOn) resolveCommit(r);
    text("dlTitle", nightlyOn ? "Mochy Nightly" : "Download Mochy");
    text("dlIntro", nightlyOn
      ? "Test builds made from the newest code. You get new things first, and new bugs too."
      : "two builds. pick the one that fits. not sure? take arm64. it fits most phones from the last 8 years.");
    fillVariant("arm64", r);
    fillVariant("universal", r);
    renderSelected(r);
    var older = byId("olderList");
    if (older) {
      older.textContent = "";
      list.slice(1).forEach(function (old) { older.appendChild(olderRow(old)); });
    }
    dl.page = 1;
    show("moreVersions", list.length > 1);
    renderTotal();
  }
  function initDownload() {
    var stableTab = byId("channelStable");
    var nightlyTab = byId("channelNightly");
    var seg = document.querySelector(".rel-segmented");
    function refresh(channel) {
      var box = byId("dlContent");
      if (!box) return;
      if (seg) seg.setAttribute("data-channel", channel);
      // reflow between class changes so the direction slide restarts on every switch
      box.classList.remove("rel-refresh", "to-nightly", "to-stable");
      void box.offsetWidth;
      box.classList.add("to-" + channel, "rel-refresh");
    }
    function pick(channel) {
      if (channel === dl.channel) return;
      dl.channel = channel;
      if (stableTab) stableTab.setAttribute("aria-pressed", channel === "stable" ? "true" : "false");
      if (nightlyTab) nightlyTab.setAttribute("aria-pressed", channel === "nightly" ? "true" : "false");
      var box = byId("dlContent");
      if (box) {
        // replay reveal so the new information arrives gradually
        Array.prototype.forEach.call(box.querySelectorAll(REVEAL), function (el) { if (el !== seg) el.classList.remove("in"); });
      }
      renderDownload(dl.list);
      refresh(channel);
    }
    if (stableTab) stableTab.addEventListener("click", function () { pick("stable"); });
    if (nightlyTab) nightlyTab.addEventListener("click", function () { pick("nightly"); });
    ["arm64", "universal"].forEach(function (kind) {
      var card = byId("variant-" + kind);
      if (card) card.addEventListener("click", function () { dl.selected = kind; renderSelected(currentRelease()); fillVariant("arm64", currentRelease()); fillVariant("universal", currentRelease()); });
    });
    var more = byId("moreVersions");
    if (more) more.addEventListener("click", function () {
      var next = dl.page + 1;
      fetchPage(next).then(function (data) {
        var list = stable(data);
        var older = byId("olderList");
        list.forEach(function (old) { if (older) older.appendChild(olderRow(old)); });
        dl.list = dl.list.concat(data || []);
        dl.page = next;
        // a short page means there is nothing left to fetch
        if (!list.length || list.length < 30) show("moreVersions", false);
        renderTotal();
      }).catch(function () { show("moreVersions", false); });
    });
    if (/[?&]channel=nightly/.test(location.search)) pick("nightly");

    loadReleases().then(function (data) {
      if (data) renderDownload(data); else {
        text("dlErrorText", LIMIT_TEXT);
        show("dlSkeleton", false);
        show("dlError", true);
        show("dlContent", false);
      }
    });
  }

  /* ---------- changelog page ---------- */
  function changeEntry(r, first) {
    var art = document.createElement("article");
    art.id = slug(r.tag_name);
    var when = document.createElement("div");
    when.className = "rel-when";
    var head = document.createElement("div");
    head.className = "rel-when-head";
    var v = document.createElement("div");
    v.className = "rel-ver" + (isMusicfy(r) ? " dim" : "");
    v.textContent = version(r);
    head.appendChild(v);
    if (first) {
      var latest = document.createElement("span");
      latest.className = "rel-status";
      latest.textContent = "latest";
      head.appendChild(latest);
    } else if (isMusicfy(r)) {
      var old = document.createElement("span");
      old.className = "rel-badge quiet";
      old.textContent = "Musicfy";
      head.appendChild(old);
    }
    when.appendChild(head);
    var d = document.createElement("span");
    d.className = "rel-sub";
    d.textContent = dateOf(r.published_at);
    when.appendChild(d);
    var col = document.createElement("div");
    col.className = "rel-body-col";
    var card = document.createElement("div");
    card.className = "rel-note-card";
    var body = document.createElement("div");
    body.className = "rel-body";
    renderMarkdown(r.body, body);
    card.appendChild(body);
    clampBody(body, card);
    card.appendChild(assetChips(r));
    col.appendChild(card);
    art.appendChild(when);
    art.appendChild(col);
    return art;
  }
  function initChangelog() {
    var listNode = byId("changeList");
    var page = 1;
    function draw(data, append) {
      var list = stable(data);
      if (!list.length && !append) { show("changeSkeleton", false); show("changeError", true); return; }
      show("changeSkeleton", false);
      show("changeError", false);
      list.forEach(function (r, i) {
        if (listNode) listNode.appendChild(changeEntry(r, !append && i === 0));
      });
      observeReveals();
      show("moreChanges", true);
    }
    loadReleases().then(function (data) {
      if (data) draw(data, false); else { show("changeSkeleton", false); show("changeError", true); }
    });
    var more = byId("moreChanges");
    if (more) more.addEventListener("click", function () {
      var next = page + 1;
      fetchPage(next).then(function (data) {
        draw(data, true);
        page = next;
        if (stable(data).length < 30) show("moreChanges", false);
      }).catch(function () { show("moreChanges", false); });
    });
  }

  /* ---------- home card ---------- */
  function initHome() {
    loadReleases().then(function (data) {
      var r = data ? stable(data)[0] : null;
      show("relHomeSkeleton", false);
      if (!r) { show("relHomeError", true); show("heroTotal", false); return; }
      show("relHomeError", false);
      show("relHomeCard", true);
      text("heroTotalCount", compact(totalCount(data)));
      text("heroTotalReleases", String((data || []).length));
      show("heroTotal", true);
      text("relHomeVersion", version(r));
      text("relHomeDate", dateOf(r.published_at));
      var notes = stripMd(r.body).split(/\s+/).slice(0, 32).join(" ");
      text("relHomeNotes", notes.length > 260 ? notes.slice(0, 260) + "…" : notes);
      show("heroNew", Date.now() - new Date(r.published_at).getTime() < 14 * 24 * 60 * 60 * 1000);
      text("heroNewText", "Mochy " + version(r) + " is out");
      var a = byId("relHomeArm");
      if (a) { a.href = "download.html"; a.textContent = "Download"; }
    });
    var nightlyLink = byId("nightlyTeaser");
    if (nightlyLink) nightlyLink.href = "download.html?channel=nightly";
  }

  document.addEventListener("DOMContentLoaded", function () {
    var page = document.body.dataset.page;
    if (page === "download") {
      initNav();
      initDownload();
    } else if (page === "changelog") {
      initNav();
      initChangelog();
    } else if (page === "home") {
      initHome();
    }
    observeReveals();
  });
})();
