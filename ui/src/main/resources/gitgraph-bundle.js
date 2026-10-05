var WebGitGraph = (() => {
  var __getOwnPropNames = Object.getOwnPropertyNames;
  var __esm = (fn, res, err) => function __init() {
    if (err) throw err[0];
    try {
      return fn && (res = (0, fn[__getOwnPropNames(fn)[0]])(fn = 0)), res;
    } catch (e) {
      throw err = [e], e;
    }
  };
  var __commonJS = (cb, mod) => function __require() {
    try {
      return mod || (0, cb[__getOwnPropNames(cb)[0]])((mod = { exports: {} }).exports, mod), mod.exports;
    } catch (e) {
      throw mod = 0, e;
    }
  };

  // node_modules/@web-git-graph/web/dist/register.js
  function firstFreeLane(active, reserved) {
    const occupied = new Set(active.map((lane2) => lane2.lane));
    let lane = 0;
    while (occupied.has(lane) || reserved.has(lane)) lane += 1;
    return lane;
  }
  function layoutGitGraph(commits, options = {}) {
    const rowOffset = options.previous?.rowOffset ?? 0;
    const active = (options.previous?.lanes ?? []).map((lane) => ({ ...lane }));
    let nextColour = options.previous?.nextColour ?? 0;
    const nodes = [];
    const segments = [];
    let maxLane = active.reduce((max, lane) => Math.max(max, lane.lane), -1);
    for (let localRow = 0; localRow < commits.length; localRow += 1) {
      const commit = commits[localRow];
      const row = rowOffset + localRow;
      const matching = active.filter((lane) => lane.target === commit.oid).sort((a, b) => a.lane - b.lane);
      const reserved = /* @__PURE__ */ new Set();
      const primary = matching[0] ?? {
        lane: firstFreeLane(active, reserved),
        target: commit.oid,
        colour: nextColour++
      };
      maxLane = Math.max(maxLane, primary.lane);
      nodes.push({
        oid: commit.oid,
        lane: primary.lane,
        row,
        colour: primary.colour,
        kind: commit.kind
      });
      for (const lane of active) {
        if (lane.target !== commit.oid) {
          segments.push({
            from: { lane: lane.lane, row: lane.fromRow },
            to: { lane: lane.lane, row },
            colour: lane.colour
          });
          lane.fromRow = row;
        }
      }
      for (const lane of matching) {
        segments.push({
          from: { lane: lane.lane, row: lane.fromRow },
          to: { lane: primary.lane, row },
          colour: lane.colour,
          anchor: "to"
        });
      }
      for (let index = active.length - 1; index >= 0; index -= 1) {
        if (active[index].target === commit.oid) active.splice(index, 1);
      }
      const parents = commit.parents.filter(Boolean);
      const firstParent = parents[0];
      if (firstParent) {
        active.push({
          lane: primary.lane,
          target: firstParent,
          colour: primary.colour,
          fromRow: row
        });
        reserved.add(primary.lane);
      }
      for (const parent of parents.slice(1)) {
        const existing = active.find((lane2) => lane2.target === parent);
        if (existing) {
          segments.push({
            from: { lane: primary.lane, row },
            to: { lane: existing.lane, row: row + 1 },
            colour: existing.colour,
            anchor: "from"
          });
          continue;
        }
        const lane = firstFreeLane(active, reserved);
        reserved.add(lane);
        maxLane = Math.max(maxLane, lane);
        const colour = nextColour++;
        active.push({ lane, target: parent, colour, fromRow: row + 1 });
        segments.push({
          from: { lane: primary.lane, row },
          to: { lane, row: row + 1 },
          colour,
          anchor: "from"
        });
      }
    }
    const endRow = rowOffset + commits.length;
    for (const lane of active) {
      if (lane.fromRow < endRow) {
        segments.push({
          from: { lane: lane.lane, row: lane.fromRow },
          to: { lane: lane.lane, row: endRow },
          colour: lane.colour,
          dangling: true
        });
        lane.fromRow = endRow;
      }
    }
    return {
      nodes,
      segments,
      state: {
        lanes: active.map((lane) => ({ ...lane })),
        nextColour,
        rowOffset: endRow
      },
      laneCount: Math.max(1, maxLane + 1)
    };
  }
  function revisionFor(commit) {
    if (commit.kind === "working-tree") return { kind: "working-tree" };
    if (commit.kind === "stash") return { kind: "stash", oid: commit.oid };
    return { kind: "commit", oid: commit.oid };
  }
  function shortOid(oid) {
    return oid.startsWith("__") ? oid.replaceAll("_", "") : oid.slice(0, 8);
  }
  function shortRefName(name) {
    return name.replace(/^refs\/(heads|tags|remotes)\//, "");
  }
  function pad(value) {
    return String(value).padStart(2, "0");
  }
  function formatDate(value, format = "datetime") {
    if (!value) return "\u2014";
    const date = new Date(value);
    if (Number.isNaN(date.valueOf())) return value;
    if (format === "relative") {
      const elapsed = date.valueOf() - Date.now();
      const relative = new Intl.RelativeTimeFormat(void 0, { numeric: "auto" });
      for (const [unit, milliseconds] of RELATIVE_UNITS) {
        if (Math.abs(elapsed) >= milliseconds) {
          return relative.format(Math.round(elapsed / milliseconds), unit);
        }
      }
      return relative.format(Math.round(elapsed / 1e3), "second");
    }
    const day = `${date.getFullYear()}/${pad(date.getMonth() + 1)}/${pad(date.getDate())}`;
    return format === "date" ? day : `${day} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
  }
  function avatarColour(email) {
    let hash = 0;
    for (let index = 0; index < email.length; index += 1) {
      hash = (hash * 31 + email.charCodeAt(index)) % 360;
    }
    return `hsl(${hash} 44% 40%)`;
  }
  async function gravatarUrl(email) {
    if (typeof crypto === "undefined" || !crypto.subtle) return void 0;
    const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(email));
    const hex = [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
    return `https://www.gravatar.com/avatar/${hex}?s=48&d=404`;
  }
  function buildFileTree(changes) {
    const root = { dirs: /* @__PURE__ */ new Map(), files: [] };
    for (const change of changes) {
      const parts = change.path.split("/");
      let node = root;
      for (const part of parts.slice(0, -1)) {
        let next = node.dirs.get(part);
        if (!next) {
          next = { dirs: /* @__PURE__ */ new Map(), files: [] };
          node.dirs.set(part, next);
        }
        node = next;
      }
      node.files.push(change);
    }
    return root;
  }
  function baseName(path) {
    return path.split("/").pop() ?? path;
  }
  function pendingBars(widths) {
    const container = document.createElement("div");
    container.className = "pending details-pending";
    container.setAttribute("aria-hidden", "true");
    container.innerHTML = widths.map((width) => `<span class="pending-bar" style="width:${width}%"></span>`).join("");
    return container;
  }
  function svgElement(name, attributes) {
    const element = document.createElementNS("http://www.w3.org/2000/svg", name);
    for (const [key, value] of Object.entries(attributes)) element.setAttribute(key, value);
    return element;
  }
  function defineWebGitGraph(name = ELEMENT_NAME) {
    if (typeof customElements !== "undefined" && !customElements.get(name)) {
      customElements.define(name, WebGitGraphElement);
    }
    return WebGitGraphElement;
  }
  var ELEMENT_NAME, PALETTE, LANE_COLOURED_REFS, IS_APPLE, PENDING_ROW_WIDTHS, PENDING_FILE_WIDTHS, STYLES, RELATIVE_UNITS, AVATAR_URLS, AVATAR_PENDING, HTMLElementBase, WebGitGraphElement;
  var init_register = __esm({
    "node_modules/@web-git-graph/web/dist/register.js"() {
      ELEMENT_NAME = "web-git-graph";
      PALETTE = ["#e3008c", "#007acc", "#00c853", "#ff8c00", "#b180d7", "#00b7c3", "#dcdcaa"];
      LANE_COLOURED_REFS = /* @__PURE__ */ new Set(["current", "head"]);
      IS_APPLE = typeof navigator !== "undefined" && /mac|iphone|ipad|ipod/i.test(navigator.userAgent ?? "");
      PENDING_ROW_WIDTHS = [78, 54, 88, 41, 69, 82, 47, 61];
      PENDING_FILE_WIDTHS = [64, 88, 45, 73, 52];
      STYLES = `
:host {
  /* Prefer VS Code / host theme tokens when present (they inherit into the
     shadow tree), then fall back to a neutral dark palette for standalone use. */
  --wgg-bg: var(--vscode-editor-background, #1e1e1e);
  --wgg-panel: var(--vscode-sideBar-background, var(--vscode-editorWidget-background, var(--vscode-editor-background, #252526)));
  --wgg-panel-raised: var(--vscode-editorWidget-background, var(--vscode-sideBar-background, #2d2d30));
  --wgg-ink: var(--vscode-foreground, var(--vscode-editor-foreground, #d4d4d4));
  --wgg-muted: var(--vscode-descriptionForeground, #a9a9a9);
  --wgg-faint: var(--vscode-disabledForeground, #777);
  --wgg-line: var(--vscode-panel-border, var(--vscode-widget-border, #3c3c3c));
  --wgg-hover: var(--vscode-list-hoverBackground, #2a2d2e);
  --wgg-selected: var(--vscode-list-inactiveSelectionBackground, var(--vscode-editor-inactiveSelectionBackground, #37373d));
  --wgg-accent: var(--vscode-focusBorder, #3794ff);
  --wgg-warning: var(--vscode-editorWarning-foreground, #cca700);
  --wgg-row-height: 24px;
  --wgg-graph-width: 72px;
  --wgg-date-width: 142px;
  --wgg-author-width: 150px;
  --wgg-commit-width: 82px;
  display: block;
  min-height: 420px;
  color: var(--wgg-ink);
  font-family: var(--wgg-font, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif);
  background: var(--wgg-bg);
  border: 1px solid var(--wgg-line);
  overflow: hidden;
  color-scheme: dark;
  container-type: inline-size;
  container-name: wgg;
}
:host([theme="light"]) {
  --wgg-bg: var(--vscode-editor-background, #ffffff);
  --wgg-panel: var(--vscode-sideBar-background, var(--vscode-editorWidget-background, var(--vscode-editor-background, #f3f3f3)));
  --wgg-panel-raised: var(--vscode-editorWidget-background, var(--vscode-sideBar-background, #f8f8f8));
  --wgg-ink: var(--vscode-foreground, var(--vscode-editor-foreground, #333333));
  --wgg-muted: var(--vscode-descriptionForeground, #616161);
  --wgg-faint: var(--vscode-disabledForeground, #8e8e8e);
  --wgg-line: var(--vscode-panel-border, var(--vscode-widget-border, #d4d4d4));
  --wgg-hover: var(--vscode-list-hoverBackground, #f0f0f0);
  --wgg-selected: var(--vscode-list-inactiveSelectionBackground, var(--vscode-editor-inactiveSelectionBackground, #e4e6f1));
  --wgg-accent: var(--vscode-focusBorder, #3794ff);
  --wgg-warning: var(--vscode-editorWarning-foreground, #cca700);
  color-scheme: light;
}
:host([hosted]) .theme-toggle { display: none; }
:host([density="compact"]) { --wgg-row-height: 20px; }
* { box-sizing: border-box; }
button, input, select { font: inherit; color: inherit; }
button { cursor: pointer; }
.shell { position: relative; min-height: inherit; height: 100%; display: grid; grid-template-rows: auto minmax(0, 1fr); }
.toolbar {
  min-height: 42px;
  display: flex;
  gap: 16px;
  align-items: center;
  padding: 6px 10px;
  background: var(--wgg-panel);
  border-bottom: 1px solid var(--wgg-line);
  font-size: 12px;
}
.branch-control, .remote-control { display: flex; align-items: center; gap: 7px; white-space: nowrap; }
.branch-control strong, .remote-control { font-weight: 600; }
.remote-control input { margin: 0; accent-color: var(--wgg-accent); }
.ref-select {
  height: 28px; max-width: min(250px, 40cqw); display: flex; align-items: center; gap: 6px;
  border: 1px solid var(--wgg-line); background: var(--wgg-bg); border-radius: 2px; padding: 3px 7px;
}
.ref-select:hover { background: var(--wgg-hover); }
.ref-select-label { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.caret { flex: none; color: var(--wgg-muted); font-size: 9px; }
.repository-name {
  min-width: 0; flex: 1; color: var(--wgg-muted); overflow: hidden; text-overflow: ellipsis;
  white-space: nowrap; text-align: center;
}
.search {
  width: min(240px, 40cqw); height: 28px; border: 1px solid var(--wgg-line); background: var(--wgg-bg);
  border-radius: 2px; padding: 4px 7px; outline: none; font-size: 12px;
}
.search:focus, select:focus, button:focus-visible { outline: 1px solid var(--wgg-accent); outline-offset: -1px; }
.tools { display: flex; align-items: center; gap: 4px; margin-left: auto; }
.find { display: flex; align-items: center; gap: 2px; }
.search-count {
  min-width: 44px; padding: 0 3px; text-align: center; color: var(--wgg-muted);
  font-variant-numeric: tabular-nums; white-space: nowrap;
}
.icon-button:disabled { color: var(--wgg-faint); background: transparent; cursor: default; }
select, .icon-button {
  height: 28px; border: 1px solid var(--wgg-line); background: var(--wgg-bg);
  border-radius: 2px; padding: 3px 7px;
}
.icon-button { min-width: 28px; color: var(--wgg-muted); background: transparent; border-color: transparent; }
.icon-button:hover { color: var(--wgg-ink); background: var(--wgg-hover); }
.body {
  min-height: 0; position: relative;
}
.history { min-width: 0; height: 100%; display: grid; grid-template-rows: 34px minmax(0, 1fr); }
.header, .row {
  display: grid;
  /* Description may shrink to zero so the graph column keeps its reserved
     width; the commit message ellipsises before branch chips are clipped. */
  grid-template-columns:
    var(--wgg-graph-width) minmax(0, 1fr) var(--wgg-date-width)
    var(--wgg-author-width) var(--wgg-commit-width);
  align-items: center;
}
.header {
  padding-right: 10px; background: var(--wgg-bg); color: var(--wgg-ink);
  border-bottom: 1px solid var(--wgg-line); font-size: 12px; font-weight: 600;
}
.header > span {
  height: 100%; display: flex; align-items: center; justify-content: center;
  padding: 0 8px; border-right: 1px solid var(--wgg-line);
}
.scroller { position: relative; overflow: auto; min-height: 0; outline: none; scrollbar-color: var(--wgg-faint) transparent; }
.spacer { position: relative; min-width: max(100%, calc(var(--wgg-graph-width) + 280px + var(--wgg-date-width) + var(--wgg-author-width) + var(--wgg-commit-width))); }
.window { position: absolute; inset: 0 0 auto 0; min-height: 100%; }
.row {
  height: var(--wgg-row-height); padding-right: 10px;
  border-bottom: 1px solid color-mix(in srgb, var(--wgg-line) 30%, transparent);
  position: absolute; left: 0; right: 0; cursor: default; font-size: 12px;
}
.row:hover, .row.preview, .row.context-active { background: var(--wgg-hover); }
.row.match { background: color-mix(in srgb, var(--wgg-warning) 16%, transparent); }
.row.match-current { box-shadow: inset 0 0 0 1px var(--wgg-warning); }
.row.selected { background: var(--wgg-selected); }
.row.compare { box-shadow: inset 2px 0 var(--wgg-warning); }
.row.merge .message { color: var(--wgg-muted); }
.row.working-tree .message { font-weight: 600; }
.row:focus { outline: 1px solid var(--wgg-accent); outline-offset: -1px; }
.graph-cell { height: 100%; position: relative; overflow: hidden; }
.subject {
  min-width: 0; display: flex; align-items: center; gap: 5px; padding: 0 4px;
  overflow: hidden; position: relative; z-index: 2;
}
.message { min-width: 0; flex: 1 1 auto; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
/* Branch chips never ellipsis \u2014 same behaviour as vscode-git-graph's .gitRef. */
.refs { flex: 0 0 auto; display: flex; gap: 2px; }
.ref {
  flex: 0 0 auto; white-space: nowrap;
  font: 600 10px/15px var(--wgg-font, inherit); padding: 0 5px; border-radius: 2px;
  border: 1px solid var(--ref-color, var(--wgg-accent));
  background: color-mix(in srgb, var(--ref-color, var(--wgg-accent)) 18%, transparent);
  color: var(--wgg-ink);
}
/* Only the checked-out branch is solid, so "you are here" reads at a glance
   while every other branch is emphasised by its lane-coloured border. */
.ref.current { background: var(--ref-color, var(--wgg-accent)); color: #fff; }
/* Remote branches recede by colour rather than by line style: a grey outline
   with no tint, so they read as "not here" without a busy dashed border. */
.ref.remote {
  /* --wgg-faint rather than --wgg-line: the border has to stay legible against
     both the dark and the light background. */
  border-color: var(--wgg-faint);
  background: transparent;
  color: var(--wgg-muted);
  font-weight: 400;
}
.ref.tag, .ref.stash { background: var(--ref-color); color: #fff; }
.ref.tag { --ref-color: #0e639c; }
.ref.stash { --ref-color: #9b2f86; }
.author, .date, .oid {
  min-width: 0; padding: 0 6px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  color: var(--wgg-ink); position: relative; z-index: 2;
}
.date, .author { text-align: center; }
.author { display: flex; align-items: center; justify-content: center; gap: 5px; }
.author-name { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.avatar {
  position: relative; flex: none; width: 16px; height: 16px; border-radius: 50%; overflow: hidden;
  display: grid; place-items: center; font: 600 9px/1 var(--wgg-font, inherit); color: #fff;
  background: var(--avatar-color, var(--wgg-faint));
}
.avatar img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.shell[data-hide-date] { --wgg-date-width: 0px; }
.shell[data-hide-author] { --wgg-author-width: 0px; }
.shell[data-hide-commit] { --wgg-commit-width: 0px; }
.shell[data-hide-date] .col-date, .shell[data-hide-date] .date,
.shell[data-hide-author] .col-author, .shell[data-hide-author] .author,
.shell[data-hide-commit] .col-commit, .shell[data-hide-commit] .oid {
  padding: 0; border-right: 0; visibility: hidden;
}
.menu {
  position: absolute; z-index: 20; min-width: 190px; max-width: min(320px, 90%); padding: 4px;
  background: var(--wgg-panel-raised); border: 1px solid var(--wgg-line); border-radius: 4px;
  box-shadow: 0 4px 14px rgb(0 0 0 / 32%); font-size: 12px;
}
.menu-scroll { max-height: min(340px, 55vh); overflow: auto; scrollbar-color: var(--wgg-faint) transparent; }
.menu-item {
  width: 100%; display: flex; align-items: center; gap: 8px; padding: 4px 8px;
  border: 0; border-radius: 2px; background: transparent; text-align: left; white-space: nowrap;
}
.menu-item:hover:not(:disabled) { background: var(--wgg-hover); }
.menu-item:disabled { color: var(--wgg-faint); cursor: default; }
.menu-label { min-width: 0; overflow: hidden; text-overflow: ellipsis; }
.menu-check { flex: none; width: 12px; text-align: center; color: var(--wgg-accent); }
.menu-group {
  padding: 6px 8px 2px; color: var(--wgg-muted); font-size: 10px; font-weight: 600;
  text-transform: uppercase; letter-spacing: 0.04em;
}
.menu-separator { height: 1px; margin: 4px 2px; background: var(--wgg-line); }
.oid { font-family: var(--wgg-mono, ui-monospace, SFMono-Regular, Consolas, monospace); font-size: 11px; text-align: center; }
.graph {
  /* Clip to the graph column so strokes never paint over description text, and
     paint above the expanded details panel so lanes cross it unbroken \u2014 the
     same layering vscode-git-graph uses for #commitGraph over #cdv. */
  position: absolute; left: 0; z-index: 4; pointer-events: none;
  width: var(--wgg-graph-width); overflow: hidden;
}
.graph path { fill: none; stroke-width: 2; vector-effect: non-scaling-stroke; }
.graph circle { stroke-width: 1.5; vector-effect: non-scaling-stroke; }
.inline-details {
  position: absolute; left: 0; right: 0; z-index: 3;
  display: flex; overflow: hidden;
  padding-left: var(--wgg-graph-width);
  background: var(--wgg-panel);
  border-top: 1px solid var(--wgg-line); border-bottom: 1px solid var(--wgg-line);
  font-size: 12px;
  animation: details-open 120ms ease-out;
}
.details-summary { flex: 1 1 55%; min-width: 0; overflow: auto; padding: 8px 12px 12px; }
.details-files {
  flex: 1 1 45%; min-width: 0; overflow: auto; padding: 5px 26px 8px 8px;
  border-left: 1px solid var(--wgg-line);
}
.details-close {
  position: absolute; top: 3px; right: 5px; z-index: 1;
  width: 22px; height: 22px; padding: 0; border: 0; border-radius: 2px;
  background: transparent; color: var(--wgg-muted); font-size: 14px; line-height: 1;
}
.details-close:hover { background: var(--wgg-hover); color: var(--wgg-ink); }
.details-heading { margin: 0 0 6px; font-size: 12px; font-weight: 600; }
.meta { display: grid; grid-template-columns: max-content minmax(0, 1fr); gap: 2px 10px; margin: 0; }
.meta dt { color: var(--wgg-muted); font-weight: 600; }
.meta dd { margin: 0; overflow-wrap: anywhere; }
.meta .oid-value { font-family: var(--wgg-mono, ui-monospace, SFMono-Regular, Consolas, monospace); font-size: 11px; }
.commit-body { margin: 10px 0 0; white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.45; }
.actions { display: flex; flex-wrap: wrap; gap: 5px; margin-top: 12px; }
.action {
  border: 1px solid var(--wgg-line); border-radius: 2px; background: var(--wgg-panel-raised);
  padding: 3px 8px; font-size: 11px;
}
.action.primary { border-color: var(--wgg-accent); background: #0e639c; color: #fff; }
.tree, .tree ul { margin: 0; padding: 0; list-style: none; }
.tree ul { padding-left: 14px; }
.tree-dir, .tree-file {
  width: 100%; display: flex; align-items: center; gap: 6px; padding: 1px 4px;
  border: 0; border-radius: 0; background: transparent; text-align: left;
  font-size: 11px; white-space: nowrap;
}
.tree-dir:hover, .tree-file:hover { background: var(--wgg-hover); }
.tree-file.active { background: var(--wgg-selected); }
.twistie { flex: none; width: 10px; color: var(--wgg-muted); font-size: 9px; }
.dir-name { min-width: 0; overflow: hidden; text-overflow: ellipsis; color: var(--wgg-muted); }
.change-code { flex: none; width: 12px; text-align: center; font: 11px var(--wgg-mono, ui-monospace, monospace); color: var(--wgg-muted); }
.change-code.add { color: #81b88b; }
.change-code.modify { color: #e2c08d; }
.change-code.delete { color: #f14c4c; }
.change-code.rename, .change-code.copy { color: #6cb8e6; }
.change-path { min-width: 0; flex: 1; overflow: hidden; text-overflow: ellipsis; }
.stats { flex: none; font: 10px var(--wgg-mono, ui-monospace, monospace); color: var(--wgg-muted); }
.no-changes { margin: 6px 4px; color: var(--wgg-faint); font-size: 11px; }
.patch {
  margin: 10px 0 0; padding: 10px; overflow: auto; border: 1px solid var(--wgg-line);
  background: var(--wgg-bg); font: 10px/1.55 var(--wgg-mono, ui-monospace, monospace); white-space: pre; tab-size: 2;
}
.empty, .loading, .error { display: grid; place-items: center; min-height: 220px; color: var(--wgg-muted); text-align: center; padding: 30px; }
.error { color: #ff8585; }
/* Waiting states breathe rather than blink: a slow opacity swell on the whole
   surface plus a sheen travelling along each placeholder bar. Both are pure
   compositor work, so they stay smooth while Git is being read. */
@keyframes wgg-breathe { 0%, 100% { opacity: 0.5; } 50% { opacity: 0.9; } }
@keyframes wgg-sheen { from { background-position: 180% 0; } to { background-position: -80% 0; } }
.pending { position: relative; animation: wgg-breathe 2.6s ease-in-out infinite; }
.pending-bar {
  height: 8px; border-radius: 4px;
  background: linear-gradient(
    100deg,
    color-mix(in srgb, var(--wgg-line) 55%, transparent) 18%,
    color-mix(in srgb, var(--wgg-muted) 40%, transparent) 42%,
    color-mix(in srgb, var(--wgg-line) 55%, transparent) 66%
  );
  background-size: 260% 100%;
  animation: wgg-sheen 2.2s linear infinite;
}
.pending-dot {
  justify-self: start; margin-left: 11.5px;
  width: 9px; height: 9px; border-radius: 50%;
  background: color-mix(in srgb, var(--wgg-muted) 45%, transparent);
}
/* Laid out on the row grid, so the placeholder occupies exactly the space the
   history is about to take: the first paint settles instead of jumping. */
.pending-rows { position: relative; display: grid; align-content: start; }
.pending-rows::before {
  content: ""; position: absolute; left: 15.5px; top: 12px; bottom: 12px; width: 1px;
  background: color-mix(in srgb, var(--wgg-muted) 30%, transparent);
}
.pending-row {
  display: grid; grid-template-columns: var(--wgg-graph-width) minmax(0, 1fr);
  align-items: center; height: var(--wgg-row-height); padding-right: 10px;
}
.pending-label { color: var(--wgg-muted); font-size: 12px; text-align: center; }
.loading-view { position: relative; min-height: 220px; height: 100%; overflow: hidden; }
/* A soft wash breathing over the whole surface, so the wait reads as one
   living panel rather than eight bars ticking on their own. */
.loading-view::after {
  content: ""; position: absolute; inset: 0; pointer-events: none;
  background: radial-gradient(
    62% 58% at 42% 34%,
    color-mix(in srgb, var(--wgg-accent) 12%, transparent),
    transparent 72%
  );
  animation: wgg-breathe 3.6s ease-in-out infinite;
}
.loading-view .pending-label { position: relative; padding: 22px 20px 0; }
.details-pending { display: grid; gap: 11px; padding: 8px 2px 0; }
.details-pending .pending-bar { height: 7px; }
@media (prefers-reduced-motion: reduce) {
  .pending, .pending-bar, .loading-view::after { animation: none; }
  .pending { opacity: 0.7; }
}
.load-more { position: absolute; left: 50%; display: block; margin: 8px 0; transform: translateX(-50%); }
@keyframes details-open {
  from { opacity: 0; }
  to { opacity: 1; }
}
/* Use the host width \u2014 not the IDE window \u2014 so a narrow webview/side panel
   collapses columns the same way a narrow browser window would. */
@container wgg (max-width: 760px) {
  .toolbar { gap: 8px; }
  .remote-control, .repository-name, .find { display: none; }
  .branch-control { flex: 1; min-width: 0; }
  .ref-select { flex: 1; max-width: none; }
  .header, .row {
    grid-template-columns: var(--wgg-graph-width) minmax(0, 1fr) var(--wgg-commit-width);
  }
  .spacer {
    min-width: max(100%, calc(var(--wgg-graph-width) + 160px + var(--wgg-commit-width)));
  }
  .col-date, .col-author, .date, .author { display: none; }
  .inline-details { flex-direction: column; padding-left: 12px; }
  .details-files { border-left: 0; border-top: 1px solid var(--wgg-line); }
}
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { transition-duration: 0.001ms !important; animation-duration: 0.001ms !important; }
}
`;
      RELATIVE_UNITS = [
        ["year", 31536e6],
        ["month", 2592e6],
        ["week", 6048e5],
        ["day", 864e5],
        ["hour", 36e5],
        ["minute", 6e4]
      ];
      AVATAR_URLS = /* @__PURE__ */ new Map();
      AVATAR_PENDING = /* @__PURE__ */ new Set();
      HTMLElementBase = typeof HTMLElement === "undefined" ? class {
      } : HTMLElement;
      WebGitGraphElement = class extends HTMLElementBase {
        static observedAttributes = ["theme", "density", "columns", "date-format", "date-type", "avatars"];
        #provider;
        #page = { commits: [], refs: [], hasMore: false };
        #layout = layoutGitGraph([]);
        #search = "";
        #matches = [];
        #matchCursor = -1;
        #selectedRefs = [];
        #menu;
        #menuClose;
        #menuSync;
        #contextOid;
        #selectedOid;
        #compareOid;
        #details;
        #comparison;
        #fileDiff;
        #collapsedDirs = /* @__PURE__ */ new Set();
        #detailsElement;
        #detailsSignature;
        #detailsPending = false;
        #loading = false;
        #loadingMore = false;
        #error;
        #abort;
        #rowHeight = 24;
        #overscan = 8;
        #detailsHeight = 240;
        #showRemoteRefs = true;
        #root;
        addEventListener(type, listener, options) {
          super.addEventListener(type, listener, options);
        }
        removeEventListener(type, listener, options) {
          super.removeEventListener(type, listener, options);
        }
        /** Assign these on* properties instead of calling addEventListener. */
        ongitgraphcommitsselect = null;
        ongitgraphcommitsopen = null;
        ongitgraphcompare = null;
        ongitgraphfileopen = null;
        ongitgraphloadmore = null;
        ongitgrapherror = null;
        ongitgraphrefresh = null;
        ongitgraphcontextmenu = null;
        constructor() {
          super();
          this.#root = this.attachShadow({ mode: "open" });
          this.#root.innerHTML = `<style>${STYLES}</style><div class="shell"></div>`;
        }
        connectedCallback() {
          this.#renderShell();
          if (this.#provider && this.#page.commits.length === 0) void this.#load(false);
        }
        disconnectedCallback() {
          this.#closeMenu();
        }
        attributeChangedCallback() {
          this.#rowHeight = this.getAttribute("density") === "compact" ? 20 : 24;
          this.#applyColumns();
          void this.#resolveAvatars();
          this.#renderWindow();
        }
        get provider() {
          return this.#provider;
        }
        set provider(value) {
          this.#provider = value;
          if (!value) return;
          this.#selectedRefs = [];
          this.#page = {
            ...this.#page,
            repositoryId: void 0,
            repositoryName: void 0,
            cursor: void 0,
            hasMore: false
          };
          if (this.isConnected) void this.#load(false);
        }
        get data() {
          return this.#page;
        }
        set data(value) {
          this.setData(value);
        }
        get theme() {
          return this.getAttribute("theme") ?? "dark";
        }
        set theme(value) {
          this.setAttribute("theme", value);
        }
        get density() {
          return this.getAttribute("density") ?? "comfortable";
        }
        set density(value) {
          this.setAttribute("density", value);
        }
        /** Comma-separated subset of `date,author,commit`; graph and description always show. */
        get columns() {
          return this.getAttribute("columns") ?? "date,author,commit";
        }
        set columns(value) {
          this.setAttribute("columns", value);
        }
        get dateFormat() {
          const value = this.getAttribute("date-format");
          return value === "date" || value === "relative" ? value : "datetime";
        }
        set dateFormat(value) {
          this.setAttribute("date-format", value);
        }
        get dateType() {
          return this.getAttribute("date-type") === "authored" ? "authored" : "committed";
        }
        set dateType(value) {
          this.setAttribute("date-type", value);
        }
        /** Off by default: resolving avatars discloses author addresses to Gravatar. */
        get avatars() {
          const value = this.getAttribute("avatars");
          return value !== null && value !== "false" && value !== "off";
        }
        set avatars(value) {
          if (value) this.setAttribute("avatars", "");
          else this.removeAttribute("avatars");
        }
        /** Refs the history is walked from; empty means every tip. */
        get refs() {
          return this.#selectedRefs;
        }
        set refs(value) {
          this.#applyRefs([...value]);
        }
        /**
         * Re-reads the current page in place. The scroll position and the open commit
         * are kept, so a manual refresh — or a host that refreshes on every Git
         * change — does not throw the reader back to the top of the history.
         */
        refresh() {
          const event = new CustomEvent("gitgraph-refresh", {
            bubbles: true,
            composed: true,
            cancelable: true,
            detail: { repositoryId: this.#page.repositoryId }
          });
          if (this.dispatchEvent(event) && this.#provider) void this.#load(false, true);
        }
        setData(page) {
          this.#page = {
            ...page,
            commits: [...page.commits],
            refs: [...page.refs]
          };
          this.#selectedOid = void 0;
          this.#compareOid = void 0;
          this.#details = void 0;
          this.#detailsPending = false;
          this.#comparison = void 0;
          this.#error = void 0;
          this.#collapsedDirs.clear();
          this.#recompute();
          this.#root.querySelector(".scroller")?.scrollTo({ top: 0 });
        }
        appendPage(page) {
          const scrollTop = this.#root.querySelector(".scroller")?.scrollTop ?? 0;
          const seen = new Set(this.#page.commits.map((commit) => commit.oid));
          this.#page = {
            ...this.#page,
            ...page,
            commits: [...this.#page.commits, ...page.commits.filter((commit) => !seen.has(commit.oid))],
            refs: this.#mergeRefs(this.#page.refs, page.refs)
          };
          this.#recompute();
          queueMicrotask(() => {
            const scroller = this.#root.querySelector(".scroller");
            if (!scroller) return;
            scroller.scrollTop = scrollTop;
            this.#renderWindow();
          });
        }
        selectCommit(oid) {
          const commit = this.#page.commits.find((item) => item.oid === oid);
          if (!commit) return;
          this.#selectedOid = oid;
          this.#compareOid = void 0;
          this.#comparison = void 0;
          this.#fileDiff = void 0;
          this.#collapsedDirs.clear();
          this.dispatchEvent(
            new CustomEvent("gitgraph-commit-select", {
              bubbles: true,
              composed: true,
              detail: { commit }
            })
          );
          void this.#loadDetails(commit);
          this.#renderWindow();
          this.#renderDetailsPanel();
          queueMicrotask(() => this.#revealDetails(commit.oid));
        }
        async compareCommits(baseOid, headOid) {
          const base = this.#page.commits.find((item) => item.oid === baseOid);
          const head = this.#page.commits.find((item) => item.oid === headOid);
          if (!base || !head || !this.#provider?.compare) return;
          this.#selectedOid = baseOid;
          this.#compareOid = headOid;
          this.#fileDiff = void 0;
          this.#comparison = void 0;
          this.#detailsPending = true;
          this.#collapsedDirs.clear();
          this.#renderWindow();
          this.#renderDetailsPanel();
          try {
            this.#comparison = await this.#provider.compare(
              this.#page.repositoryId,
              revisionFor(base),
              revisionFor(head)
            );
            this.dispatchEvent(
              new CustomEvent("gitgraph-compare", {
                bubbles: true,
                composed: true,
                detail: this.#comparison
              })
            );
          } catch (error) {
            this.#emitError(error);
          }
          this.#renderDetailsPanel();
        }
        focusCommit(oid) {
          const index = this.#page.commits.findIndex((commit) => commit.oid === oid);
          if (index < 0) return;
          const scroller = this.#root.querySelector(".scroller");
          scroller?.scrollTo({
            top: this.#rowTop(index, this.#selectedIndex()),
            behavior: "smooth"
          });
          queueMicrotask(() => {
            this.#root.querySelector(`.row[data-oid="${CSS.escape(oid)}"]`)?.focus();
          });
        }
        #mergeRefs(left, right) {
          const refs = new Map(left.map((ref) => [`${ref.kind}:${ref.name}`, ref]));
          for (const ref of right) refs.set(`${ref.kind}:${ref.name}`, ref);
          return [...refs.values()];
        }
        #recompute() {
          this.#layout = layoutGitGraph(this.#page.commits);
          this.#updateMatches(false);
          this.#renderShell();
          this.#applyColumns();
          this.#updateToolbar();
          void this.#resolveAvatars();
          this.#renderWindow();
        }
        /**
         * Builds the static shell once. Rebuilding it on data changes would destroy
         * the search input mid-typing (dropping focus after every keystroke), so all
         * data-driven updates go through #updateToolbar and #renderWindow instead.
         */
        #renderShell() {
          const shell = this.#root.querySelector(".shell");
          if (!shell || shell.querySelector(".toolbar")) return;
          shell.innerHTML = `
      <div class="toolbar">
        <div class="branch-control">
          <strong>Branches:</strong>
          <button class="ref-select" type="button" aria-haspopup="menu" aria-expanded="false"
            aria-label="Select branches and tags">
            <span class="ref-select-label">Show All</span><span class="caret">\u25BE</span>
          </button>
        </div>
        <label class="remote-control">
          <input class="remote-toggle" type="checkbox" checked>
          <span>Show Remote Branches</span>
        </label>
        <span class="repository-name"></span>
        <div class="tools">
          <div class="find">
            <input class="search" type="search" placeholder="Find commits\u2026" aria-label="Search commits">
            <span class="search-count" hidden></span>
            <button class="icon-button search-prev" type="button" aria-label="Previous match" disabled>\u2191</button>
            <button class="icon-button search-next" type="button" aria-label="Next match" disabled>\u2193</button>
          </div>
          <button class="icon-button refresh" type="button" aria-label="Refresh" title="Refresh">\u21BB</button>
          <button class="icon-button theme-toggle" type="button" aria-label="Toggle theme">\u25D0</button>
        </div>
      </div>
      <div class="body">
        <section class="history">
          <div class="header" aria-hidden="true">
            <span class="col-graph">Graph</span><span class="col-description">Description</span
            ><span class="col-date">Date</span><span class="col-author">Author</span
            ><span class="col-commit">Commit</span>
          </div>
          <div class="scroller" role="treegrid" aria-label="Git commit history" tabindex="0">
            <div class="spacer"><div class="window"></div></div>
          </div>
        </section>
      </div>`;
          const search = shell.querySelector(".search");
          search.value = this.#search;
          search.addEventListener("input", () => {
            this.#search = search.value;
            this.#updateMatches(true);
            this.#updateToolbar();
            this.#renderWindow();
            this.#scrollToMatch();
          });
          search.addEventListener("keydown", (event) => {
            if (event.key === "Enter") {
              event.preventDefault();
              this.#gotoMatch(event.shiftKey ? -1 : 1);
            } else if (event.key === "Escape" && search.value) {
              event.stopPropagation();
              search.value = "";
              this.#search = "";
              this.#updateMatches(true);
              this.#updateToolbar();
              this.#renderWindow();
            }
          });
          shell.querySelector(".search-prev")?.addEventListener("click", () => this.#gotoMatch(-1));
          shell.querySelector(".search-next")?.addEventListener("click", () => this.#gotoMatch(1));
          shell.querySelector(".refresh")?.addEventListener("click", () => this.refresh());
          shell.querySelector(".theme-toggle")?.addEventListener("click", () => {
            this.theme = this.theme === "light" ? "dark" : "light";
          });
          const remoteToggle = shell.querySelector(".remote-toggle");
          remoteToggle.checked = this.#showRemoteRefs;
          remoteToggle.addEventListener("change", () => {
            this.#showRemoteRefs = remoteToggle.checked;
            this.#closeMenu();
            this.#renderWindow();
          });
          const refSelect = shell.querySelector(".ref-select");
          refSelect.addEventListener("click", () => {
            if (this.#menu?.dataset.menu === "refs") this.#closeMenu();
            else this.#openRefMenu(refSelect);
          });
          const scroller = shell.querySelector(".scroller");
          scroller.addEventListener("scroll", () => {
            this.#renderWindow();
            if (this.#page.hasMore && !this.#loadingMore && scroller.scrollTop + scroller.clientHeight > scroller.scrollHeight - this.#rowHeight * 4) {
              void this.#load(true);
            }
          });
          scroller.addEventListener("keydown", (event) => this.#onKeyDown(event));
          this.#renderWindow();
        }
        #updateToolbar() {
          const shell = this.#root.querySelector(".shell");
          if (!shell || !shell.querySelector(".toolbar")) return;
          shell.querySelector(".repository-name").textContent = this.#page.repositoryName ?? this.#page.repositoryId ?? "data provider";
          const selected = this.#selectedRefs;
          shell.querySelector(".ref-select-label").textContent = selected.length === 0 ? "Show All" : selected.length === 1 ? shortRefName(selected[0]) : `${selected.length} selected`;
          this.#updateSearchStatus();
        }
        #applyColumns() {
          const shell = this.#root.querySelector(".shell");
          if (!shell) return;
          const attribute = this.getAttribute("columns");
          const wanted = attribute === null ? void 0 : new Set(attribute.split(",").map((name) => name.trim().toLowerCase()).filter(Boolean));
          for (const column of ["date", "author", "commit"]) {
            shell.toggleAttribute(`data-hide-${column}`, wanted !== void 0 && !wanted.has(column));
          }
        }
        #openMenu(name, anchor) {
          this.#closeMenu();
          const shell = this.#root.querySelector(".shell");
          const menu = document.createElement("div");
          menu.className = "menu";
          menu.dataset.menu = name;
          menu.setAttribute("role", "menu");
          shell.append(menu);
          this.#menu = menu;
          const onPointerDown = (event) => {
            const path = event.composedPath();
            if (!path.includes(menu) && !(anchor && path.includes(anchor))) this.#closeMenu();
          };
          const onKeyDown = (event) => {
            if (event.key !== "Escape") return;
            event.stopPropagation();
            this.#closeMenu();
          };
          const onReflow = () => this.#closeMenu();
          const scroller = this.#root.querySelector(".scroller");
          document.addEventListener("pointerdown", onPointerDown, true);
          document.addEventListener("keydown", onKeyDown, true);
          scroller?.addEventListener("scroll", onReflow);
          window.addEventListener("resize", onReflow);
          this.#menuClose = () => {
            document.removeEventListener("pointerdown", onPointerDown, true);
            document.removeEventListener("keydown", onKeyDown, true);
            scroller?.removeEventListener("scroll", onReflow);
            window.removeEventListener("resize", onReflow);
            menu.remove();
          };
          anchor?.setAttribute("aria-expanded", "true");
          return menu;
        }
        #closeMenu() {
          const close = this.#menuClose;
          this.#menu = void 0;
          this.#menuClose = void 0;
          this.#menuSync = void 0;
          this.#contextOid = void 0;
          close?.();
          this.#markContextRow();
          this.#root.querySelector(".ref-select")?.setAttribute("aria-expanded", "false");
        }
        /** Toggles the marker class in place; re-rendering the window would undo the
         * very thing this exists to avoid. */
        #markContextRow() {
          for (const row of this.#root.querySelectorAll(".row")) {
            row.classList.toggle("context-active", row.dataset.oid === this.#contextOid);
          }
        }
        /** Clamps the menu inside the host so it never spills out of the component. */
        #positionMenu(menu, left, top) {
          menu.style.left = "0px";
          menu.style.top = "0px";
          const host = this.getBoundingClientRect();
          const box = menu.getBoundingClientRect();
          menu.style.left = `${Math.min(Math.max(4, left), Math.max(4, host.width - box.width - 4))}px`;
          menu.style.top = `${Math.min(Math.max(4, top), Math.max(4, host.height - box.height - 4))}px`;
        }
        #menuItem(label, options) {
          const item = document.createElement("button");
          item.className = "menu-item";
          item.type = "button";
          item.setAttribute("role", "menuitem");
          item.disabled = options.enabled === false;
          if (options.checked !== void 0) {
            const check = document.createElement("span");
            check.className = "menu-check";
            check.textContent = options.checked ? "\u2713" : "";
            item.append(check);
          }
          const text = document.createElement("span");
          text.className = "menu-label";
          text.textContent = label;
          item.append(text);
          item.addEventListener("click", options.onSelect);
          return item;
        }
        #openRefMenu(anchor) {
          const menu = this.#openMenu("refs", anchor);
          const groups = [
            ["Local Branches", "head"],
            ["Remote Branches", "remote"],
            ["Tags", "tag"]
          ];
          const checks = /* @__PURE__ */ new Map();
          const selected = () => new Set(this.#selectedRefs);
          const allItem = this.#menuItem("Show All", {
            checked: this.#selectedRefs.length === 0,
            onSelect: () => this.#applyRefs([])
          });
          menu.append(allItem);
          const scroll = document.createElement("div");
          scroll.className = "menu-scroll";
          let count = 0;
          for (const [heading, kind] of groups) {
            const refs = this.#page.refs.filter(
              (ref) => ref.kind === kind && (kind !== "remote" || this.#showRemoteRefs)
            );
            if (refs.length === 0) continue;
            const title = document.createElement("div");
            title.className = "menu-group";
            title.textContent = heading;
            scroll.append(title);
            for (const ref of refs) {
              const item = this.#menuItem(shortRefName(ref.name), {
                checked: this.#selectedRefs.includes(ref.name),
                onSelect: () => this.#toggleRef(ref.name)
              });
              checks.set(ref.name, item.querySelector(".menu-check"));
              scroll.append(item);
              count += 1;
            }
          }
          if (count > 0) {
            menu.append(Object.assign(document.createElement("div"), { className: "menu-separator" }), scroll);
          }
          this.#menuSync = () => {
            const active = selected();
            allItem.querySelector(".menu-check").textContent = active.size === 0 ? "\u2713" : "";
            for (const [name, check] of checks) check.textContent = active.has(name) ? "\u2713" : "";
          };
          const anchorBox = anchor.getBoundingClientRect();
          const host = this.getBoundingClientRect();
          this.#positionMenu(menu, anchorBox.left - host.left, anchorBox.bottom - host.top + 2);
        }
        #toggleRef(name) {
          const next = new Set(this.#selectedRefs);
          if (next.has(name)) next.delete(name);
          else next.add(name);
          this.#applyRefs([...next]);
        }
        #applyRefs(refs) {
          this.#selectedRefs = refs;
          this.#updateToolbar();
          this.#menuSync?.();
          if (this.#provider) void this.#load(false);
        }
        #openCommitMenu(commit, clientX, clientY) {
          const proceed = this.dispatchEvent(
            new CustomEvent("gitgraph-context-menu", {
              bubbles: true,
              composed: true,
              cancelable: true,
              detail: { commit, clientX, clientY }
            })
          );
          if (!proceed) return;
          const menu = this.#openMenu("commit");
          this.#contextOid = commit.oid;
          this.#markContextRow();
          const subject = commit.message.split("\n", 1)[0] ?? "";
          menu.append(
            this.#menuItem("Copy Commit Hash", {
              enabled: commit.kind !== "working-tree",
              onSelect: () => {
                this.#closeMenu();
                void this.#copy(commit.oid);
              }
            }),
            this.#menuItem("Copy Commit Subject", {
              enabled: subject.length > 0,
              onSelect: () => {
                this.#closeMenu();
                void this.#copy(subject);
              }
            }),
            this.#menuItem("Compare with Selected Commit", {
              enabled: Boolean(
                this.#selectedOid && this.#selectedOid !== commit.oid && this.#provider?.compare
              ),
              onSelect: () => {
                const base = this.#selectedOid;
                this.#closeMenu();
                if (base) void this.compareCommits(base, commit.oid);
              }
            })
          );
          if (commit.url) {
            const url = commit.url;
            menu.append(
              this.#menuItem("Open in Remote \u2197", {
                onSelect: () => {
                  this.#closeMenu();
                  window.open(url, "_blank", "noopener,noreferrer");
                }
              })
            );
          }
          const host = this.getBoundingClientRect();
          this.#positionMenu(menu, clientX - host.left, clientY - host.top);
        }
        async #copy(value) {
          try {
            await navigator.clipboard.writeText(value);
          } catch {
            const area = document.createElement("textarea");
            area.value = value;
            area.setAttribute("aria-hidden", "true");
            area.style.position = "fixed";
            area.style.opacity = "0";
            document.body.append(area);
            area.select();
            document.execCommand("copy");
            area.remove();
          }
        }
        #avatarElement(commit) {
          const email = commit.author?.email?.trim().toLowerCase() ?? "";
          const avatar = document.createElement("span");
          avatar.className = "avatar";
          avatar.setAttribute("aria-hidden", "true");
          const initial = document.createElement("span");
          initial.textContent = (commit.author?.name ?? "?").trim().slice(0, 1).toUpperCase() || "?";
          avatar.append(initial);
          if (email) avatar.style.setProperty("--avatar-color", avatarColour(email));
          const url = commit.author?.avatarUrl ?? (email ? AVATAR_URLS.get(email) : void 0);
          if (url) {
            const image = document.createElement("img");
            image.src = url;
            image.alt = "";
            image.loading = "lazy";
            image.decoding = "async";
            image.addEventListener("error", () => image.remove());
            avatar.append(image);
          }
          return avatar;
        }
        async #resolveAvatars() {
          if (!this.avatars) return;
          const pending = /* @__PURE__ */ new Set();
          for (const commit of this.#page.commits) {
            const email = commit.author?.email?.trim().toLowerCase();
            if (email && !commit.author?.avatarUrl && !AVATAR_URLS.has(email) && !AVATAR_PENDING.has(email)) {
              pending.add(email);
            }
          }
          if (pending.size === 0) return;
          for (const email of pending) AVATAR_PENDING.add(email);
          await Promise.all(
            [...pending].map(async (email) => {
              const url = await gravatarUrl(email).catch(() => void 0);
              if (url) AVATAR_URLS.set(email, url);
              AVATAR_PENDING.delete(email);
            })
          );
          this.#renderWindow();
        }
        #updateMatches(resetCursor) {
          const needle = this.#search.trim().toLocaleLowerCase();
          if (!needle) {
            this.#matches = [];
            this.#matchCursor = -1;
            return;
          }
          const matches = [];
          this.#page.commits.forEach((commit, index) => {
            const author = `${commit.author?.name ?? ""} ${commit.author?.email ?? ""}`;
            if (`${commit.oid} ${commit.message} ${author}`.toLocaleLowerCase().includes(needle)) {
              matches.push(index);
            }
          });
          this.#matches = matches;
          this.#matchCursor = matches.length === 0 ? -1 : resetCursor ? 0 : Math.min(Math.max(this.#matchCursor, 0), matches.length - 1);
        }
        #updateSearchStatus() {
          const count = this.#root.querySelector(".search-count");
          if (!count) return;
          const active = this.#search.trim().length > 0;
          count.hidden = !active;
          count.textContent = active ? `${this.#matchCursor + 1}/${this.#matches.length}` : "";
          const disabled = this.#matches.length === 0;
          this.#root.querySelector(".search-prev").disabled = disabled;
          this.#root.querySelector(".search-next").disabled = disabled;
        }
        #gotoMatch(delta) {
          if (this.#matches.length === 0) return;
          this.#matchCursor = (this.#matchCursor + delta + this.#matches.length) % this.#matches.length;
          this.#updateSearchStatus();
          this.#renderWindow();
          this.#scrollToMatch();
        }
        #scrollToMatch() {
          const index = this.#matches[this.#matchCursor];
          if (index === void 0) return;
          const scroller = this.#root.querySelector(".scroller");
          if (!scroller) return;
          const top = this.#rowTop(index, this.#selectedIndex());
          if (top < scroller.scrollTop || top + this.#rowHeight > scroller.scrollTop + scroller.clientHeight) {
            scroller.scrollTo({ top: Math.max(0, top - scroller.clientHeight / 2) });
          }
        }
        #renderWindow() {
          const scroller = this.#root.querySelector(".scroller");
          const spacer = this.#root.querySelector(".spacer");
          const windowElement = this.#root.querySelector(".window");
          if (!scroller || !spacer || !windowElement) return;
          if (this.#page.commits.length === 0) {
            this.#detailsElement = void 0;
            this.#detailsSignature = void 0;
          }
          if (this.#loading && this.#page.commits.length === 0) {
            spacer.style.height = "100%";
            windowElement.innerHTML = `
        <div class="loading-view">
          <div class="pending pending-rows" aria-hidden="true">${PENDING_ROW_WIDTHS.map(
              (width) => `<div class="pending-row"><span class="pending-dot"></span><span class="pending-bar" style="width:${width}%"></span></div>`
            ).join("")}</div>
          <p class="pending-label"><slot name="loading">Reading the commit DAG\u2026</slot></p>
        </div>`;
            return;
          }
          if (this.#error && this.#page.commits.length === 0) {
            spacer.style.height = "100%";
            windowElement.innerHTML = `<div class="error"><slot name="error"></slot></div>`;
            const slot = windowElement.querySelector("slot");
            if (slot) slot.textContent = this.#error;
            return;
          }
          if (this.#page.commits.length === 0) {
            spacer.style.height = "100%";
            windowElement.innerHTML = `<div class="empty"><slot name="empty">No commits match this view.</slot></div>`;
            return;
          }
          const graphWidth = Math.max(56, this.#layout.laneCount * 16 + 24);
          this.#root.querySelector(".shell")?.style.setProperty("--wgg-graph-width", `${graphWidth}px`);
          const selectedIndex = this.#selectedIndex();
          const detailsHeight = selectedIndex >= 0 ? this.#detailsHeight : 0;
          const detailsTop = (selectedIndex + 1) * this.#rowHeight;
          const contentHeight = this.#page.commits.length * this.#rowHeight + detailsHeight;
          spacer.style.height = `${contentHeight + (this.#page.hasMore ? 42 : 0)}px`;
          const visibleRows = Math.ceil(Math.max(scroller.clientHeight, 420) / this.#rowHeight);
          const rowAtOffset = (offset) => {
            if (selectedIndex < 0 || offset < detailsTop) return Math.floor(offset / this.#rowHeight);
            if (offset < detailsTop + detailsHeight) return selectedIndex;
            return Math.floor((offset - detailsHeight) / this.#rowHeight);
          };
          const start = Math.max(0, rowAtOffset(scroller.scrollTop) - this.#overscan);
          const end = Math.min(
            this.#page.commits.length,
            Math.max(start + visibleRows, rowAtOffset(scroller.scrollTop + scroller.clientHeight) + 1) + this.#overscan
          );
          windowElement.style.transform = "";
          const reusedDetails = this.#detailsElement;
          for (const child of [...windowElement.children]) {
            if (child !== reusedDetails) child.remove();
          }
          const graphTop = this.#rowTop(start, selectedIndex);
          const graphHeight = Math.max(this.#rowHeight, this.#rowTop(end, selectedIndex) - graphTop);
          const svg = svgElement("svg", {
            class: "graph",
            width: `${graphWidth}`,
            height: `${graphHeight}`,
            "aria-hidden": "true"
          });
          svg.style.top = `${graphTop}px`;
          this.#drawGraph(svg, start, end, selectedIndex);
          windowElement.append(svg);
          const refsByTarget = /* @__PURE__ */ new Map();
          for (const ref of this.#page.refs) {
            if (!this.#showRemoteRefs && ref.kind === "remote") continue;
            const existing = refsByTarget.get(ref.target) ?? [];
            existing.push(ref);
            refsByTarget.set(ref.target, existing);
          }
          const nodesByOid = new Map(this.#layout.nodes.map((node) => [node.oid, node]));
          const matchSet = new Set(this.#matches);
          const currentMatch = this.#matchCursor >= 0 ? this.#matches[this.#matchCursor] : -1;
          const showAvatars = this.avatars;
          const dateFormat = this.dateFormat;
          for (let index = start; index < end; index += 1) {
            const commit = this.#page.commits[index];
            const row = document.createElement("div");
            row.className = "row";
            row.classList.toggle("merge", commit.parents.length > 1);
            row.classList.toggle("working-tree", commit.kind === "working-tree");
            row.classList.toggle("match", matchSet.has(index));
            row.classList.toggle("match-current", index === currentMatch);
            row.classList.toggle("context-active", commit.oid === this.#contextOid);
            if (commit.oid === this.#selectedOid) row.classList.add("selected");
            if (commit.oid === this.#compareOid) row.classList.add("compare");
            row.dataset.oid = commit.oid;
            row.dataset.index = String(index);
            row.setAttribute("role", "row");
            row.tabIndex = commit.oid === this.#selectedOid || !this.#selectedOid && index === 0 ? 0 : -1;
            row.style.top = `${this.#rowTop(index, selectedIndex)}px`;
            row.innerHTML = `
        <div class="graph-cell" role="gridcell"></div>
        <div class="subject" role="gridcell"><div class="refs"></div><span class="message"></span></div>
        <div class="date" role="gridcell"></div>
        <div class="author" role="gridcell"></div>
        <div class="oid" role="gridcell"></div>`;
            row.querySelector(".message").textContent = commit.message.split("\n", 1)[0] ?? "";
            const authorCell = row.querySelector(".author");
            if (showAvatars && commit.kind !== "working-tree") authorCell.append(this.#avatarElement(commit));
            const authorName = document.createElement("span");
            authorName.className = "author-name";
            authorName.textContent = commit.author?.name ?? "\u2014";
            authorCell.append(authorName);
            row.querySelector(".date").textContent = formatDate(
              this.#commitDate(commit),
              dateFormat
            );
            row.querySelector(".oid").textContent = shortOid(commit.oid);
            const refs = row.querySelector(".refs");
            const seenLabels = /* @__PURE__ */ new Set();
            for (const ref of refsByTarget.get(commit.oid) ?? []) {
              const label = shortRefName(ref.name);
              const dedupeKey = ref.kind === "current" || ref.kind === "head" ? `branch:${label}` : `${ref.kind}:${label}`;
              if (seenLabels.has(dedupeKey)) continue;
              seenLabels.add(dedupeKey);
              const badge = document.createElement("span");
              badge.className = `ref ${ref.kind}`;
              const prefix = ref.kind === "tag" ? "\u25C7" : ref.kind === "stash" ? "\u224B" : ref.kind === "remote" ? "\u2197" : "\u2442";
              badge.textContent = `${prefix} ${label}`;
              badge.title = label;
              const node = nodesByOid.get(commit.oid);
              if (node && LANE_COLOURED_REFS.has(ref.kind)) {
                badge.style.setProperty("--ref-color", PALETTE[node.colour % PALETTE.length]);
              }
              refs.append(badge);
              if (seenLabels.size >= 4) break;
            }
            row.addEventListener("click", (event) => {
              if (event.button !== 0 || IS_APPLE && event.ctrlKey) return;
              if ((event.metaKey || event.ctrlKey) && this.#selectedOid && this.#selectedOid !== commit.oid) {
                void this.compareCommits(this.#selectedOid, commit.oid);
              } else if (commit.oid === this.#selectedOid && !this.#compareOid) {
                this.#closeDetails();
              } else {
                this.selectCommit(commit.oid);
              }
            });
            row.addEventListener("mousedown", (event) => {
              if (event.button === 2) event.preventDefault();
            });
            row.addEventListener("contextmenu", (event) => {
              event.preventDefault();
              event.stopPropagation();
              this.#openCommitMenu(commit, event.clientX, event.clientY);
            });
            row.addEventListener("dblclick", () => {
              if (commit.url) window.open(commit.url, "_blank", "noopener,noreferrer");
              this.dispatchEvent(
                new CustomEvent("gitgraph-commit-open", {
                  bubbles: true,
                  composed: true,
                  detail: { commit }
                })
              );
            });
            windowElement.append(row);
          }
          if (selectedIndex >= 0) {
            const details = reusedDetails ?? document.createElement("aside");
            details.className = "inline-details";
            details.setAttribute("aria-label", this.#compareOid ? "Commit comparison" : "Commit details");
            details.style.top = `${detailsTop}px`;
            details.style.height = `${detailsHeight}px`;
            if (details.parentNode !== windowElement) windowElement.append(details);
            this.#detailsElement = details;
          } else {
            reusedDetails?.remove();
            this.#detailsElement = void 0;
            this.#detailsSignature = void 0;
          }
          if (this.#page.hasMore && end === this.#page.commits.length) {
            const button = document.createElement("button");
            button.className = "action load-more";
            button.type = "button";
            button.style.top = `${contentHeight}px`;
            button.textContent = this.#loadingMore ? "Loading\u2026" : "Load more commits";
            button.disabled = this.#loadingMore;
            button.addEventListener("click", () => {
              const event = new CustomEvent("gitgraph-load-more", {
                bubbles: true,
                composed: true,
                cancelable: true,
                detail: { cursor: this.#page.cursor }
              });
              if (this.dispatchEvent(event) && this.#provider) void this.#load(true);
            });
            windowElement.append(button);
          }
          this.#renderDetailsPanel();
        }
        #drawGraph(svg, start, end, selectedIndex) {
          const x = (lane) => 16 + lane * 16;
          const y = (row) => this.#rowTop(row, selectedIndex) - this.#rowTop(start, selectedIndex) + this.#rowHeight * 0.5;
          for (const segment of this.#layout.segments) {
            if (segment.to.row < start || segment.from.row >= end) continue;
            const fromRow = Math.max(start, segment.from.row);
            const toRow = Math.min(end, segment.to.row);
            const x1 = x(segment.from.lane);
            const x2 = x(segment.to.lane);
            const y1 = y(fromRow);
            const y2 = y(toRow);
            const anchorTop = segment.anchor === "from";
            svg.append(
              svgElement("path", {
                d: this.#segmentPath(x1, x2, y1, y2, anchorTop),
                stroke: PALETTE[segment.colour % PALETTE.length],
                ...segment.dangling ? { "stroke-dasharray": "3 4" } : {}
              })
            );
          }
          for (const node of this.#layout.nodes) {
            if (node.row < start || node.row >= end) continue;
            const colour = node.kind === "working-tree" ? "var(--wgg-faint)" : PALETTE[node.colour % PALETTE.length];
            svg.append(
              svgElement("circle", {
                cx: `${x(node.lane)}`,
                cy: `${y(node.row)}`,
                r: node.kind === "working-tree" ? "4.5" : node.kind === "stash" ? "4" : "3.5",
                fill: node.oid === this.#page.head || node.kind === "working-tree" ? "var(--wgg-bg)" : colour,
                stroke: colour
              })
            );
          }
        }
        #segmentPath(x1, x2, y1, y2, anchorTop) {
          if (x1 === x2) return `M ${x1} ${y1} L ${x2} ${y2}`;
          const lead = this.#rowHeight * 0.55;
          if (y2 - y1 <= this.#rowHeight) {
            return `M ${x1} ${y1} C ${x1} ${y1 + lead}, ${x2} ${y2 - lead}, ${x2} ${y2}`;
          }
          if (anchorTop) {
            const yBend2 = y1 + this.#rowHeight;
            return `M ${x1} ${y1} C ${x1} ${y1 + lead}, ${x2} ${yBend2 - lead}, ${x2} ${yBend2} L ${x2} ${y2}`;
          }
          const yBend = y2 - this.#rowHeight;
          return `M ${x1} ${y1} L ${x1} ${yBend} C ${x1} ${yBend + lead}, ${x2} ${y2 - lead}, ${x2} ${y2}`;
        }
        #onKeyDown(event) {
          const rows = [...this.#root.querySelectorAll(".row")];
          const active = this.#root.activeElement;
          const current = rows.indexOf(active);
          let target = current;
          if (event.key === "ArrowDown") target = Math.min(rows.length - 1, Math.max(0, current + 1));
          else if (event.key === "ArrowUp") target = Math.max(0, current - 1);
          else if (event.key === "Home") target = 0;
          else if (event.key === "End") target = rows.length - 1;
          else if (event.key === "Enter" && active?.dataset.oid) {
            this.selectCommit(active.dataset.oid);
            return;
          } else if (event.key === "Escape") {
            this.#closeDetails();
            return;
          } else return;
          event.preventDefault();
          rows[target]?.focus();
        }
        async #loadDetails(commit) {
          if (!this.#provider?.getCommitDetails) {
            this.#detailsPending = false;
            this.#details = { commit, refs: this.#page.refs.filter((ref) => ref.target === commit.oid), changes: [] };
            this.#renderDetailsPanel();
            return;
          }
          const reread = this.#details?.commit.oid === commit.oid;
          if (!reread) {
            this.#details = void 0;
            this.#detailsPending = true;
            this.#renderDetailsPanel();
          }
          try {
            const details = await this.#provider.getCommitDetails(
              this.#page.repositoryId,
              revisionFor(commit)
            );
            if (this.#selectedOid !== commit.oid) return;
            this.#details = details;
          } catch (error) {
            if (this.#selectedOid !== commit.oid) return;
            this.#emitError(error);
          }
          this.#detailsPending = false;
          this.#renderDetailsPanel();
        }
        #renderDetailsPanel(waiting = false) {
          const pending = waiting || this.#detailsPending;
          const details = this.#root.querySelector(".inline-details");
          if (!details || !this.#selectedOid) return;
          const signature = JSON.stringify([
            this.#selectedOid,
            this.#compareOid,
            pending,
            this.#details?.commit.oid,
            this.#details?.changes.length,
            Boolean(this.#comparison),
            this.#fileDiff?.path,
            this.#fileDiff?.patch?.length,
            [...this.#collapsedDirs].sort()
          ]);
          if (signature === this.#detailsSignature && details.firstChild) return;
          this.#detailsSignature = signature;
          details.innerHTML = `
      <button class="details-close" type="button" aria-label="Close details">\xD7</button>
      <div class="details-summary"></div>
      <div class="details-files"></div>`;
          details.querySelector(".details-close")?.addEventListener("click", () => this.#closeDetails());
          const summary = details.querySelector(".details-summary");
          const files = details.querySelector(".details-files");
          if (this.#compareOid) {
            if (pending || !this.#comparison) {
              summary.innerHTML = `<p class="pending-label">Calculating tree difference\u2026</p>`;
              summary.append(pendingBars(PENDING_FILE_WIDTHS.slice(0, 3)));
              files.append(pendingBars(PENDING_FILE_WIDTHS));
              return;
            }
            this.#renderComparison(summary, files, this.#comparison);
            return;
          }
          const commit = this.#details?.commit ?? this.#page.commits.find((item) => item.oid === this.#selectedOid);
          if (!commit) {
            summary.innerHTML = `<p class="pending-label">Reading commit object\u2026</p>`;
            summary.append(pendingBars(PENDING_FILE_WIDTHS.slice(0, 3)));
            return;
          }
          const meta = document.createElement("dl");
          meta.className = "meta";
          const fields = [
            ["Commit", commit.kind === "working-tree" ? "uncommitted changes" : commit.oid, true],
            ["Parents", commit.parents.map(shortOid).join(", ") || "root commit", true],
            ["Author", `${commit.author?.name ?? "Unknown"}${commit.author?.email ? ` <${commit.author.email}>` : ""}`],
            // The panel always shows the absolute timestamp, whatever the column shows.
            ["Date", formatDate(this.#commitDate(commit))]
          ];
          for (const [label, value, mono] of fields) {
            const dt = document.createElement("dt");
            const dd = document.createElement("dd");
            dt.textContent = label;
            dd.textContent = value;
            if (mono) dd.className = "oid-value";
            meta.append(dt, dd);
          }
          summary.append(meta);
          const body = document.createElement("p");
          body.className = "commit-body";
          body.textContent = (this.#details?.body ?? commit.message).trim();
          summary.append(body);
          const actions = document.createElement("div");
          actions.className = "actions";
          actions.innerHTML = `<button class="action primary copy" type="button">Copy SHA</button>`;
          actions.querySelector(".copy")?.addEventListener("click", () => void this.#copy(commit.oid));
          if (this.#provider?.compare) {
            const compareAction = document.createElement("button");
            compareAction.className = "action compare-action";
            compareAction.type = "button";
            compareAction.textContent = "Compare with\u2026";
            compareAction.addEventListener("click", () => {
              compareAction.textContent = IS_APPLE ? "Cmd-click another commit" : "Ctrl-click another commit";
              compareAction.disabled = true;
              this.#root.querySelector(".scroller")?.focus();
            });
            actions.append(compareAction);
          }
          if (commit.url) {
            const open = document.createElement("button");
            open.className = "action";
            open.textContent = "Open remote \u2197";
            open.addEventListener("click", () => window.open(commit.url, "_blank", "noopener,noreferrer"));
            actions.append(open);
          }
          summary.append(actions);
          if (pending) {
            files.append(pendingBars(PENDING_FILE_WIDTHS));
            return;
          }
          const changes = this.#details?.changes ?? [];
          const parentOid = commit.parents[0];
          const diff = parentOid && this.#provider?.getFileDiff ? { base: { kind: "commit", oid: parentOid }, head: revisionFor(commit) } : void 0;
          if (this.#fileDiff) {
            summary.append(this.#patchElement());
          } else if (diff && changes.length > 0) {
            const hint = document.createElement("p");
            hint.className = "no-changes";
            hint.textContent = "Select a file to view its diff.";
            summary.append(hint);
          }
          this.#renderFileTree(files, changes, diff);
        }
        #patchElement() {
          const patch = document.createElement("pre");
          patch.className = "patch";
          patch.textContent = this.#fileDiff?.patch ?? this.#fileDiff?.unavailableReason ?? (this.#fileDiff?.binary ? "Binary file \u2014 patch unavailable." : "No textual patch.");
          return patch;
        }
        #renderComparison(summary, files, comparison) {
          const heading = document.createElement("h2");
          heading.className = "details-heading";
          heading.textContent = `${this.#revisionLabel(comparison.base)} \u2192 ${this.#revisionLabel(comparison.head)}`;
          summary.append(heading);
          const stats = document.createElement("p");
          stats.className = "stats";
          stats.textContent = `${comparison.changes.length} files \xB7 +${comparison.additions} \u2212${comparison.deletions}${comparison.truncated ? " \xB7 truncated" : ""}`;
          summary.append(stats);
          if (this.#fileDiff) {
            summary.append(this.#patchElement());
          } else if (comparison.changes.length > 0) {
            const hint = document.createElement("p");
            hint.className = "no-changes";
            hint.textContent = "Select a file to view its diff.";
            summary.append(hint);
          }
          this.#renderFileTree(files, comparison.changes, {
            base: comparison.base,
            head: comparison.head,
            comparison
          });
        }
        #renderFileTree(container, changes, diff) {
          if (changes.length === 0) {
            container.innerHTML = `<p class="no-changes">No file changes.</p>`;
            return;
          }
          const list = document.createElement("ul");
          list.className = "tree";
          this.#renderTreeLevel(list, buildFileTree(changes), "", diff);
          container.append(list);
        }
        #renderTreeLevel(list, node, prefix, diff) {
          for (let [name, dir] of [...node.dirs.entries()].sort((a, b) => a[0].localeCompare(b[0]))) {
            while (dir.files.length === 0 && dir.dirs.size === 1) {
              const [entry] = dir.dirs;
              name = `${name}/${entry[0]}`;
              dir = entry[1];
            }
            const path = prefix ? `${prefix}/${name}` : name;
            const collapsed = this.#collapsedDirs.has(path);
            const item = document.createElement("li");
            const toggle = document.createElement("button");
            toggle.className = "tree-dir";
            toggle.type = "button";
            toggle.setAttribute("aria-expanded", String(!collapsed));
            const twistie = document.createElement("span");
            twistie.className = "twistie";
            twistie.textContent = collapsed ? "\u25B8" : "\u25BE";
            const label = document.createElement("span");
            label.className = "dir-name";
            label.textContent = name;
            toggle.append(twistie, label);
            toggle.addEventListener("click", () => {
              if (collapsed) this.#collapsedDirs.delete(path);
              else this.#collapsedDirs.add(path);
              this.#renderDetailsPanel();
            });
            item.append(toggle);
            if (!collapsed) {
              const children = document.createElement("ul");
              this.#renderTreeLevel(children, dir, path, diff);
              item.append(children);
            }
            list.append(item);
          }
          for (const change of [...node.files].sort((a, b) => a.path.localeCompare(b.path))) {
            const item = document.createElement("li");
            const button = document.createElement("button");
            button.className = "tree-file";
            button.type = "button";
            if (this.#fileDiff?.path === change.path) button.classList.add("active");
            button.title = change.previousPath ? `${change.previousPath} \u2192 ${change.path}` : change.path;
            const code = document.createElement("span");
            code.className = `change-code ${change.kind}`;
            code.textContent = change.kind.slice(0, 1).toUpperCase();
            const path = document.createElement("span");
            path.className = "change-path";
            path.textContent = change.previousPath ? `${baseName(change.previousPath)} \u2192 ${baseName(change.path)}` : baseName(change.path);
            const stats = document.createElement("span");
            stats.className = "stats";
            stats.textContent = change.binary ? "binary" : `${change.additions === void 0 ? "" : `+${change.additions}`} ${change.deletions === void 0 ? "" : `\u2212${change.deletions}`}`.trim();
            button.append(code, path, stats);
            button.addEventListener("click", async () => {
              const proceed = this.dispatchEvent(
                new CustomEvent("gitgraph-file-open", {
                  bubbles: true,
                  composed: true,
                  cancelable: true,
                  detail: { change, base: diff?.base, head: diff?.head, comparison: diff?.comparison }
                })
              );
              if (!proceed || !diff || !this.#provider?.getFileDiff) return;
              if (change.unavailableReason) {
                this.#fileDiff = {
                  base: diff.base,
                  head: diff.head,
                  path: change.path,
                  unavailableReason: change.unavailableReason
                };
                this.#renderDetailsPanel();
                return;
              }
              try {
                this.#fileDiff = await this.#provider.getFileDiff(
                  this.#page.repositoryId,
                  diff.base,
                  diff.head,
                  change.path,
                  3
                );
              } catch (error) {
                this.#emitError(error);
              }
              this.#renderDetailsPanel();
            });
            item.append(button);
            list.append(item);
          }
        }
        #commitDate(commit) {
          return this.dateType === "authored" ? commit.authoredAt ?? commit.committedAt : commit.committedAt ?? commit.authoredAt;
        }
        #revisionLabel(revision) {
          if (revision.kind === "working-tree") return "working tree";
          return shortOid(revision.oid);
        }
        #closeDetails() {
          this.#selectedOid = void 0;
          this.#compareOid = void 0;
          this.#details = void 0;
          this.#detailsPending = false;
          this.#comparison = void 0;
          this.#fileDiff = void 0;
          this.#collapsedDirs.clear();
          this.#detailsSignature = void 0;
          this.#renderWindow();
          this.#renderDetailsPanel();
        }
        #selectedIndex() {
          if (!this.#selectedOid) return -1;
          return this.#page.commits.findIndex((commit) => commit.oid === this.#selectedOid);
        }
        #rowTop(index, selectedIndex) {
          return index * this.#rowHeight + (selectedIndex >= 0 && index > selectedIndex ? this.#detailsHeight : 0);
        }
        #revealDetails(oid) {
          if (this.#selectedOid !== oid) return;
          const selectedIndex = this.#selectedIndex();
          const scroller = this.#root.querySelector(".scroller");
          if (selectedIndex < 0 || !scroller) return;
          const rowTop = selectedIndex * this.#rowHeight;
          const blockBottom = rowTop + this.#rowHeight + this.#detailsHeight;
          let target = scroller.scrollTop;
          if (blockBottom > target + scroller.clientHeight) target = blockBottom - scroller.clientHeight;
          if (rowTop < target) target = rowTop;
          if (target !== scroller.scrollTop) scroller.scrollTo({ top: target, behavior: "smooth" });
        }
        async #load(append, preserveView = false) {
          if (!this.#provider || append && !this.#page.hasMore) return;
          this.#abort?.abort();
          const abort = new AbortController();
          this.#abort = abort;
          const view = preserveView ? {
            scrollTop: this.#root.querySelector(".scroller")?.scrollTop ?? 0,
            selectedOid: this.#selectedOid,
            details: this.#details
          } : void 0;
          this.#loading = !append && !preserveView;
          this.#loadingMore = append;
          this.#error = void 0;
          this.setAttribute("aria-busy", "true");
          this.#renderWindow();
          try {
            const page = await this.#provider.getHistory({
              repositoryId: this.#page.repositoryId,
              refs: this.#selectedRefs.length ? this.#selectedRefs : void 0,
              cursor: append ? this.#page.cursor : void 0,
              limit: 200,
              includeWorkingTree: true,
              signal: abort.signal
            });
            if (append) this.appendPage(page);
            else {
              this.setData(page);
              if (view) this.#restoreView(view);
            }
          } catch (error) {
            if (abort.signal.aborted) return;
            this.#emitError(error);
          } finally {
            this.#loading = false;
            this.#loadingMore = false;
            if (this.#abort === abort) this.setAttribute("aria-busy", "false");
            this.#renderWindow();
          }
        }
        /**
         * Reapplies the pre-reload view. The selection is restored without the reveal
         * animation and the scroll offset is written back synchronously, so a refresh
         * is invisible when the history has not changed.
         */
        #restoreView(view) {
          const commit = view.selectedOid ? this.#page.commits.find((item) => item.oid === view.selectedOid) : void 0;
          if (commit) {
            this.#selectedOid = commit.oid;
            if (view.details?.commit.oid === commit.oid) this.#details = view.details;
            void this.#loadDetails(commit);
          }
          this.#renderWindow();
          const scroller = this.#root.querySelector(".scroller");
          if (scroller && view.scrollTop !== scroller.scrollTop) {
            scroller.scrollTop = view.scrollTop;
            this.#renderWindow();
          }
        }
        #emitError(error) {
          this.#error = error instanceof Error ? error.message : String(error);
          this.dispatchEvent(
            new CustomEvent("gitgraph-error", {
              bubbles: true,
              composed: true,
              detail: { error }
            })
          );
          this.#renderWindow();
        }
      };
      defineWebGitGraph();
    }
  });

  // entry.js
  var require_entry = __commonJS({
    "entry.js"() {
      init_register();
    }
  });
  return require_entry();
})();
