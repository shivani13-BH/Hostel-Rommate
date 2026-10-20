/* Hostel Roommate Finder – single page frontend (vanilla JS, talks to the Java REST API). */
"use strict";

const state = { token: localStorage.getItem("hrf_token"), me: null, options: null, poll: null };
const $ = (sel, root = document) => root.querySelector(sel);
const view = () => $("#view");

// ---------------------------------------------------------------- helpers
function esc(s) {
  return String(s ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}
function rupees(n) { return "₹" + Number(n).toLocaleString("en-IN"); }
function when(ms) {
  const d = new Date(ms);
  const today = new Date().toDateString() === d.toDateString();
  return today ? d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
               : d.toLocaleDateString([], { day: "numeric", month: "short" }) + " " + d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}
function toast(msg, bad = false) {
  const t = $("#toast");
  t.textContent = msg;
  t.className = "toast show" + (bad ? " bad" : "");
  clearTimeout(t._h);
  t._h = setTimeout(() => (t.className = "toast"), 3200);
}
function tier(pct) { return pct >= 75 ? "" : pct >= 50 ? "mid" : "low"; }

async function api(path, data, method) {
  const opts = { method: method || (data ? "POST" : "GET"), headers: {} };
  if (state.token) opts.headers["X-Auth-Token"] = state.token;
  if (data) {
    opts.headers["Content-Type"] = "application/x-www-form-urlencoded";
    opts.body = new URLSearchParams(data).toString();
  }
  let res;
  try { res = await fetch("/api" + path, opts); }
  catch { throw new Error("Can't reach the server. Is it still running?"); }
  const body = await res.json().catch(() => ({}));
  if (res.status === 401 && state.token) { logout(true); }
  if (!res.ok) throw new Error(body.error || "Request failed (" + res.status + ")");
  return body;
}

// ---------------------------------------------------------------- auth
function showAuth() {
  $("#shell").hidden = true;
  $("#auth").hidden = false;
}
document.querySelectorAll("[data-auth]").forEach(tab => tab.addEventListener("click", () => {
  document.querySelectorAll("[data-auth]").forEach(t => t.classList.toggle("active", t === tab));
  $("#loginForm").hidden = tab.dataset.auth !== "login";
  $("#registerForm").hidden = tab.dataset.auth !== "register";
  $("#authError").textContent = "";
}));

async function submitAuth(e, path) {
  e.preventDefault();
  const form = e.target;
  const btn = form.querySelector("button[type=submit]");
  btn.disabled = true;
  $("#authError").textContent = "";
  try {
    const res = await api(path, Object.fromEntries(new FormData(form)));
    state.token = res.token;
    localStorage.setItem("hrf_token", res.token);
    state.me = res.user;
    form.reset();
    enterApp();
  } catch (err) {
    $("#authError").textContent = err.message;
  } finally { btn.disabled = false; }
}
$("#loginForm").addEventListener("submit", e => submitAuth(e, "/login"));
$("#registerForm").addEventListener("submit", e => submitAuth(e, "/register"));

async function logout(expired) {
  if (!expired) { try { await api("/logout", {}); } catch { /* ignore */ } }
  state.token = null; state.me = null;
  localStorage.removeItem("hrf_token");
  stopPolling();
  location.hash = "";
  showAuth();
  if (expired) $("#authError").textContent = "Your session has ended. Log in again.";
}
$("#logoutBtn").addEventListener("click", () => logout(false));

// ---------------------------------------------------------------- shell & routing
const STUDENT_NAV = [["matches", "Matches"], ["profile", "My profile"], ["messages", "Messages"], ["booking", "Book a room"]];
const ADMIN_NAV = [["admin", "Overview"], ["admin-rooms", "Rooms"], ["admin-bookings", "Bookings"]];

function enterApp() {
  $("#auth").hidden = true;
  $("#shell").hidden = false;
  $("#whoName").textContent = state.me.name;
  const items = state.me.role === "ADMIN" ? ADMIN_NAV : STUDENT_NAV;
  $("#nav").innerHTML = items.map(([k, label]) => `<a href="#/${k}" data-nav="${k}">${label}</a>`).join("");
  if (!location.hash || location.hash === "#/") {
    location.hash = state.me.role === "ADMIN" ? "#/admin" : (state.me.hasProfile ? "#/matches" : "#/profile");
  } else route();
  refreshUnread();
}

function route() {
  if (!state.me) return;
  stopPolling();
  const [path, query] = location.hash.replace(/^#\//, "").split("?");
  const parts = path.split("/");
  const params = new URLSearchParams(query || "");
  document.querySelectorAll("[data-nav]").forEach(a => a.classList.toggle("active", a.dataset.nav === parts[0]));
  const isAdmin = state.me.role === "ADMIN";
  const routes = isAdmin
    ? { admin: renderAdmin, "admin-rooms": renderAdminRooms, "admin-bookings": renderAdminBookings }
    : { matches: renderMatches, profile: renderProfile, messages: () => renderMessages(parts[1]), booking: () => renderBooking(params.get("partner")) };
  const fn = routes[parts[0]] || routes[isAdmin ? "admin" : "matches"];
  view().innerHTML = `<p class="lede">Loading…</p>`;
  fn().catch(err => { view().innerHTML = `<div class="panel empty"><p class="error">${esc(err.message)}</p></div>`; });
  view().focus({ preventScroll: true });
}
window.addEventListener("hashchange", route);

function stopPolling() { if (state.poll) { clearInterval(state.poll); state.poll = null; } }

async function refreshUnread() {
  if (!state.me || state.me.role !== "STUDENT") return;
  try {
    const { conversations } = await api("/conversations");
    const n = conversations.reduce((s, c) => s + c.unread, 0);
    const link = $('[data-nav="messages"]');
    if (link) link.innerHTML = "Messages" + (n ? `<span class="badge">${n}</span>` : "");
  } catch { /* not critical */ }
}
setInterval(refreshUnread, 10000);

// ---------------------------------------------------------------- profile
const SLIDERS = [
  ["sleepSchedule", "Sleep schedule", "Early bird, asleep by 10", "Night owl, up past 2"],
  ["cleanliness", "Cleanliness", "A bit of mess is fine", "Everything in its place"],
  ["noiseTolerance", "Noise in the room", "I need quiet", "Noise doesn't bother me"],
  ["studyHabit", "How you study", "Alone, in silence", "With music or friends"],
  ["socialLevel", "Social life", "I keep to myself", "Always with people"],
  ["guestFrequency", "Guests in the room", "Rarely", "Often"],
];

async function renderProfile() {
  if (!state.options) state.options = await api("/options");
  const p = state.me.preferences || {};
  const hobbies = new Set(p.hobbies || []);
  view().innerHTML = `
    <h2>${state.me.hasProfile ? "Your lifestyle profile" : "Set up your lifestyle profile"}</h2>
    <p class="lede">Be honest — your matches are only as good as these answers. Other students see this profile, not your email.</p>
    <form id="profileForm" class="panel prefs">
      ${SLIDERS.map(([key, label, lo, hi]) => `
        <div class="slider">
          <label for="${key}">${label}</label>
          <input id="${key}" name="${key}" type="range" min="1" max="5" step="1" value="${p[key] ?? 3}">
          <div class="ends"><span>${lo}</span><span>${hi}</span></div>
        </div>`).join("")}
      <div class="row2">
        <label>Monthly rent budget (₹)
          <input name="budget" type="number" min="1000" max="50000" step="500" value="${p.budget ?? 6000}" required>
        </label>
        <label>Diet
          <select name="diet">${state.options.diets.map(d => `<option ${d === p.diet ? "selected" : ""}>${d}</option>`).join("")}</select>
        </label>
      </div>
      <label class="toggle"><input type="checkbox" name="smoker" ${p.smoker ? "checked" : ""}> I smoke</label>
      <div>
        <p style="font-weight:700;margin:0 0 .5rem">Hobbies <span class="hint">(pick any)</span></p>
        <div class="chips">${state.options.hobbies.map(h =>
          `<button type="button" class="chip" aria-pressed="${hobbies.has(h)}" data-hobby="${esc(h)}">${esc(h)}</button>`).join("")}</div>
      </div>
      <label>About me <textarea name="bio" rows="3" maxlength="300" placeholder="Anything a future roommate should know">${esc(p.bio)}</textarea></label>
      <div><button class="btn primary" type="submit">Save profile</button></div>
    </form>`;
  view().querySelectorAll(".chip").forEach(c => c.addEventListener("click", () =>
    c.setAttribute("aria-pressed", c.getAttribute("aria-pressed") !== "true")));
  $("#profileForm").addEventListener("submit", async e => {
    e.preventDefault();
    const fd = Object.fromEntries(new FormData(e.target));
    fd.smoker = e.target.smoker.checked ? "true" : "false";
    fd.hobbies = [...view().querySelectorAll('.chip[aria-pressed="true"]')].map(c => c.dataset.hobby).join(",");
    try {
      const first = !state.me.hasProfile;
      state.me = await api("/profile", fd);
      toast("Profile saved");
      if (first) location.hash = "#/matches";
    } catch (err) { toast(err.message, true); }
  });
}

// ---------------------------------------------------------------- matches
function keyTag(score, label) {
  return `<div class="keytag ${tier(score)}" aria-label="${score} percent compatible"><span class="pct">${score}%</span><span class="lbl">${esc(label)}</span></div>`;
}
function factorBars(factors) {
  return `<div class="factors">${factors.map(f => `
    <div class="factor"><span>${esc(f.label)}</span>
      <div class="bar"><span class="${tier(f.percent)}" style="width:${f.percent}%"></span></div>
      <span>${f.percent}%</span><small>${esc(f.note)}</small></div>`).join("")}</div>`;
}

async function renderMatches() {
  if (!state.me.hasProfile) {
    view().innerHTML = `<div class="panel empty"><h2>Finish your profile first</h2><p>Matches are worked out from your lifestyle answers.</p><a class="btn primary" href="#/profile">Set up profile</a></div>`;
    return;
  }
  const { matches } = await api("/matches");
  if (!matches.length) {
    view().innerHTML = `<div class="panel empty"><h2>No matches yet</h2><p>Nobody else in your wing has finished a profile. Check back soon.</p></div>`;
    return;
  }
  view().innerHTML = `
    <h2>Your top ${matches.length} roommate matches</h2>
    <p class="lede">Ranked by how closely your daily habits line up. Open “See the breakdown” to check each habit.</p>
    <div class="matches">${matches.map(m => {
      const u = m.user, p = u.preferences;
      return `<article class="match">
        ${keyTag(m.score, m.label)}
        <div>
          <h3>${esc(u.name)}</h3>
          <p class="meta">${esc(u.course)}, year ${u.year} • ${esc(p.diet)} • budget ${rupees(p.budget)}</p>
          ${p.bio ? `<p>“${esc(p.bio)}”</p>` : ""}
          <p class="why"><b>You line up on:</b> ${m.strengths.map(esc).join(" and ")}</p>
          ${m.watchOut ? `<p class="watch"><b>Talk about:</b> ${esc(m.watchOut)}</p>` : ""}
          <details><summary>See the breakdown</summary>${factorBars(m.factors)}</details>
          <div class="actions">
            <a class="btn primary" href="#/messages/${u.id}">Message ${esc(u.name.split(" ")[0])}</a>
            <a class="btn" href="#/booking?partner=${u.id}">Book a room together</a>
          </div>
        </div>
      </article>`;
    }).join("")}</div>`;
}

// ---------------------------------------------------------------- messages
async function renderMessages(withId) {
  view().innerHTML = `
    <h2>Messages</h2>
    <div class="chat">
      <div class="convos" id="convos"></div>
      <section class="thread" id="thread">
        <div class="empty"><p>Pick a conversation, or message someone from your <a href="#/matches">matches</a>.</p></div>
      </section>
    </div>`;
  async function drawConvos() {
    const { conversations } = await api("/conversations");
    const box = $("#convos");
    if (!box) return;
    box.innerHTML = conversations.length ? conversations.map(c => `
      <button class="convo ${String(c.user.id) === withId ? "active" : ""}" data-id="${c.user.id}">
        <b>${esc(c.user.name)} ${c.unread && String(c.user.id) !== withId ? `<span class="status PENDING">${c.unread} new</span>` : ""}</b>
        <span>${esc(c.lastMessage.text)}</span>
      </button>`).join("") : `<p class="hint" style="padding:1rem">No conversations yet. Start one from your matches.</p>`;
    box.querySelectorAll(".convo").forEach(b => b.addEventListener("click", () => (location.hash = "#/messages/" + b.dataset.id)));
  }
  await drawConvos();
  if (!withId) return;

  const thread = $("#thread");
  let lastCount = -1;
  async function load() {
    const data = await api("/messages/" + withId);
    if (lastCount === -1) {
      thread.innerHTML = `
        <div class="thread-head"><div><b>${esc(data.user.name)}</b><div class="hint">${esc(data.user.course)}</div></div>
          <a class="btn small" href="#/booking?partner=${data.user.id}">Book a room together</a></div>
        <div class="bubbles" id="bubbles"></div>
        <form class="composer" id="composer"><input name="text" maxlength="1000" placeholder="Write a message" autocomplete="off" required>
          <button class="btn primary" type="submit">Send</button></form>`;
      $("#composer").addEventListener("submit", async e => {
        e.preventDefault();
        const input = e.target.text;
        if (!input.value.trim()) return;
        try { await api("/messages/" + withId, { text: input.value }); input.value = ""; await load(); }
        catch (err) { toast(err.message, true); }
      });
      $("#composer input").focus();
    }
    if (data.messages.length !== lastCount) {
      lastCount = data.messages.length;
      const box = $("#bubbles");
      box.innerHTML = data.messages.length ? data.messages.map(m => `
        <div class="bubble ${m.fromId === state.me.id ? "mine" : ""}">${esc(m.text)}<time>${when(m.sentAt)}</time></div>`).join("")
        : `<p class="hint">Say hello — ask about their routine, or what they'd want in a roommate.</p>`;
      box.scrollTop = box.scrollHeight;
      refreshUnread();
      drawConvos().catch(() => {});
    }
  }
  try { await load(); } catch (err) { thread.innerHTML = `<div class="empty"><p class="error">${esc(err.message)}</p></div>`; return; }
  state.poll = setInterval(() => load().catch(() => {}), 4000);
}

// ---------------------------------------------------------------- booking
async function renderBooking(partnerId) {
  if (!state.me.hasProfile) {
    view().innerHTML = `<div class="panel empty"><h2>Finish your profile first</h2><p>You need a profile before you can book a room.</p><a class="btn primary" href="#/profile">Set up profile</a></div>`;
    return;
  }
  const [{ bookings }, { rooms }, { matches }] = await Promise.all([api("/bookings"), api("/rooms"), api("/matches")]);
  const active = bookings.find(b => b.status === "PENDING" || b.status === "CONFIRMED");

  // Partner options: top matches, plus the partner from the link if they aren't in the top 5.
  const candidates = matches.map(m => ({ id: m.user.id, name: m.user.name, score: m.score }));
  if (partnerId && !candidates.some(c => String(c.id) === partnerId)) {
    try {
      const r = await api("/users/" + partnerId);
      candidates.unshift({ id: r.user.id, name: r.user.name, score: r.match ? r.match.score : null });
    } catch { /* ignore bad id */ }
  }

  const bookingList = bookings.length ? bookings.map(b => {
    const other = b.youAreRequester ? b.partnerName : b.requesterName;
    let buttons = "";
    if (b.status === "PENDING" && !b.youAreRequester)
      buttons = `<button class="btn primary small" data-act="accept" data-id="${b.id}">Accept</button>
                 <button class="btn small danger" data-act="decline" data-id="${b.id}">Decline</button>`;
    else if (b.status === "PENDING" || b.status === "CONFIRMED")
      buttons = `<button class="btn small danger" data-act="cancel" data-id="${b.id}">Cancel booking</button>`;
    const line = b.status === "PENDING"
      ? (b.youAreRequester ? `Waiting for ${esc(other)} to accept` : `${esc(other)} invited you to share this room`)
      : `Roommate: ${esc(other)}`;
    return `<div class="booking">
      <div><b>Room ${esc(b.room?.number)}</b> <span class="status ${b.status}">${b.status.toLowerCase()}</span>
        <div class="hint">${line} • ${b.compatibility}% compatible • ${rupees(b.room?.monthlyRent)} each per month</div></div>
      <div class="actions" style="margin:0">${buttons}</div></div>`;
  }).join("") : `<p class="hint">No bookings yet.</p>`;

  view().innerHTML = `
    <h2>Book a room</h2>
    <p class="lede">Choose your roommate and a free room. The room is held for you both until your roommate accepts.</p>
    <section class="panel" style="margin-bottom:1.5rem"><h3>Your bookings</h3>${bookingList}</section>
    ${active ? `<div class="panel empty"><p>${active.status === "CONFIRMED"
      ? "Your room is booked. To change rooms, cancel the booking above first."
      : "You have a booking request waiting. You can book another room once it's accepted, declined or cancelled."}</p></div>` : `
    <form id="bookForm" class="steps">
      <section class="step">
        <h3>Choose your roommate</h3>
        ${candidates.length ? `<select name="partnerId" required>
          ${candidates.map(c => `<option value="${c.id}" ${String(c.id) === partnerId ? "selected" : ""}>${esc(c.name)}${c.score != null ? " — " + c.score + "% match" : ""}</option>`).join("")}
        </select>` : `<p class="hint">No available students in your wing yet.</p>`}
      </section>
      <section class="step">
        <h3>Pick a room</h3>
        <label style="max-width:16rem">Highest rent you'll pay (₹)
          <input id="maxRent" type="number" step="500" min="1000" value="${state.me.preferences.budget}"></label>
        <div class="rooms" id="rooms"></div>
        <input type="hidden" name="roomId" required>
      </section>
      <section class="step">
        <h3>Send the request</h3>
        <p class="hint" id="summary">Choose a room above.</p>
        <button class="btn primary" type="submit" id="sendBtn" disabled>Send booking request</button>
      </section>
    </form>`}`;

  view().querySelectorAll("[data-act]").forEach(btn => btn.addEventListener("click", async () => {
    if (btn.dataset.act !== "accept" && !confirm("Are you sure? This frees the room for others.")) return;
    try {
      await api(`/bookings/${btn.dataset.id}/${btn.dataset.act}`, {});
      toast({ accept: "Booking confirmed", decline: "Request declined", cancel: "Booking cancelled" }[btn.dataset.act]);
      renderBooking(partnerId);
    } catch (err) { toast(err.message, true); }
  }));

  const form = $("#bookForm");
  if (!form) return;
  function drawRooms() {
    const max = Number($("#maxRent").value) || Infinity;
    const list = rooms.filter(r => r.monthlyRent <= max);
    $("#rooms").innerHTML = list.length ? list.map(r => `
      <button type="button" class="room" data-room="${r.id}" aria-pressed="${form.roomId.value === String(r.id)}" ${r.available ? "" : "disabled"}>
        <b>${esc(r.number)}</b><small>Floor ${r.floor} • ${rupees(r.monthlyRent)} each / month</small>
        <small>${esc(r.amenities)}</small><span class="status ${r.available ? "Available" : "Reserved"}">${r.available ? "Available" : "Taken"}</span>
      </button>`).join("") : `<p class="hint">No rooms at or under this rent. Raise the limit to see more.</p>`;
    $("#rooms").querySelectorAll(".room").forEach(b => b.addEventListener("click", () => {
      form.roomId.value = b.dataset.room;
      drawRooms();
      updateSummary();
    }));
  }
  function updateSummary() {
    const room = rooms.find(r => String(r.id) === form.roomId.value);
    const sel = form.partnerId;
    if (!room || !sel) { $("#sendBtn").disabled = true; return; }
    $("#summary").textContent = `Room ${room.number} with ${sel.options[sel.selectedIndex].text.split(" — ")[0]}, ${rupees(room.monthlyRent)} each per month.`;
    $("#sendBtn").disabled = false;
  }
  $("#maxRent").addEventListener("input", drawRooms);
  if (form.partnerId) form.partnerId.addEventListener("change", updateSummary);
  drawRooms();
  form.addEventListener("submit", async e => {
    e.preventDefault();
    try {
      await api("/bookings", { roomId: form.roomId.value, partnerId: form.partnerId.value });
      toast("Booking request sent");
      renderBooking(partnerId);
    } catch (err) { toast(err.message, true); }
  });
}

// ---------------------------------------------------------------- admin
async function renderAdmin() {
  const s = await api("/admin/stats");
  const b = s.bookings;
  view().innerHTML = `
    <h2>Hostel overview</h2>
    <p class="lede">Live numbers from student profiles, rooms and bookings.</p>
    <div class="stats">
      <div class="stat"><b>${s.students}</b>students registered</div>
      <div class="stat"><b>${s.profilesCompleted}</b>profiles completed</div>
      <div class="stat"><b>${s.availableRooms} / ${s.activeRooms}</b>rooms free</div>
      <div class="stat"><b>${b.CONFIRMED}</b>confirmed bookings</div>
      <div class="stat"><b>${b.PENDING}</b>waiting for roommate</div>
      <div class="stat"><b>${s.averageBestMatch}%</b>average best match</div>
      <div class="stat"><b>${s.messages}</b>messages sent</div>
      <div class="stat"><b>${b.DECLINED + b.CANCELLED}</b>declined or cancelled</div>
    </div>`;
}

async function renderAdminRooms() {
  const { rooms } = await api("/admin/rooms");
  view().innerHTML = `
    <h2>Rooms</h2>
    <form id="roomForm" class="panel" style="margin-bottom:1.5rem">
      <h3>Add a room</h3>
      <div class="room-form">
        <label>Block <input name="block" required maxlength="5" placeholder="A"></label>
        <label>Room number <input name="number" required placeholder="A-301"></label>
        <label>Floor <input name="floor" type="number" min="0" max="20" value="1"></label>
        <label>Wing <select name="gender"><option>Female</option><option>Male</option></select></label>
        <label>Rent per student (₹) <input name="monthlyRent" type="number" min="500" step="500" value="5000"></label>
        <label class="wide">Amenities <input name="amenities" placeholder="Attached bath, Balcony"></label>
        <button class="btn primary" type="submit">Add room</button>
      </div>
    </form>
    <div class="table-wrap"><table>
      <thead><tr><th>Room</th><th>Wing</th><th>Rent</th><th>Status</th><th>Occupants</th><th></th></tr></thead>
      <tbody>${rooms.map(r => `<tr>
        <td><b>${esc(r.number)}</b><div class="hint">${esc(r.amenities)}</div></td>
        <td>${esc(r.gender)}</td><td>${rupees(r.monthlyRent)}</td>
        <td><span class="status ${r.status.split(" ")[0]}">${esc(r.status)}</span></td>
        <td>${r.occupants ? esc(r.occupants.requesterName) + " & " + esc(r.occupants.partnerName) : "—"}</td>
        <td><button class="btn small" data-toggle="${r.id}">${r.active ? "Take out of service" : "Put back in service"}</button></td>
      </tr>`).join("")}</tbody></table>
    </div>`;
  view().querySelectorAll("[data-toggle]").forEach(b => b.addEventListener("click", async () => {
    try { await api(`/admin/rooms/${b.dataset.toggle}/toggle`, {}); renderAdminRooms(); }
    catch (err) { toast(err.message, true); }
  }));
  $("#roomForm").addEventListener("submit", async e => {
    e.preventDefault();
    try { await api("/admin/rooms", Object.fromEntries(new FormData(e.target))); toast("Room added"); renderAdminRooms(); }
    catch (err) { toast(err.message, true); }
  });
}

async function renderAdminBookings() {
  const { bookings } = await api("/admin/bookings");
  view().innerHTML = `
    <h2>All bookings</h2>
    ${bookings.length ? `<div class="table-wrap"><table>
      <thead><tr><th>Room</th><th>Requested by</th><th>Roommate</th><th>Match</th><th>Status</th><th>Last update</th></tr></thead>
      <tbody>${bookings.map(b => `<tr><td>${esc(b.room?.number)}</td><td>${esc(b.requesterName)}</td><td>${esc(b.partnerName)}</td>
        <td>${b.compatibility}%</td><td><span class="status ${b.status}">${b.status.toLowerCase()}</span></td><td>${when(b.updatedAt)}</td></tr>`).join("")}
      </tbody></table></div>` : `<div class="panel empty"><p>No bookings yet. They appear here as soon as students send requests.</p></div>`}`;
}

// ---------------------------------------------------------------- start
(async function start() {
  if (!state.token) return showAuth();
  try { state.me = await api("/me"); enterApp(); }
  catch { localStorage.removeItem("hrf_token"); state.token = null; showAuth(); }
})();
