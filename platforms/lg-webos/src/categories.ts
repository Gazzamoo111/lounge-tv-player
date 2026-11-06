import type { Category } from "./model";
export interface CategoryGroup {
  name: string;
  items: Category[];
}
/** Presentation only: never omit IDs, sort source rows or change membership. */
export function categoryGroups(rows: Category[]): CategoryGroup[] {
  const groups = new Map<string, Category[]>();
  for (const row of rows) {
    const name = row.section || row.name || "Other";
    const list = groups.get(name) ?? [];
    list.push(row);
    groups.set(name, list);
  }
  return [...groups].map(([name, items]) => ({ name, items }));
}
