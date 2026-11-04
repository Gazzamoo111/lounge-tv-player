// QA-only match guard for public event artwork and badges; caller hosts are never trusted.
export const normalizeName = value => String(value ?? "").normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "").toLowerCase()
    .replace(/[^a-z0-9]+/g, " ").trim().replace(/\s+/g, " ");

const TRUSTED_HOSTS = new Set(["www.thesportsdb.com", "thesportsdb.com", "r2.thesportsdb.com"]);

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
function identityMatches(fixture, item) {
    if (!fixture || !item || !nearEventDate(fixture, item) ||
        !sportMatches(fixture.sport, item.strSport)) return false;
    const participants = Array.isArray(fixture.teams) ? fixture.teams.slice(0, 2) : [];
    if (participants.length === 2) {
        const info = [item.strHomeTeam, item.strAwayTeam, item.strEvent]
            .filter(Boolean).join(" ");
        if (!participants.every(team => nameMatches(team.name, team.shortName, info))) return false;
        const actualSides = [item.strHomeTeam, item.strAwayTeam].filter(Boolean);
        if (actualSides.length === 2 && !participants.every(team =>
            actualSides.some(name => nameMatches(team.name, team.shortName, name)))) return false;
        return true;
    }
    if (fixture.sport === "racing") {
        const actual = normalizeName(item.strEvent);
        const expected = normalizeName(fixture.title);
        return actual.length >= 12 && expected.includes(actual);
    }
    return false;
}
function trustedOriginalBadge(raw, kind) {
    if (typeof raw !== "string") return null;
    try {
        const u = new URL(raw);
        if (u.protocol !== "https:" || !TRUSTED_HOSTS.has(u.hostname)) return null;
        let path = u.pathname.replace(/\/(?:medium|small|tiny)$/i, "");
        if (!path.startsWith(`/images/media/${kind}/badge/`) || !path.toLowerCase().endsWith(".png")) return null;
        return `https://${u.hostname}${path}`;
    } catch {
        return null;
    }
}
function verifiedEventArtUrl(item) {
    const urls = [item.strFanart, item.strThumb, item.strFanart1, item.strFanart2];
    for (const raw of urls) {
        if (typeof raw !== "string") continue;
        try {
            const u = new URL(raw);
            if (u.protocol !== "https:" || !TRUSTED_HOSTS.has(u.hostname)) continue;
            if (!u.pathname.startsWith("/images/media/event/")) continue;
            if (!/\/(?:thumb|fanart)\//.test(u.pathname)) continue;
            return u.href;
        } catch { /* invalid source image */ }
    }
    return null;
}
function resolvedTeamLogos(fixture, item) {
    const participants = Array.isArray(fixture.teams) ? fixture.teams.slice(0, 2) : [];
    if (participants.length !== 2) return [];
    const actual = [
        { name: item.strHomeTeam, url: item.strHomeTeamBadge },
        { name: item.strAwayTeam, url: item.strAwayTeamBadge },
    ];
    const used = new Set();
    const logos = [];
    for (const team of participants) {
        const index = actual.findIndex((side, i) =>
            !used.has(i) && side.name && nameMatches(team.name, team.shortName, side.name));
        if (index < 0) continue;
        used.add(index);
        const url = trustedOriginalBadge(actual[index].url, "team");
        if (url) logos.push({ name: team.name, url });
    }
    return logos;
}
export function verifiedEventAssets(fixture, item) {
    if (!identityMatches(fixture, item)) return null;
    return {
        url: verifiedEventArtUrl(item),
        source: "thesportsdb",
        confidence: "exact",
        teamLogos: resolvedTeamLogos(fixture, item),
        competitionLogoUrl: trustedOriginalBadge(item.strLeagueBadge, "league"),
    };
}
export function verifiedEventArtwork(fixture, item) {
    const assets = verifiedEventAssets(fixture, item);
    if (!assets?.url) return null;
    return { url: assets.url, source: assets.source, confidence: assets.confidence };
}
