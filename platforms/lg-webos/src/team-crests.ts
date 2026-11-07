import crests from "./team-crests.json" with { type: "json" };
import { SportEvent } from "./model";

/** Published RLWC nation marks, bundled so calendar-only fixtures need no image host. */
export function teamCrest(
  event: SportEvent,
  team: SportEvent["competitors"][number],
) {
  const league = /rugby-league|rlwc/i.test(event.sport + " " + event.feedId);
  if (league) {
    const nation = team.name.toLowerCase().trim().replace(/\s+/g, "-");
    const women = /women|ferns|jillaroos|fetu/i.test(
      event.title + " " + event.subtitle + " " + event.leagueName,
    );
    const key =
      women && nation + "-women" in crests ? nation + "-women" : nation;
    const local = crests[key as keyof typeof crests];
    if (local) return local.path;
  }
  return team.crestUrl || "";
}
