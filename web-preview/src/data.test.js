import test from "node:test";
import assert from "node:assert/strict";
import {
  monthlyTotal,
  monthlyValue,
  occurrences,
  initialSubscriptions,
  daysUntil,
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
