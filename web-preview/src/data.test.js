import test from "node:test";
import assert from "node:assert/strict";
import {
  monthlyTotal,
  monthlyValue,
  occurrences,
  initialSubscriptions,
  daysUntil,
  nextRenewal,
  dueLabel,
  plural,
  csvCell,
  isValidSubscription,
  isValidBackup,
  isValidDate,
  currentSubscriptions,
  newId,
  readSaved,
  STORAGE_KEY,
  RECOVERY_KEY,
} from "./data.js";

test("spending totals count active plans and normalize billing cycles", () => {
  assert.equal(monthlyTotal(initialSubscriptions), 2970);
  assert.equal(monthlyValue({ price: 1200, cycle: "Yearly" }), 100);
  assert.equal(monthlyValue({ price: 12, cycle: "Weekly" }), 52);
  assert.equal(
    monthlyTotal([
      { price: 100, cycle: "Monthly", status: "Archived" },
      { price: 200, cycle: "Monthly", status: "Trial" },
    ]),
    0,
  );
});
test("monthly recurrence clamps month end and recovers the original day", () => {
  const s = [
    {
      id: "a",
      date: "2026-01-31",
      price: 100,
      cycle: "Monthly",
      status: "Active",
    },
  ];
  assert.equal(occurrences(s, 2026, 1)[0].occurrence, "2026-02-28");
  assert.equal(occurrences(s, 2026, 2)[0].occurrence, "2026-03-31");
  assert.equal(occurrences(s, 2025, 11).length, 0);
});
test("yearly recurrences respect the billing month and leap years", () => {
  const s = [
    {
      id: "a",
      date: "2024-02-29",
      price: 100,
      cycle: "Yearly",
      status: "Active",
    },
  ];
  assert.equal(occurrences(s, 2026, 1)[0].occurrence, "2026-02-28");
  assert.equal(occurrences(s, 2026, 2).length, 0);
  assert.equal(occurrences(s, 2028, 1)[0].occurrence, "2028-02-29");
});
test("weekly recurrence includes each charge inside the displayed month", () => {
  const s = [
    {
      id: "a",
      date: "2026-08-31",
      price: 100,
      cycle: "Weekly",
      status: "Active",
    },
  ];
  assert.deepEqual(
    occurrences(s, 2026, 8).map((s) => s.occurrence),
    ["2026-09-07", "2026-09-14", "2026-09-21", "2026-09-28"],
  );
});
test("calendar totals and the upcoming countdown agree with the sample collection", () => {
  assert.equal(daysUntil("2026-09-15"), 3);
  assert.equal(
    occurrences(initialSubscriptions, 2026, 8).reduce((n, s) => n + s.price, 0),
    2970,
  );
  assert.equal(
    occurrences(
      initialSubscriptions.map((s) => ({ ...s, status: "Archived" })),
      2026,
      8,
    ).length,
    0,
  );
});
test("renewals roll forward from the demo date and keep month-end anchors", () => {
  assert.equal(nextRenewal({ date: "2026-08-15", cycle: "Monthly" }), "2026-09-15");
  assert.equal(nextRenewal({ date: "2026-10-01", cycle: "Monthly" }), "2026-10-01");
  assert.equal(
    nextRenewal({ date: "2026-01-31", cycle: "Monthly" }, new Date(2026, 1, 1)),
    "2026-02-28",
  );
  assert.equal(
    nextRenewal({ date: "2026-01-31", cycle: "Monthly" }, new Date(2026, 2, 1)),
    "2026-03-31",
  );
  assert.equal(
    nextRenewal({ date: "2024-02-29", cycle: "Yearly" }, new Date(2026, 0, 1)),
    "2026-02-28",
  );
  assert.equal(nextRenewal({ date: "2026-08-31", cycle: "Weekly" }), "2026-09-14");
});
test("exports neutralize spreadsheet formulas and quote text", () => {
  assert.equal(csvCell("=HYPERLINK(1)"), `"'=HYPERLINK(1)"`);
  assert.equal(csvCell('Say "hi"'), `"Say ""hi"""`);
  assert.equal(csvCell(649), `"649"`);
});
test("saved data and restored backups share one validation", () => {
  const sub = initialSubscriptions[0];
  assert.equal(isValidSubscription(sub), true);
  assert.equal(isValidSubscription({ ...sub, price: "649" }), false);
  assert.equal(isValidSubscription({ ...sub, cycle: "Daily" }), false);
  assert.equal(isValidSubscription({ ...sub, id: "" }), false);
  assert.equal(isValidSubscription({ ...sub, date: "2026-99-99" }), false);
  assert.equal(isValidDate("2026-02-29"), false);
  assert.equal(isValidDate("2028-02-29"), true);
});
test("backup validation keeps duplicate records from overwriting one another", () => {
  const sub = initialSubscriptions[0];
  const prefs = { budget: 9000, reminder: 7, notifications: false, theme: "dark" };
  assert.equal(isValidBackup({ subs: [sub], prefs }), true);
  assert.equal(isValidBackup({ subs: [sub, { ...sub, name: "Another" }], prefs }), false);
  assert.equal(isValidBackup({ subs: [sub], prefs: { ...prefs, budget: "9000" } }), false);
});
test("legacy duplicate IDs are repaired without losing either record", () => {
  const entries = new Map([[STORAGE_KEY, JSON.stringify({ subs: [
    initialSubscriptions[0],
    { ...initialSubscriptions[0], name: "A distinct subscription" },
  ] })]]);
  const previous = globalThis.localStorage;
  globalThis.localStorage = {
    getItem: (key) => entries.get(key) ?? null,
    setItem: (key, value) => entries.set(key, value),
  };
  try {
    const restored = readSaved();
    assert.equal(restored.subs.length, 2);
    assert.notEqual(restored.subs[0].id, restored.subs[1].id);
    assert.equal(restored.subs[1].name, "A distinct subscription");
    assert.equal(entries.get(RECOVERY_KEY), entries.get(STORAGE_KEY));
    entries.set(STORAGE_KEY, "newer invalid data");
    assert.equal(readSaved(), null);
    assert.notEqual(entries.get(RECOVERY_KEY), entries.get(STORAGE_KEY));
  } finally {
    if (previous === undefined) delete globalThis.localStorage;
    else globalThis.localStorage = previous;
  }
});
test("expired trials occur once, then enter active spending on restore", () => {
  const trial = { ...initialSubscriptions[0], id: "trial", status: "Trial", date: "2026-09-01" };
  const today = new Date(2026, 8, 12);
  assert.equal(nextRenewal(trial, today), "2026-09-01");
  assert.equal(occurrences([trial], 2026, 9).length, 0);
  const [active] = currentSubscriptions([trial], today);
  assert.equal(active.status, "Active");
  assert.equal(monthlyTotal([active]), active.price);
});
test("ids and labels work without a secure context", () => {
  assert.match(newId(), /\S+/);
  assert.equal(plural(1, "day"), "1 day");
  assert.equal(plural(3, "day"), "3 days");
  assert.equal(dueLabel(1), "tomorrow");
});
