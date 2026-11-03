// QA-only match guard for public event artwork; no URLs are constructed from caller hosts.
export const normalizeName = value => String(value ?? "").normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "").toLowerCase()
    .replace(/[^a-z0-9]+/g, " ").trim().replace(/\s+/g, " ");

function nearEventDate(fixture, item) {
    const expected = Number(fixture.startUtcMs);
    if (!Number.isFinite(expected)) return false;
    const stamp = Date.parse(String(item.strTimestamp || ""));
    if (Number.isFinite(stamp) && Math.abs(expected - stamp) > 36 * 3600_000) return false;
    const date = Date.parse(String(item.dateEvent || "") + "T00:00:00Z");
    if (!Number.isFinite(date)) return false;
    const expectedMidnight = Date.parse(new Date(expected).toISOString().slice(0, 10) + "T00:00:00Z");
    return Math.abs(date - expectedMidnight) <= 24 * 3600_000;
}
function sportMatches(sport, strSport) {
    const label = normalizeName(strSport);
    if (!label) return false;
    if (sport === "rugby-league") return label === "rugby league";
    if (sport === "rugby") return label === "rugby" || label === "rugby union";
    if (sport === "football") return label === "american football";
    if (sport === "soccer") return label === "soccer" || label === "football";
    if (sport === "basketball") return label === "basketball";
    if (sport === "cricket") return label === "cricket";
    if (sport === "racing") return ["motorsport", "motor racing", "formula 1", "formula 1 racing"].includes(label);
    if (sport === "mma") return ["boxing", "mixed martial arts", "mma", "fighting"].includes(label);
    return false;
}
function nameMatches(name, shortName, candidate) {
    const variants = [name, shortName]
        .map(normalizeName)
        .map(v => v.replace(/^(?:as|afc|fc|cf) /, ""))
        .filter(v => v.length >= 4);
    const value = " " + normalizeName(candidate) + " ";
    return variants.some(v => value.includes(" " + v + " "));
}
export function verifiedEventArtwork(fixture, item) {
    if (!fixture || !item || !nearEventDate(fixture, item) ||
        !sportMatches(fixture.sport, item.strSport)) return null;
    const participants = Array.isArray(fixture.teams) ? fixture.teams.slice(0, 2) : [];
    if (participants.length === 2) {
        const info = [item.strHomeTeam, item.strAwayTeam, item.strEvent]
            .filter(Boolean).join(" ");
        if (!participants.every(team => nameMatches(team.name, team.shortName, info))) return null;
        // Never accept a record that has one correct team but the wrong opponent.
        const actualSides = [item.strHomeTeam, item.strAwayTeam].filter(Boolean);
        if (actualSides.length === 2 && !participants.every(team =>
            actualSides.some(name => nameMatches(team.name, team.shortName, name)))) return null;
    } else if (fixture.sport === "racing") {
        const actual = normalizeName(item.strEvent);
        const expected = normalizeName(fixture.title);
        if (actual.length < 12 || !expected.includes(actual)) return null;
    } else return null;

    // Use only 1280x720 event thumbs/fan art. Reject the skinny 1000x185 banners.
    const urls = [item.strFanart, item.strThumb, item.strFanart1, item.strFanart2];
    for (const raw of urls) {
        if (typeof raw !== "string") continue;
        try {
            const u = new URL(raw);
            if (u.protocol !== "https:") continue;
            if (!["www.thesportsdb.com", "thesportsdb.com", "r2.thesportsdb.com"].includes(u.hostname)) continue;
            if (!u.pathname.startsWith("/images/media/event/")) continue;
            if (!/\/(?:thumb|fanart)\//.test(u.pathname)) continue;
            return { url: u.href, source: "thesportsdb", confidence: "exact" };
        } catch { /* invalid source image */ }
    }
    return null;
}