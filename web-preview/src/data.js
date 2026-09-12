export const TODAY = new Date(2026, 8, 12);
export const STORAGE_KEY = "budgie-studio-v2";
export const categories = [
  "Entertainment",
  "Productivity",
  "Music",
  "Storage",
  "Other",
];
export const categoryColors = {
  Entertainment: "#df8667",
  Productivity: "#929adb",
  Music: "#a8c994",
  Storage: "#e3be66",
  Other: "#a7afb5",
};
export const initialSubscriptions = [
  {
    id: "netflix",
    name: "Netflix",
    plan: "Standard",
    price: 649,
    cycle: "Monthly",
    date: "2026-09-15",
    category: "Entertainment",
    brand: "netflix",
    status: "Active",
    reminder: 3,
    notes: "Movie nights, sorted.",
    started: "2026-06-15",
  },
  {
    id: "spotify",
    name: "Spotify",
    plan: "Premium Individual",
    price: 119,
    cycle: "Monthly",
    date: "2026-09-18",
    category: "Music",
    brand: "spotify",
    status: "Active",
    reminder: 3,
    notes: "",
    started: "2026-05-18",
  },
  {
    id: "youtube",
    name: "YouTube",
    plan: "Premium",
    price: 149,
    cycle: "Monthly",
    date: "2026-09-20",
    category: "Entertainment",
    brand: "youtube",
    status: "Active",
    reminder: 3,
    notes: "",
    started: "2026-07-20",
  },
  {
    id: "notion",
    name: "Notion",
    plan: "Plus",
    price: 800,
    cycle: "Monthly",
    date: "2026-09-22",
    category: "Productivity",
    brand: "notion",
    status: "Active",
    reminder: 3,
    notes: "A home for all my ideas.",
    started: "2026-05-22",
  },
  {
    id: "figma",
    name: "Figma",
    plan: "Professional",
    price: 749,
    cycle: "Monthly",
    date: "2026-09-24",
    category: "Productivity",
    brand: "figma",
    status: "Active",
    reminder: 3,
    notes: "",
    started: "2026-08-24",
  },
  {
    id: "google",
    name: "Google One",
    plan: "100 GB",
    price: 130,
    cycle: "Monthly",
    date: "2026-09-25",
    category: "Storage",
    brand: "google",
    status: "Active",
    reminder: 1,
    notes: "",
    started: "2026-08-25",
  },
  {
    id: "prime",
    name: "Prime Video",
    plan: "Monthly",
    price: 299,
    cycle: "Monthly",
    date: "2026-09-28",
    category: "Entertainment",
    brand: "prime",
    status: "Active",
    reminder: 3,
    notes: "",
    started: "2026-07-28",
  },
  {
    id: "icloud",
    name: "iCloud+",
    plan: "50 GB",
    price: 75,
    cycle: "Monthly",
    date: "2026-09-29",
    category: "Storage",
    brand: "icloud",
    status: "Active",
    reminder: 1,
    notes: "",
    started: "2026-08-29",
  },
];
export function money(amount, currency = "INR") {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency,
    maximumFractionDigits: Number.isInteger(amount) ? 0 : 2,
  }).format(amount);
}
export function parseDate(value) {
  const [y, m, d] = value.split("-").map(Number);
  return new Date(y, m - 1, d);
}
export function dateString(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}
export function dateLabel(value, options = { month: "short", day: "numeric" }) {
  return parseDate(value).toLocaleDateString("en-IN", options);
}
export function daysUntil(value) {
  return Math.round((parseDate(value) - TODAY) / 86400000);
}
export function monthlyValue(sub) {
  return sub.cycle === "Yearly"
    ? sub.price / 12
    : sub.cycle === "Weekly"
      ? (sub.price * 52) / 12
      : sub.price;
}
export function monthlyTotal(subs) {
  return subs
    .filter((s) => s.status === "Active")
    .reduce((a, s) => a + monthlyValue(s), 0);
}
export function occurrences(subs, year, month) {
  const start = new Date(year, month, 1),
    end = new Date(year, month + 1, 0),
    result = [];
  for (const sub of subs.filter((s) => s.status !== "Archived")) {
    const anchor = parseDate(sub.date);
    if (sub.cycle === "Weekly") {
      const first = new Date(anchor);
      if (first < start)
        first.setDate(
          first.getDate() + Math.ceil((start - first) / 604800000) * 7,
        );
      for (let d = new Date(first); d <= end; d.setDate(d.getDate() + 7))
        result.push({ ...sub, occurrence: dateString(d) });
    } else {
      if (
        year < anchor.getFullYear() ||
        (year === anchor.getFullYear() && month < anchor.getMonth())
      )
        continue;
      if (sub.cycle === "Yearly" && month !== anchor.getMonth()) continue;
      const day = Math.min(anchor.getDate(), end.getDate());
      result.push({
        ...sub,
        occurrence: dateString(new Date(year, month, day)),
      });
    }
  }
  return result.sort((a, b) => a.occurrence.localeCompare(b.occurrence));
}
export function readSaved() {
  try {
    const raw = JSON.parse(localStorage.getItem(STORAGE_KEY));
    if (
      raw &&
      Array.isArray(raw.subs) &&
      raw.subs.every(
        (s) =>
          typeof s.id === "string" &&
          typeof s.name === "string" &&
          Number.isFinite(s.price) &&
          s.price > 0 &&
          /^\d{4}-\d{2}-\d{2}$/.test(s.date) &&
          ["Monthly", "Yearly", "Weekly"].includes(s.cycle) &&
          ["Active", "Trial", "Archived"].includes(s.status),
      )
    )
      return raw;
  } catch {
    /* Invalid or unavailable storage falls back to sample data. */
  }
  return null;
}
