import React, { useState, useEffect, useRef } from "react";
import { createRoot } from "react-dom/client";
import {
  Bird,
  SquaresFour,
  Stack,
  CalendarBlank,
  ChartBar,
  Bell,
  GearSix,
  Plus,
  ArrowUpRight,
  ArrowRight,
  ArrowLeft,
  CaretLeft,
  CaretRight,
  CaretDown,
  MagnifyingGlass,
  X,
  Check,
  CheckCircle,
  ArrowDown,
  DownloadSimple,
  UploadSimple,
  ShieldCheck,
  Moon,
  Sun,
  DotsThree,
  SlidersHorizontal,
  Wallet,
  CircleNotch,
  Archive,
  PencilSimple,
  ArrowCounterClockwise,
  Cloud,
  CreditCard,
  Sparkle,
  List,
  GridFour,
  SignOut,
  PlayCircle,
  UserCircle,
  GoogleLogo,
  EnvelopeSimple,
  LockKey,
  ArrowsClockwise,
} from "@phosphor-icons/react";
import {
  siNetflix,
  siSpotify,
  siYoutube,
  siNotion,
  siFigma,
  siGoogle,
  siIcloud,
} from "simple-icons";
import "@fontsource-variable/dm-sans";
import "@fontsource-variable/bricolage-grotesque";
import "./styles.css";
import "./responsive.css";
import {
  TODAY,
  STORAGE_KEY,
  initialSubscriptions,
  categories,
  categoryColors,
  money,
  dateLabel,
  parseDate,
  daysUntil,
  monthlyTotal,
  monthlyValue,
  occurrences,
  readSaved,
} from "./data";

