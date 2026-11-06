import glyphs from "./glyphs.json";
export function node<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  className = "",
  text?: string,
): HTMLElementTagNameMap[K] {
  const el = document.createElement(tag);
  el.className = className;
  if (text !== undefined) el.textContent = text;
  return el;
}
export function icon(name: string) {
  const el = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  el.setAttribute("viewBox", "0 0 24 24");
  el.setAttribute("fill", "none");
  el.setAttribute("stroke", "currentColor");
  el.setAttribute("stroke-width", "1.8");
  el.setAttribute("stroke-linecap", "round");
  el.setAttribute("stroke-linejoin", "round");
  el.setAttribute("aria-hidden", "true");
  const path = document.createElementNS(el.namespaceURI, "path");
  path.setAttribute(
    "d",
    (glyphs as Record<string, string>)[name] ?? glyphs.INFO,
  );
  el.append(path);
  return el;
}
export function button(
  id: string,
  label: string,
  action: () => void,
  className = "",
  glyph?: string,
) {
  const el = node("button", className);
  el.id = id;
  el.dataset.focus = "";
  el.tabIndex = -1;
  if (glyph) el.append(icon(glyph));
  el.append(node("span", "", label));
  el.addEventListener("click", action);
  return el;
}
export function image(url: string, className = "", alt = "") {
  const el = node("img", className);
  el.alt = alt;
  el.loading = url.startsWith("assets/") ? "eager" : "lazy";
  el.decoding = "async";
  el.referrerPolicy = "no-referrer";
  if (/^https?:\/\//i.test(url) || url.startsWith("assets/")) el.src = url;
  else el.hidden = true;
  el.addEventListener("error", () => {
    el.hidden = true;
  });
  return el;
}
export function input(id: string, placeholder: string, password = false) {
  const el = node("input", "search-field");
  el.id = id;
  el.placeholder = placeholder;
  el.setAttribute("aria-label", placeholder);
  el.type = password ? "password" : "text";
  el.dataset.focus = "";
  el.tabIndex = -1;
  el.autocomplete = "off";
  return el;
}
export function tag(text: string) {
  return node("span", "tag", text);
}
export function message(title: string, description: string) {
  const e = node("div", "empty");
  e.append(node("h2", "", title), node("p", "muted", description));
  return e;
}
