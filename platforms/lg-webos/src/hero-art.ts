/** Packaged artwork covers every primary discovery sport, independent of the network. */
export const HERO_ART: Record<string, string> = {
  basketball: "nba",
  football: "nfl",
  "american-football": "nfl",
  "rugby-league": "league",
  rugby: "rugby",
  "rugby-union": "rugby",
  soccer: "soccer",
  racing: "f1",
  motorsport: "f1",
  f1: "f1",
  cricket: "cricket",
  mma: "combat",
  boxing: "combat",
  wrestling: "combat",
  hockey: "hockey",
  "ice-hockey": "hockey",
  baseball: "baseball",
  golf: "golf",
  tennis: "tennis",
};
export function heroAsset(sport: string): string | undefined {
  const key = HERO_ART[sport.toLowerCase().trim()];
  return key ? `assets/lounge_hero_${key}.webp` : undefined;
}