const icons = {
  netflix: siNetflix,
  spotify: siSpotify,
  youtube: siYoutube,
  notion: siNotion,
  figma: siFigma,
  google: siGoogle,
  icloud: siIcloud,
};
const brandStyles = {
  netflix: ["#fff0ec", "#e50914"],
  spotify: ["#eaf3e7", "#148b46"],
  youtube: ["#fff3ec", "#ef312a"],
  notion: ["#f0ede6", "#232323"],
  figma: ["#eeebfb", "#7753b5"],
  google: ["#edf1fb", "#4285f4"],
  prime: ["#e9f3fa", "#0086be"],
  icloud: ["#edf2fb", "#4185dd"],
  custom: ["#f0ede6", "#625d53"],
};
const routes = [
  { id: "overview", label: "Overview", icon: SquaresFour },
  { id: "subscriptions", label: "Subscriptions", icon: Stack },
  { id: "calendar", label: "Calendar", icon: CalendarBlank },
  { id: "insights", label: "Insights", icon: ChartBar },
  { id: "reminders", label: "Reminders", icon: Bell },
];
function Brand({ brand = "custom", large = false }) {
  const icon = icons[brand];
  const [bg, color] = brandStyles[brand] || brandStyles.custom;
  return (
    <span
      className={`brand-icon ${large ? "large" : ""}`}
      style={{ background: bg, color }}
      aria-hidden="true"
    >
      {icon ? (
        <svg viewBox="0 0 24 24" fill="currentColor">
          <path d={icon.path} />
        </svg>
      ) : brand === "prime" ? (
        <PlayCircle size={large ? 34 : 24} weight="fill" />
      ) : (
        <Stack size={large ? 34 : 24} />
      )}
    </span>
  );
}
function Button({
  children,
  icon: Icon,
  onClick,
  variant = "",
  className = "",
  ...props
}) {
  return (
    <button
      className={`button ${variant} ${className}`}
      onClick={onClick}
      {...props}
    >
      {Icon && <Icon size={18} aria-hidden="true" />}
      {children}
    </button>
  );
}
function IconButton({ icon: Icon, label, ...props }) {
  return (
    <button className="icon-button" aria-label={label} title={label} {...props}>
      <Icon size={21} aria-hidden="true" />
    </button>
  );
}
function Empty({
  title = "Nothing here just yet.",
  text = "A little space for something new.",
  action,
}) {
  return (
    <div className="empty">
      <div className="empty-icon">
        <Stack size={32} />
      </div>
      <h3>{title}</h3>
      <p>{text}</p>
      {action}
    </div>
  );
}
function Toggle({ checked, onChange, label }) {
  return (
    <button
      type="button"
      className={`toggle ${checked ? "on" : ""}`}
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={() => onChange(!checked)}
    >
      <span />
    </button>
  );
}
function App() {
  const [saved] = useState(readSaved);
  const [subs, setSubs] = useState(saved?.subs || initialSubscriptions);
  const [prefs, setPrefs] = useState({
    ...{ budget: 4000, reminder: 3, notifications: true, theme: "light" },
    ...saved?.prefs,
  });
  const [page, setPage] = useState(location.hash.slice(1) || "overview");
  const [modal, setModal] = useState(null),
    [toast, setToast] = useState(""),
    [period, setPeriod] = useState("Monthly");
  const [search, setSearch] = useState(""),
    [filter, setFilter] = useState("All"),
    [category, setCategory] = useState("All categories"),
    [layout, setLayout] = useState("grid");
  const [month, setMonth] = useState(8),
    [year, setYear] = useState(2026),
    [selectedDay, setSelectedDay] = useState(null);
  const [compact, setCompact] = useState(false);
  const [insightPeriod, setInsightPeriod] = useState("Monthly");
  const [reminderTab, setReminderTab] = useState("Upcoming");
  const restoreInput = useRef(null);
  const notify = (message) => setToast(message);
  useEffect(() => {
    const change = () => {
      setPage(location.hash.slice(1) || "overview");
      setSearch("");
      setSelectedDay(null);
    };
    window.addEventListener("hashchange", change);
    return () => window.removeEventListener("hashchange", change);
  }, []);
  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({ subs, prefs }));
    } catch {
      notify(
        "Browser storage is unavailable. Changes will last for this session.",
      );
    }
  }, [subs, prefs]);
  useEffect(() => {
    document.documentElement.dataset.theme = prefs.theme;
  }, [prefs.theme]);
  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(""), 4500);
    return () => clearTimeout(timer);
  }, [toast]);
  const go = (id) => {
    location.hash = id;
    setPage(id);
    window.scrollTo({ top: 0, behavior: "instant" });
  };
  const active = subs.filter((s) => s.status === "Active"),
    total = monthlyTotal(subs),
    yearTotal = total * 12;
  const upcoming = subs
    .filter((s) => s.status !== "Archived")
    .sort((a, b) => a.date.localeCompare(b.date));
  const grouped = categories
    .map((name) => ({
      name,
      value: active
        .filter((s) => s.category === name)
        .reduce((a, s) => a + monthlyValue(s), 0),
      color: categoryColors[name],
    }))
    .filter((x) => x.value > 0);
  const add = () => setModal({ type: "edit" });
  const details = (sub) => setModal({ type: "detail", sub });
  const archive = (sub) => {
    setSubs((prev) =>
      prev.map((s) =>
        s.id === sub.id
          ? { ...s, status: s.status === "Archived" ? "Active" : "Archived" }
          : s,
      ),
    );
    setModal(null);
    notify(
      sub.status === "Archived"
        ? `${sub.name} restored.`
        : `${sub.name} archived. Your provider subscription is unchanged.`,
    );
  };
  const save = (sub) => {
    setSubs((prev) =>
      prev.some((s) => s.id === sub.id)
        ? prev.map((s) => (s.id === sub.id ? sub : s))
        : [...prev, sub],
    );
    setModal(null);
    notify(
      `${sub.name} ${subs.some((s) => s.id === sub.id) ? "updated" : "added"}. All in a good place.`,
    );
  };
  const download = (format) => {
    const json = JSON.stringify({ subs, prefs }, null, 2);
    const csv = [
      "Service,Plan,Amount,Cycle,Next payment,Category,Status",
      ...subs.map((s) =>
        [s.name, s.plan, s.price, s.cycle, s.date, s.category, s.status]
          .map((v) => '"' + String(v).replaceAll('"', '""') + '"')
          .join(","),
      ),
    ].join("\n");
    const url = URL.createObjectURL(
      new Blob([format === "json" ? json : csv], {
        type: format === "json" ? "application/json" : "text/csv",
      }),
    );
    const a = document.createElement("a");
    a.href = url;
    a.download = `budgie-${format === "json" ? "backup" : "subscriptions"}.${format}`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    notify(
      format === "json"
        ? "Your backup is ready."
        : "Your subscription export is ready.",
    );
  };
  const restore = async (event) => {
    const file = event.target.files[0];
    if (!file) return;
    try {
      const data = JSON.parse(await file.text());
      if (
        !Array.isArray(data.subs) ||
        !data.subs.every(
          (s) =>
            typeof s.name === "string" &&
            typeof s.id === "string" &&
            s.price > 0 &&
            ["Monthly", "Yearly", "Weekly"].includes(s.cycle) &&
            ["Active", "Trial", "Archived"].includes(s.status) &&
            /^\d{4}-\d{2}-\d{2}$/.test(s.date),
        )
      )
        throw Error();
      setModal({ type: "restore", data });
    } catch {
      notify("That file is not a valid Budgie backup. Your data is unchanged.");
    }
    event.target.value = "";
  };
  const sectionHead = (title, subtitle, action) => (
    <div className="section-heading">
      <div>
        <h2>{title}</h2>
        {subtitle && <p>{subtitle}</p>}
      </div>
      {action}
    </div>
  );
  const serviceCard = (sub) => (
    <button
      className={`service-card ${sub.status === "Archived" ? "archived" : ""}`}
      key={sub.id}
      onClick={() => details(sub)}
    >
      <div className="service-card-top">
        <Brand brand={sub.brand} />
        <span className="card-category">{sub.category}</span>
        <ArrowUpRight className="card-arrow" size={18} />
      </div>
      <h3>{sub.name}</h3>
      <p>{sub.plan || "Personal plan"}</p>
      <div className="service-price">
        {money(sub.price)}
        <span>
          /
          {sub.cycle === "Yearly"
            ? "year"
            : sub.cycle === "Weekly"
              ? "week"
              : "month"}
        </span>
      </div>
      <div className="service-bottom">
        <span className="status-dot" />
        {sub.status === "Archived"
          ? "Archived"
          : sub.status === "Trial"
            ? "Trial ends"
            : "Renews"}{" "}
        {dateLabel(sub.date)}
        <span className="service-cycle">{sub.cycle}</span>
      </div>
    </button>
  );
  const serviceRow = (sub) => (
    <button className="service-row" key={sub.id} onClick={() => details(sub)}>
      <Brand brand={sub.brand} />
      <span className="row-name">
        <strong>{sub.name}</strong>
        <small>
          {sub.plan} · {sub.category}
        </small>
      </span>
      <span className="row-date">
        {dateLabel(sub.date)}
        <small>{sub.status}</small>
      </span>
      <span className="row-amount">
        {money(sub.price)}
        <small>
          /
          {sub.cycle === "Yearly"
            ? "year"
            : sub.cycle === "Weekly"
              ? "week"
              : "month"}
        </small>
      </span>
      <CaretRight size={18} />
    </button>
  );
  function Overview() {
    const next = upcoming[0];
    const budgetRatio = Math.min(100, (total / Number(prefs.budget)) * 100);
    return (
      <>
        <div className="page-heading">
          <div>
            <div className="eyebrow">
              <span className="tiny-sun" />
              <span>A LITTLE CLARITY, EVERY DAY</span>
            </div>
            <h1>
              Good things. On repeat<span className="orange">.</span>
            </h1>
            <p>Your subscriptions, finally in a good place.</p>
          </div>
          <Button icon={Plus} onClick={add}>
            Add subscription
          </Button>
        </div>
        <div className="overview-grid">
          <div className="overview-main">
            <section className="spending-card">
              <div className="spending-top">
                <span>
                  <Wallet size={20} />
                  YOUR RECURRING SPEND
                </span>
                <div className="segmented dark-on-yellow">
                  {["Monthly", "Yearly"].map((x) => (
                    <button
                      key={x}
                      className={period === x ? "selected" : ""}
                      onClick={() => setPeriod(x)}
                    >
                      {x}
                    </button>
                  ))}
                </div>
              </div>
              <div className="spending-body">
                <div>
                  <div className="hero-amount">
                    {money(period === "Monthly" ? total : yearTotal)}
                    <span>/{period === "Monthly" ? "mo" : "yr"}</span>
                  </div>
                  <p>{active.length} subscriptions. One clear picture.</p>
                </div>
                <div
                  className="spending-seal"
                  aria-label={`${active.length} active subscriptions`}
                >
                  <ArrowUpRight size={34} />
                  <span>
                    ALL IN
                    <br />
                    ONE PLACE
                  </span>
                </div>
              </div>
              <div className="spending-footer">
                <div className="mini-brands">
                  {active.slice(0, 5).map((s) => (
                    <Brand key={s.id} brand={s.brand} />
                  ))}
                  {active.length > 5 && (
                    <span className="plus-brands">+{active.length - 5}</span>
                  )}
                </div>
                <button onClick={() => go("insights")}>
                  See the breakdown <ArrowUpRight size={17} />
                </button>
              </div>
            </section>
            <div className="quick-stats">
              <div>
                <span>
                  Annual outlook
                  <ArrowUpRight size={16} />
                </span>
                <strong>{money(yearTotal)}</strong>
                <small>At your current pace</small>
              </div>
              <div>
                <span>
                  Next 7 days
                  <CalendarBlank size={16} />
                </span>
                <strong>
                  {money(
                    upcoming
                      .filter(
                        (s) => daysUntil(s.date) >= 0 && daysUntil(s.date) <= 7,
                      )
                      .reduce((a, s) => a + s.price, 0),
                  )}
                </strong>
                <small>
                  {
                    upcoming.filter(
                      (s) => daysUntil(s.date) >= 0 && daysUntil(s.date) <= 7,
                    ).length
                  }{" "}
                  upcoming payments
                </small>
              </div>
              <button onClick={() => go("subscriptions")}>
                <span>
                  Active subscriptions
                  <Stack size={16} />
                </span>
                <strong>
                  {String(active.length).padStart(2, "0")}
                  <span className="stat-sticker">All yours</span>
                </strong>
                <small>Everything you love</small>
              </button>
            </div>
            {sectionHead(
              "Your little collection",
              `${active.length} subscriptions that make life a little better.`,
              <button
                className="text-button"
                onClick={() => go("subscriptions")}
              >
                View all <ArrowRight size={17} />
              </button>,
            )}
            <div className="service-grid">
              {active.slice(0, 6).map(serviceCard)}
            </div>
            {!active.length && (
              <Empty
                title="Start your collection."
                text="Add your first subscription to see your spending clearly."
                action={
                  <Button icon={Plus} onClick={add}>
                    Add subscription
                  </Button>
                }
              />
            )}
          </div>
          <aside className="overview-aside">
            <section className="next-card">
              <div className="aside-heading">
                <h2>Up next</h2>
                <span className="live-label">THIS MONTH</span>
              </div>
              {next ? (
                <>
                  <button
                    className="next-feature"
                    onClick={() => details(next)}
                  >
                    <Brand brand={next.brand} />
                    <div>
                      <strong>{next.name}</strong>
                      <p>{next.plan}</p>
                    </div>
                    <ArrowUpRight size={18} />
                  </button>
                  <div className="next-price">
                    {money(next.price)}
                    <span>in {Math.max(0, daysUntil(next.date))} days</span>
                  </div>
                  <div className="next-date">
                    <CalendarBlank size={17} />
                    {dateLabel(next.date, {
                      weekday: "long",
                      day: "numeric",
                      month: "short",
                    })}
                    <CheckCircle size={17} />
                  </div>
                </>
              ) : (
                <p>No upcoming renewals.</p>
              )}
              <div className="timeline">
                {upcoming.slice(1, 4).map((s) => (
                  <button key={s.id} onClick={() => details(s)}>
                    <span className="timeline-date">
                      {dateLabel(s.date, { day: "2-digit" })}
                      <small>SEP</small>
                    </span>
                    <Brand brand={s.brand} />
                    <span>
                      <strong>{s.name}</strong>
                      <small>{s.plan}</small>
                    </span>
                    <b>{money(s.price)}</b>
                  </button>
                ))}
              </div>
              <button className="wide-link" onClick={() => go("calendar")}>
                Open calendar <ArrowRight size={17} />
              </button>
            </section>
            <section className="budget-card">
              <div className="aside-heading">
                <span className="budget-icon">
                  <CircleNotch size={22} />
                </span>
                <button
                  onClick={() => setModal({ type: "budget" })}
                  aria-label="Edit monthly budget"
                >
                  <PencilSimple size={18} />
                </button>
              </div>
              <h2>A little breathing room.</h2>
              <p>
                {total <= prefs.budget
                  ? "You’re keeping things in balance."
                  : "A good moment to review your subscriptions."}
              </p>
              <div className="budget-amount">
                {money(Math.abs(prefs.budget - total))}
                <small>
                  {total <= prefs.budget
                    ? "left in your budget"
                    : "over your budget"}
                </small>
              </div>
              <div
                className="budget-track"
                role="meter"
                aria-label="Monthly budget used"
                aria-valuemin={0}
                aria-valuemax={prefs.budget}
                aria-valuenow={Math.min(total, prefs.budget)}
              >
                <span style={{ width: `${budgetRatio}%` }} />
              </div>
              <div className="budget-labels">
                <span>{money(total)} spent</span>
                <span>{money(prefs.budget)} budget</span>
              </div>
            </section>
            <div className="local-note">
              <ShieldCheck size={21} />
              <p>
                A space that’s just yours.
                <br />
                <span>Your data stays in this browser.</span>
              </p>
            </div>
          </aside>
        </div>
      </>
    );
  }
  function Subscriptions() {
    const result = subs.filter(
      (s) =>
        (filter === "All" ? s.status !== "Archived" : s.status === filter) &&
        [s.name, s.plan, s.category]
          .join(" ")
          .toLowerCase()
          .includes(search.toLowerCase()) &&
        (category === "All categories" || s.category === category),
    );
    return (
      <>
        <PageHeading
          eyebrow="A HOME FOR YOUR FAVORITES"
          title="Your subscriptions."
          subtitle="The things you love. And what they cost."
          action={
            <Button icon={Plus} onClick={add}>
              Add subscription
            </Button>
          }
        />
        <div className="collection-summary">
          <div>
            <span>Monthly commitment</span>
            <strong>{money(total)}</strong>
          </div>
          <div>
            <span>Active subscriptions</span>
            <strong>{active.length}</strong>
          </div>
          <div>
            <span>Annual outlook</span>
            <strong>{money(yearTotal)}</strong>
          </div>
        </div>
        <div className="library-toolbar">
          <div className="filter-tabs">
            {["All", "Active", "Trial", "Archived"].map((x) => (
              <button
                key={x}
                className={filter === x ? "selected" : ""}
                onClick={() => setFilter(x)}
              >
                {x === "All"
                  ? "All subscriptions"
                  : x === "Trial"
                    ? "Free trials"
                    : x}
                <span>
                  {
                    subs.filter((s) =>
                      x === "All" ? s.status !== "Archived" : s.status === x,
                    ).length
                  }
                </span>
              </button>
            ))}
          </div>
          <div className="view-switch">
            <IconButton
              icon={GridFour}
              label="Grid view"
              aria-pressed={layout === "grid"}
              onClick={() => setLayout("grid")}
            />
            <IconButton
              icon={List}
              label="List view"
              aria-pressed={layout === "list"}
              onClick={() => setLayout("list")}
            />
          </div>
        </div>
        <div className="search-row">
          <label className="search-field">
            <MagnifyingGlass size={20} />
            <input
              aria-label="Search subscriptions"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Find a subscription…"
            />
            {search && (
              <button aria-label="Clear search" onClick={() => setSearch("")}>
                <X size={16} />
              </button>
            )}
          </label>
          <label className="select-field">
            <SlidersHorizontal size={18} />
            <select
              aria-label="Filter by category"
              value={category}
              onChange={(e) => setCategory(e.target.value)}
            >
              {["All categories", ...categories].map((x) => (
                <option key={x}>{x}</option>
              ))}
            </select>
          </label>
        </div>
        <div
          className={
            layout === "grid" ? "service-grid library-grid" : "service-list"
          }
        >
          {result.map(layout === "grid" ? serviceCard : serviceRow)}
        </div>
        {!result.length && (
          <Empty
            title={
              search
                ? "No matches this time."
                : filter === "Archived"
                  ? "Nothing archived."
                  : filter === "Trial"
                    ? "No free trials right now."
                    : "Your collection starts here."
            }
            text={
              search
                ? "Try another name or category."
                : "Every subscription you add will have a place here."
            }
            action={
              !search && (
                <Button icon={Plus} onClick={add}>
                  Add subscription
                </Button>
              )
            }
          />
        )}
      </>
    );
  }
  function Calendar() {
    const items = occurrences(subs, year, month);
    const first = (new Date(year, month, 1).getDay() + 6) % 7;
    const count = new Date(year, month + 1, 0).getDate();
    const move = (n) => {
      const d = new Date(year, month + n, 1);
      setYear(d.getFullYear());
      setMonth(d.getMonth());
      setSelectedDay(null);
    };
    const shown = selectedDay
      ? items.filter((s) => parseDate(s.occurrence).getDate() === selectedDay)
      : items;
    return (
      <>
        <PageHeading
          eyebrow="NO MORE SURPRISES"
          title="A date with your dues."
          subtitle="A clear view of what’s coming. Room to plan ahead."
          action={
            <Button icon={Plus} onClick={add}>
              Add subscription
            </Button>
          }
        />
        <div className="calendar-layout">
          <section className="calendar-panel">
            <div className="calendar-toolbar">
              <h2>
                {new Date(year, month, 1).toLocaleDateString("en-IN", {
                  month: "long",
                  year: "numeric",
                })}
              </h2>
              <div>
                <button
                  className="text-button"
                  onClick={() => {
                    setYear(2026);
                    setMonth(8);
                    setSelectedDay(12);
                  }}
                >
                  Today
                </button>
                <IconButton
                  icon={CaretLeft}
                  label="Previous month"
                  onClick={() => move(-1)}
                />
                <IconButton
                  icon={CaretRight}
                  label="Next month"
                  onClick={() => move(1)}
                />
              </div>
            </div>
            <div className="calendar-grid">
              {["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"].map((d) => (
                <span className="weekday" key={d}>
                  {d}
                </span>
              ))}
              {Array.from({ length: first }, (_, i) => (
                <span className="day blank" key={`blank${i}`} />
              ))}
              {Array.from({ length: count }, (_, i) => {
                const day = i + 1;
                const due = items.filter(
                  (s) => parseDate(s.occurrence).getDate() === day,
                );
                const today = year === 2026 && month === 8 && day === 12;
                return (
                  <button
                    key={day}
                    className={`day ${today ? "today" : ""} ${selectedDay === day ? "chosen" : ""} ${due.length ? "has-due" : ""}`}
                    aria-label={`${new Date(year, month, day).toLocaleDateString("en-IN", { month: "long", day: "numeric", year: "numeric" })}, ${due.length} payments`}
                    aria-pressed={selectedDay === day}
                    onClick={() =>
                      setSelectedDay(selectedDay === day ? null : day)
                    }
                  >
                    <span className="day-number">{day}</span>
                    <span className="day-logos">
                      {due.slice(0, 2).map((s) => (
                        <Brand key={s.id} brand={s.brand} />
                      ))}
                    </span>
                    {due.length > 0 && (
                      <small>
                        {money(due.reduce((a, s) => a + s.price, 0))}
                      </small>
                    )}
                  </button>
                );
              })}
            </div>
            <div className="calendar-legend">
              <span>
                <i />
                Today
              </span>
              <span>
                <i />
                Payment date
              </span>
              <p>Select a date to see its subscriptions.</p>
            </div>
          </section>
          <aside className="month-summary">
            <span className="eyebrow">PLANNED PAYMENTS</span>
            <h2>{money(items.reduce((a, s) => a + s.price, 0))}</h2>
            <p>{items.length} renewals this month</p>
            <hr />
            <div className="aside-heading">
              <h3>
                {selectedDay
                  ? `${selectedDay} ${new Date(year, month, 1).toLocaleDateString("en-IN", { month: "short" })}`
                  : "This month"}
              </h3>
              {selectedDay && (
                <button
                  className="text-button"
                  onClick={() => setSelectedDay(null)}
                >
                  Show all
                </button>
              )}
            </div>
            <div className="month-agenda">
              {shown.map((s) => (
                <button
                  key={`${s.id}${s.occurrence}`}
                  onClick={() => details(s)}
                >
                  <Brand brand={s.brand} />
                  <span>
                    <strong>{s.name}</strong>
                    <small>
                      {dateLabel(s.occurrence)} · {s.cycle}
                    </small>
                  </span>
                  <b>{money(s.price)}</b>
                </button>
              ))}
            </div>
            {!shown.length && (
              <Empty
                title="A little breathing room."
                text="No payments on this date."
              />
            )}
          </aside>
        </div>
      </>
    );
  }
  function Insights() {
    const annual = insightPeriod === "Yearly";
    const value = annual ? yearTotal : total;
    return (
      <>
        <PageHeading
          eyebrow="THE BIGGER PICTURE"
          title="Know where it goes."
          subtitle="Small payments add up. Let’s make them make sense."
          action={
            <Button
              variant="secondary"
              icon={DownloadSimple}
              onClick={() => download("csv")}
            >
              Export data
            </Button>
          }
        />
        <div className="insights-summary">
          <div className="insight-total">
            <span>Your {annual ? "annual" : "monthly"} commitment</span>
            <h2>{money(value)}</h2>
            <p>
              {active.length} active subscriptions ·{" "}
              {annual ? "Annualized estimate" : "Current monthly estimate"}
            </p>
          </div>
          <div className="segmented">
            {["Monthly", "Yearly"].map((x) => (
              <button
                key={x}
                className={insightPeriod === x ? "selected" : ""}
                onClick={() => setInsightPeriod(x)}
              >
                {x}
              </button>
            ))}
          </div>
        </div>
        <div className="insights-grid">
          <section className="chart-panel">
            <div className="section-heading">
              <div>
                <h2>A look ahead</h2>
                <p>Projected spend at your current rates</p>
              </div>
              <span className="soft-tag">
                {annual ? "NEXT 3 YEARS" : "NEXT 6 MONTHS"}
              </span>
            </div>
            <div
              className="bar-chart"
              role="img"
              aria-label={
                annual
                  ? `Annual projection ${money(yearTotal)} each year at current rates`
                  : `Monthly projection ${money(total)} from September through February at current rates`
              }
            >
              {(annual
                ? ["2027", "2028", "2029"]
                : ["Sep", "Oct", "Nov", "Dec", "Jan", "Feb"]
              ).map((label, i) => (
                <div className="bar-column" key={label}>
                  <span>{money(value)}</span>
                  <div
                    className={`chart-bar ${i === 0 ? "current" : ""}`}
                    style={{ height: `${value ? 180 : 4}px` }}
                  />
                  <small>{label}</small>
                </div>
              ))}
            </div>
            <div className="chart-footnote">
              <CircleNotch size={16} /> Estimates assume no plan or price
              changes.
            </div>
          </section>
          <section className="category-panel">
            <h2>A little of everything.</h2>
            <p>Spending by category</p>
            <div
              className="donut"
              role="img"
              aria-label={grouped
                .map((g) => `${g.name}: ${money(g.value)}`)
                .join(", ")}
              style={{
                background: `conic-gradient(${grouped.map((g, i) => `${g.color} ${(grouped.slice(0, i).reduce((a, x) => a + x.value, 0) / total) * 100}% ${(grouped.slice(0, i + 1).reduce((a, x) => a + x.value, 0) / total) * 100}%`).join(", ") || "#ecebe4"})`,
              }}
            >
              <div>
                <strong>{active.length}</strong>
                <span>subscriptions</span>
              </div>
            </div>
            <div className="category-legend">
              {grouped.map((g) => (
                <div key={g.name}>
                  <i style={{ background: g.color }} />
                  <span>{g.name}</span>
                  <strong>{money(g.value * (annual ? 12 : 1))}</strong>
                  <small>{Math.round((g.value / total) * 100)}%</small>
                </div>
              ))}
            </div>
          </section>
        </div>
        {sectionHead(
          "The biggest pieces",
          "Your subscriptions, ranked by monthly cost.",
        )}
        <div className="ranking">
          {[...active]
            .sort((a, b) => monthlyValue(b) - monthlyValue(a))
            .map((s, i) => (
              <button key={s.id} onClick={() => details(s)}>
                <span className="rank-number">
                  {String(i + 1).padStart(2, "0")}
                </span>
                <Brand brand={s.brand} />
                <strong>{s.name}</strong>
                <span className="ranking-track">
                  <span
                    style={{
                      width: `${(monthlyValue(s) / Math.max(...active.map(monthlyValue))) * 100}%`,
                      background: categoryColors[s.category] || "#bbb",
                    }}
                  />
                </span>
                <b>
                  {money(monthlyValue(s))}
                  <small>/mo</small>
                </b>
                <ArrowUpRight size={17} />
              </button>
            ))}
        </div>
      </>
    );
  }
  function Reminders() {
    const tab = reminderTab,
      setTab = setReminderTab;
    const list =
      tab === "Upcoming"
        ? upcoming
        : upcoming.filter((s) => s.status === "Trial");
    return (
      <>
        <PageHeading
          eyebrow="A FRIENDLY HEADS-UP"
          title="Stay one step ahead."
          subtitle="A small nudge before the next payment."
          action={
            <Button
              variant="secondary"
              icon={GearSix}
              onClick={() => go("settings")}
            >
              Reminder settings
            </Button>
          }
        />
        <div className="reminder-banner">
          <span className="banner-bell">
            <Bell size={29} />
          </span>
          <div>
            <h2>
              {prefs.notifications
                ? "A little reminder. A lot less worry."
                : "Taking a quiet moment."}
            </h2>
            <p>
              {prefs.notifications
                ? "Reminders are enabled in this preview. Native notifications come with the Android app."
                : "Your reminder preference is off. You can still see all upcoming payments here."}
            </p>
          </div>
          <Toggle
            label="Enable reminders"
            checked={prefs.notifications}
            onChange={(v) => setPrefs({ ...prefs, notifications: v })}
          />
        </div>
        <div className="filter-tabs reminder-tabs">
          {["Upcoming", "Free trials"].map((x) => (
            <button
              key={x}
              onClick={() => setTab(x)}
              className={tab === x ? "selected" : ""}
            >
              {x}
            </button>
          ))}
        </div>
        <div className="reminder-list">
          {list.map((s) => (
            <button key={s.id} onClick={() => details(s)}>
              <div className="reminder-day">
                <strong>{parseDate(s.date).getDate()}</strong>
                <span>{dateLabel(s.date, { month: "short" })}</span>
              </div>
              <Brand brand={s.brand} />
              <div className="reminder-info">
                <h3>
                  {s.name}
                  <span className="soft-tag">
                    {s.status === "Trial"
                      ? "TRIAL ENDS"
                      : `${daysUntil(s.date)} DAYS TO GO`}
                  </span>
                </h3>
                <p>
                  {s.plan} · {s.cycle} renewal
                </p>
                <small>
                  <Bell size={13} /> Reminder {s.reminder}{" "}
                  {s.reminder === 1 ? "day" : "days"} before renewal
                </small>
              </div>
              <strong>{money(s.price)}</strong>
              <ArrowUpRight size={20} />
            </button>
          ))}
        </div>
        {!list.length && (
          <Empty
            title="Nothing to keep an eye on."
            text="Your trial reminders will appear here when you add a free trial."
            action={
              <Button onClick={add} icon={Plus}>
                Add a free trial
              </Button>
            }
          />
        )}
      </>
    );
  }
  function Settings() {
    return (
      <>
        <PageHeading
          eyebrow="MAKE YOURSELF AT HOME"
          title="Your space. Your way."
          subtitle="The small details that make Budgie yours."
        />
        <div className="settings-layout">
          <div>
            <section className="settings-section account-settings">
              <h2>Your Budgie account</h2>
              <div className="account-card">
                <div className="account-mark" aria-hidden="true">
                  <Bird size={36} weight="fill" />
                </div>
                <div className="account-copy">
                  <span>LOCAL MODE</span>
                  <h3>Take your collection with you.</h3>
                  <p>
                    Sign in to keep your subscriptions within reach across the
                    website and Android app.
                  </p>
                  <div
                    className="account-benefits"
                    aria-label="Account benefits"
                  >
                    <span>
                      <ArrowsClockwise size={15} /> Sync across devices
                    </span>
                    <span>
                      <ShieldCheck size={15} /> Your private collection
                    </span>
                  </div>
                </div>
                <Button
                  className="account-action"
                  icon={UserCircle}
                  onClick={() => setModal({ type: "auth" })}
                >
                  Sign in
                </Button>
              </div>
            </section>
            <section className="settings-section">
              <h2>The everyday essentials</h2>
              <Setting
                icon={Wallet}
                title="Monthly budget"
                text="A little boundary for your recurring spending."
              >
                <button
                  className="setting-value"
                  onClick={() => setModal({ type: "budget" })}
                >
                  {money(prefs.budget)}
                  <CaretRight size={17} />
                </button>
              </Setting>
              <Setting
                icon={CreditCard}
                title="Currency"
                text="This preview uses Indian rupees."
              >
                <span className="setting-value">INR (₹)</span>
              </Setting>
              <Setting
                icon={prefs.theme === "light" ? Sun : Moon}
                title="Appearance"
                text="A look that feels right."
              >
                <select
                  aria-label="Appearance"
                  value={prefs.theme}
                  onChange={(e) =>
                    setPrefs({ ...prefs, theme: e.target.value })
                  }
                >
                  <option value="light">Daylight</option>
                  <option value="dark">After hours</option>
                </select>
              </Setting>
            </section>
            <section className="settings-section">
              <h2>A friendly nudge</h2>
              <Setting
                icon={Bell}
                title="Renewal reminders"
                text="Save your reminder preference for the Android design."
              >
                <Toggle
                  label="Renewal reminders"
                  checked={prefs.notifications}
                  onChange={(v) => setPrefs({ ...prefs, notifications: v })}
                />
              </Setting>
              <Setting
                icon={CalendarBlank}
                title="Default reminder"
                text="Used when you add a new subscription."
              >
                <select
                  aria-label="Default reminder"
                  value={prefs.reminder}
                  onChange={(e) =>
                    setPrefs({ ...prefs, reminder: Number(e.target.value) })
                  }
                >
                  {[1, 2, 3, 7].map((d) => (
                    <option key={d} value={d}>
                      {d} {d === 1 ? "day" : "days"} before
                    </option>
                  ))}
                </select>
              </Setting>
            </section>
            <section className="settings-section">
              <h2>Always in your hands</h2>
              <Setting
                icon={DownloadSimple}
                title="Export subscriptions"
                text="Take your collection with you as a CSV file."
              >
                <Button
                  variant="secondary small"
                  onClick={() => download("csv")}
                >
                  Export
                  <ArrowUpRight size={16} />
                </Button>
              </Setting>
              <Setting
                icon={Cloud}
                title="Back up your collection"
                text="Save subscriptions and preferences in a JSON file."
              >
                <Button
                  variant="secondary small"
                  onClick={() => download("json")}
                >
                  Back up
                  <ArrowUpRight size={16} />
                </Button>
              </Setting>
              <Setting
                icon={UploadSimple}
                title="Restore a backup"
                text="Bring back a previously saved Budgie collection."
              >
                <Button
                  variant="secondary small"
                  onClick={() => restoreInput.current.click()}
                >
                  Restore
                  <ArrowUpRight size={16} />
                </Button>
              </Setting>
            </section>
          </div>
          <aside>
            <div className="about-card">
              <Bird size={54} weight="fill" />
              <h2>
                A little bird.
                <br />A clearer picture.
              </h2>
              <p>
                Budgie gives your subscriptions a home, so you can spend more
                thoughtfully and live a little lighter.
              </p>
              <span>Made for the everyday.</span>
              <div>Budgie · Design preview</div>
            </div>
            <div className="privacy-note">
              <ShieldCheck size={24} />
              <h3>Private by design.</h3>
              <p>
                This interactive preview stores your collection locally in this
                browser. The sign-in experience is a preview until Firebase is
                connected, so no account data is sent to a server yet.
              </p>
              <p>
                Example prices and dates are sample data, anchored to September
                12, 2026.
              </p>
            </div>
          </aside>
        </div>
      </>
    );
  }
  const content = {
    overview: Overview,
    subscriptions: Subscriptions,
    calendar: Calendar,
    insights: Insights,
    reminders: Reminders,
    settings: Settings,
  };
  return (
    <div className={`app-shell ${compact ? "mobile-preview" : ""}`}>
      <a
        className="skip-link"
        href="#main"
        onClick={(e) => {
          e.preventDefault();
          document.getElementById("main").focus();
        }}
      >
        Skip to content
      </a>
      <aside className="sidebar">
        <button
          className="wordmark"
          onClick={() => go("overview")}
          aria-label="Budgie home"
        >
          <span>
            <Bird size={31} weight="fill" />
          </span>
          budgie
        </button>
        <div className="sidebar-caption">A LITTLE MORE IN CONTROL.</div>
        <nav aria-label="Main navigation">
          {routes.map(({ id, label, icon: Icon }) => (
            <button
              key={id}
              onClick={() => go(id)}
              className={page === id ? "active" : ""}
              aria-current={page === id ? "page" : undefined}
            >
              <Icon size={21} weight={page === id ? "fill" : "regular"} />
              <span>{label}</span>
              {id === "subscriptions" && (
                <small>
                  {subs.filter((s) => s.status !== "Archived").length}
                </small>
              )}
              {id === "reminders" && prefs.notifications && (
                <span className="nav-dot" />
              )}
            </button>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="sidebar-message">
            <Sparkle size={25} />
            <p>
              Less keeping track.
              <br />
              More living life.
            </p>
            <span>We like the sound of that.</span>
          </div>
          <button
            className={`settings-nav ${page === "settings" ? "active" : ""}`}
            onClick={() => go("settings")}
          >
            <GearSix size={21} />
            Settings
          </button>
          <div className="profile">
            <span className="profile-avatar">
              <Bird size={22} weight="fill" />
            </span>
            <div>
              <strong>Your personal space</strong>
              <small>Just you and your favorites.</small>
            </div>
          </div>
        </div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <div className="breadcrumb">
            Your workspace
            <CaretRight size={13} />
            <strong>
              {routes.find((r) => r.id === page)?.label || "Settings"}
            </strong>
          </div>
          <div className="topbar-actions">
            <span className="demo-label">DESIGN PREVIEW</span>
            <span className="today-label">Sat, 12 September</span>
            <IconButton
              icon={Bell}
              label="Open reminders"
              onClick={() => go("reminders")}
            />
            <button
              className={`preview-toggle ${compact ? "active" : ""}`}
              onClick={() => setCompact(!compact)}
              title="Toggle mobile layout"
            >
              {compact ? "Desktop view" : "Mobile view"}
            </button>
          </div>
        </header>
        <main id="main" tabIndex={-1}>
          {(content[page] || Overview)()}
        </main>
        <footer className="page-footer">
          <Bird size={16} weight="fill" />
          <span>A little less to think about.</span>
          <span>Made with a little care.</span>
        </footer>
      </div>
      <nav className="mobile-nav" aria-label="Mobile navigation">
        {routes.slice(0, 4).map(({ id, label, icon: Icon }) => (
          <button
            key={id}
            onClick={() => go(id)}
            aria-current={page === id ? "page" : undefined}
            className={page === id ? "active" : ""}
          >
            <Icon size={22} weight={page === id ? "fill" : "regular"} />
            <span>{id === "overview" ? "Home" : label}</span>
          </button>
        ))}
        <button
          onClick={() => go("settings")}
          className={page === "settings" ? "active" : ""}
        >
          <GearSix size={22} />
          <span>Settings</span>
        </button>
      </nav>
      <input
        type="file"
        ref={restoreInput}
        accept="application/json,.json"
        onChange={restore}
        hidden
      />
      {modal && (
        <Modal
          onClose={() => setModal(null)}
          title={
            modal.type === "edit"
              ? modal.sub
                ? "Edit subscription"
                : "Make room for a new favorite."
              : modal.type === "detail"
                ? "Subscription details"
                : modal.type === "budget"
                  ? "A little breathing room."
                  : modal.type === "restore"
                    ? "Restore your collection?"
                    : modal.type === "auth"
                      ? "Welcome to your collection."
                      : "Archive subscription?"
          }
        >
          {modal.type === "edit" ? (
            <SubscriptionForm
              sub={modal.sub}
              reminder={prefs.reminder}
              save={save}
              cancel={() => setModal(null)}
            />
          ) : modal.type === "detail" ? (
            <Detail
              sub={modal.sub}
              edit={() => setModal({ type: "edit", sub: modal.sub })}
              archive={() =>
                modal.sub.status === "Archived"
                  ? archive(modal.sub)
                  : setModal({ type: "archive", sub: modal.sub })
              }
            />
          ) : modal.type === "budget" ? (
            <BudgetForm
              current={prefs.budget}
              save={(budget) => {
                setPrefs({ ...prefs, budget });
                setModal(null);
                notify("Your budget has a fresh start.");
              }}
            />
          ) : modal.type === "restore" ? (
            <>
              <p className="modal-copy">
                Replace your current collection with {modal.data.subs.length}{" "}
                subscriptions from this backup? You can export your current
                collection first.
              </p>
              <div className="modal-actions">
                <Button variant="secondary" onClick={() => setModal(null)}>
                  Keep current collection
                </Button>
                <Button
                  onClick={() => {
                    setSubs(modal.data.subs);
                    setModal(null);
                    notify("Your subscriptions have been restored.");
                  }}
                >
                  Restore subscriptions
                </Button>
              </div>
            </>
          ) : modal.type === "auth" ? (
            <AuthForm />
          ) : (
            <>
              <p className="modal-copy">
                {modal.sub.name} will move to your archive and leave your
                spending totals. You can restore it any time.
              </p>
              <div className="callout">
                <ShieldCheck size={21} />
                <span>
                  This only changes your Budgie collection. To stop payments,
                  cancel directly with your provider.
                </span>
              </div>
              <div className="modal-actions">
                <Button variant="secondary" onClick={() => setModal(null)}>
                  Keep subscription
                </Button>
                <Button onClick={() => archive(modal.sub)}>
                  Archive subscription
                </Button>
              </div>
            </>
          )}
        </Modal>
      )}
      {toast && (
        <div className="toast" role="status">
          <CheckCircle size={22} />
          <span>{toast}</span>
          <button aria-label="Dismiss message" onClick={() => setToast("")}>
            <X size={17} />
          </button>
        </div>
      )}
    </div>
  );
}
function AuthForm() {
  const [mode, setMode] = useState("signin");
  const [message, setMessage] = useState("");
  const previewAuth = () =>
    setMessage(
      "Account access will turn on when Firebase is connected. Your collection is still private in this browser.",
    );
  return (
    <div className="auth-panel">
      <p className="modal-copy">
        {mode === "signin"
          ? "Sign in to see the same collection wherever Budgie goes with you."
          : "Create an account for one collection shared between web and Android."}
      </p>
      <div className="auth-mode" role="group" aria-label="Account action">
        <button
          type="button"
          className={mode === "signin" ? "active" : ""}
          aria-pressed={mode === "signin"}
          onClick={() => {
            setMode("signin");
            setMessage("");
          }}
        >
          Sign in
        </button>
        <button
          type="button"
          className={mode === "signup" ? "active" : ""}
          aria-pressed={mode === "signup"}
          onClick={() => {
            setMode("signup");
            setMessage("");
          }}
        >
          Create account
        </button>
      </div>
      <Button
        variant="secondary auth-provider"
        icon={GoogleLogo}
        type="button"
        onClick={previewAuth}
      >
        Continue with Google
      </Button>
      <div className="auth-divider" aria-hidden="true">
        <span>or continue with email</span>
      </div>
      <form
        className="auth-form"
        onSubmit={(event) => {
          event.preventDefault();
          previewAuth();
        }}
      >
        <label htmlFor="account-email">
          Email address
          <span className="auth-input">
            <EnvelopeSimple size={18} aria-hidden="true" />
            <input
              id="account-email"
              name="email"
              type="email"
              autoComplete="email"
              placeholder="you@example.com"
              required
            />
          </span>
        </label>
        <label htmlFor="account-password">
          Password
          <span className="auth-input">
            <LockKey size={18} aria-hidden="true" />
            <input
              id="account-password"
              name="password"
              type="password"
              autoComplete={
                mode === "signin" ? "current-password" : "new-password"
              }
              minLength={8}
              required
            />
          </span>
        </label>
        {mode === "signin" && (
          <button className="auth-link" type="button" onClick={previewAuth}>
            Forgot password?
          </button>
        )}
        <Button className="auth-submit" type="submit">
          {mode === "signin" ? "Sign in with email" : "Create my account"}
          <ArrowRight size={17} aria-hidden="true" />
        </Button>
      </form>
      {message && (
        <div className="auth-message" role="status" aria-live="polite">
          <ShieldCheck size={18} aria-hidden="true" />
          <span>{message}</span>
        </div>
      )}
      <p className="auth-terms">
        Account creation will include Budgie’s terms and privacy policy when
        sign-in launches.
      </p>
    </div>
  );
}
function PageHeading({ eyebrow, title, subtitle, action }) {
  return (
    <div className="page-heading">
      <div>
        <div className="eyebrow">{eyebrow}</div>
        <h1>{title}</h1>
        <p>{subtitle}</p>
      </div>
      {action}
    </div>
  );
}
function Setting({ icon: Icon, title, text, children }) {
  return (
    <div className="setting-row">
      <span className="setting-icon">
        <Icon size={21} />
      </span>
      <div>
        <h3>{title}</h3>
        <p>{text}</p>
      </div>
      {children}
    </div>
  );
}
function Modal({ children, onClose, title }) {
  const ref = useRef(null);
  useEffect(() => {
    const previous = document.activeElement;
    ref.current.showModal();
    const dialog = ref.current;
    const listener = (e) => {
      e.preventDefault();
      onClose();
    };
    dialog.addEventListener("cancel", listener);
    return () => {
      dialog.removeEventListener("cancel", listener);
      previous?.focus();
    };
  }, []);
  return (
    <dialog
      ref={ref}
      className="modal"
      aria-labelledby="modal-title"
      onClick={(e) => {
        if (e.target === ref.current) {
          const r = ref.current.getBoundingClientRect();
          if (
            e.clientX < r.left ||
            e.clientX > r.right ||
            e.clientY < r.top ||
            e.clientY > r.bottom
          )
            onClose();
        }
      }}
    >
      <div className="modal-head">
        <span className="eyebrow">YOUR COLLECTION</span>
        <IconButton icon={X} label="Close dialog" onClick={onClose} />
      </div>
      <h2 id="modal-title">{title}</h2>
      {children}
    </dialog>
  );
}
function SubscriptionForm({ sub, reminder, save, cancel }) {
  const [form, setForm] = useState(
    sub || {
      name: "",
      plan: "",
      price: "",
      cycle: "Monthly",
      date: "2026-09-15",
      category: "Entertainment",
      brand: "custom",
      status: "Active",
      reminder,
      notes: "",
    },
  );
  const [error, setError] = useState("");
  const change = (key, value) => setForm((f) => ({ ...f, [key]: value }));
  const selectService = (name) => {
    const known = initialSubscriptions.find((s) => s.name === name);
    setForm((f) => ({
      ...f,
      name,
      ...(known
        ? {
            brand: known.brand,
            category: known.category,
            plan: known.plan,
            price: known.price,
          }
        : { brand: "custom" }),
    }));
  };
  const submit = (e) => {
    e.preventDefault();
    if (
      !form.name.trim() ||
      !Number.isFinite(Number(form.price)) ||
      Number(form.price) <= 0 ||
      !form.date ||
      !Number.isFinite(parseDate(form.date).getTime())
    ) {
      setError("Add a service name, a valid amount, and a renewal date.");
      return;
    }
    save({
      ...form,
      id: sub?.id || crypto.randomUUID(),
      name: form.name.trim(),
      price: Number(form.price),
      reminder: Number(form.reminder),
      started: sub?.started || "2026-09-12",
    });
  };
  return (
    <form onSubmit={submit} className="subscription-form">
      <p className="modal-copy">The next step to a clearer picture.</p>
      <div className="service-picker">
        {initialSubscriptions.slice(0, 5).map((s) => (
          <button
            type="button"
            aria-label={`Choose ${s.name}`}
            aria-pressed={form.name === s.name}
            key={s.id}
            onClick={() => selectService(s.name)}
          >
            <Brand brand={s.brand} />
          </button>
        ))}
        <span>or add your own below</span>
      </div>
      <label>
        Service name
        <input
          required
          maxLength={60}
          value={form.name}
          onChange={(e) => selectService(e.target.value)}
          placeholder="e.g. Netflix"
          autoFocus
        />
      </label>
      <label>
        Plan name <span className="optional">optional</span>
        <input
          maxLength={80}
          value={form.plan}
          onChange={(e) => change("plan", e.target.value)}
          placeholder="e.g. Premium Individual"
        />
      </label>
      <div className="form-pair">
        <label>
          Amount (₹)
          <input
            type="number"
            min="0.01"
            max="10000000"
            step="0.01"
            required
            value={form.price}
            onChange={(e) => change("price", e.target.value)}
            placeholder="0.00"
          />
        </label>
        <label>
          Billing cycle
          <select
            value={form.cycle}
            onChange={(e) => change("cycle", e.target.value)}
          >
            {["Monthly", "Yearly", "Weekly"].map((x) => (
              <option key={x}>{x}</option>
            ))}
          </select>
        </label>
      </div>
      <div className="form-pair">
        <label>
          Next payment
          <input
            type="date"
            required
            value={form.date}
            onChange={(e) => change("date", e.target.value)}
          />
        </label>
        <label>
          Category
          <select
            value={form.category}
            onChange={(e) => change("category", e.target.value)}
          >
            {categories.map((x) => (
              <option key={x}>{x}</option>
            ))}
          </select>
        </label>
      </div>
      <label>
        Remind me
        <select
          value={form.reminder}
          onChange={(e) => change("reminder", e.target.value)}
        >
          {[1, 2, 3, 7].map((d) => (
            <option key={d} value={d}>
              {d} {d === 1 ? "day" : "days"} before renewal
            </option>
          ))}
        </select>
      </label>
      <div className="trial-setting">
        <div>
          <strong>Currently on a free trial?</strong>
          <p>Keep an eye on it before the first payment.</p>
        </div>
        <Toggle
          label="Free trial"
          checked={form.status === "Trial"}
          onChange={(v) => change("status", v ? "Trial" : "Active")}
        />
      </div>
      <label>
        A little note <span className="optional">optional</span>
        <textarea
          rows={2}
          maxLength={500}
          value={form.notes}
          onChange={(e) => change("notes", e.target.value)}
          placeholder="Anything worth remembering…"
        />
      </label>
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      <div className="form-footer">
        <Button type="button" variant="secondary" onClick={cancel}>
          Cancel
        </Button>
        <Button type="submit" icon={Check}>
          {sub ? "Save changes" : "Add subscription"}
        </Button>
      </div>
    </form>
  );
}
function Detail({ sub, edit, archive }) {
  return (
    <div className="detail">
      <div className="detail-brand">
        <Brand brand={sub.brand} large />
        <div>
          <h3>{sub.name}</h3>
          <p>{sub.plan}</p>
        </div>
        <span
          className={`status-tag ${sub.status === "Archived" ? "muted" : ""}`}
        >
          {sub.status}
        </span>
      </div>
      <div className="detail-price">
        {money(sub.price)}
        <span>
          /
          {sub.cycle === "Monthly"
            ? "month"
            : sub.cycle === "Yearly"
              ? "year"
              : "week"}
        </span>
      </div>
      <div className="renewal-ticket">
        <CalendarBlank size={24} />
        <div>
          <small>NEXT RENEWAL</small>
          <strong>
            {dateLabel(sub.date, {
              day: "numeric",
              month: "long",
              year: "numeric",
            })}
          </strong>
        </div>
        <span>
          {daysUntil(sub.date) >= 0
            ? `In ${daysUntil(sub.date)} days`
            : "Date has passed"}
        </span>
      </div>
      <dl>
        <div>
          <dt>Category</dt>
          <dd>{sub.category}</dd>
        </div>
        <div>
          <dt>Billing cycle</dt>
          <dd>{sub.cycle}</dd>
        </div>
        <div>
          <dt>Monthly equivalent</dt>
          <dd>{money(monthlyValue(sub))}</dd>
        </div>
        <div>
          <dt>Reminder</dt>
          <dd>{sub.reminder} days before</dd>
        </div>
        <div>
          <dt>Tracking since</dt>
          <dd>
            {dateLabel(sub.started || "2026-09-12", {
              month: "short",
              day: "numeric",
              year: "numeric",
            })}
          </dd>
        </div>
      </dl>
      {sub.notes && (
        <div className="detail-note">
          <PencilSimple size={18} />
          <p>{sub.notes}</p>
        </div>
      )}
      <div className="detail-actions">
        <Button icon={PencilSimple} onClick={edit}>
          Edit subscription
        </Button>
        <Button
          variant="secondary"
          icon={sub.status === "Archived" ? ArrowCounterClockwise : Archive}
          onClick={archive}
        >
          {sub.status === "Archived"
            ? "Restore subscription"
            : "Archive subscription"}
        </Button>
      </div>
      <p className="detail-disclaimer">
        Budgie tracks your subscriptions. Manage billing and cancellations
        directly with your provider.
      </p>
    </div>
  );
}
function BudgetForm({ current, save }) {
  const [value, setValue] = useState(current);
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        save(Number(value));
      }}
      className="subscription-form"
    >
      <p className="modal-copy">
        Choose a monthly limit that feels right. We’ll help you see where you
        stand.
      </p>
      <label>
        Monthly budget (₹)
        <input
          type="number"
          min="1"
          max="10000000"
          step="1"
          required
          autoFocus
          value={value}
          onChange={(e) => setValue(e.target.value)}
        />
      </label>
      <Button type="submit" icon={Check}>
        Save budget
      </Button>
    </form>
  );
}
createRoot(document.getElementById("root")).render(<App />);
