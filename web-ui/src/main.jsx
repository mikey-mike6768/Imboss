import React, { useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  ArrowUpRight, Bookmark, ChevronRight, CircleHelp, Clock3, Download,
  Moon, Music2, Play, Search, Settings2,
  Sparkles, Sun, Video, MessageCircle
} from "lucide-react";
import "./style.css";

const services = [
  { name: "Spotify", url: "https://open.spotify.com", icon: Music2, color: "#1ed760" },
  { name: "Apple Music", url: "https://music.apple.com", icon: Music2, color: "#fa5267" },
  { name: "Netflix", url: "https://www.netflix.com", icon: Video, color: "#e50914" },
  { name: "Prime Video", url: "https://www.primevideo.com", icon: Play, color: "#38a8ee" },
  { name: "Telegram", url: "https://web.telegram.org", icon: SendIcon, color: "#31a9e8" },
  { name: "WhatsApp", url: "https://web.whatsapp.com", icon: MessageCircle, color: "#28c76f" }
];
function SendIcon(props) { return <ArrowUpRight {...props}/>; }

function App() {
  const bridge = window.Imboss;
  const [dark, setDark] = useState(bridge ? bridge.isDark() : true);
  const [accent, setAccent] = useState(bridge ? bridge.getAccent() : "#91aaff");
  const [query, setQuery] = useState("");
  const [enabled, setEnabled] = useState(() => {
    try { return JSON.parse(localStorage.getItem("imboss.services") || "null") || services.map(x => x.name); }
    catch { return services.map(x => x.name); }
  });
  const visible = useMemo(() => services.filter(x => enabled.includes(x.name)), [enabled]);

  const open = (url) => bridge?.openUrl(url);
  const submit = () => query.trim() && open(query.trim());
  const toggleTheme = () => {
    const next = !dark;
    setDark(next);
    bridge?.setDark(next);
  };
  const toggleService = (name) => {
    const next = enabled.includes(name) ? enabled.filter(x => x !== name) : [...enabled, name];
    setEnabled(next);
    localStorage.setItem("imboss.services", JSON.stringify(next));
  };

  return <main className={dark ? "app dark" : "app light"} style={{"--accent": accent}}>
    <div className="ambient ambient-a"/><div className="ambient ambient-b"/>
    <header className="welcome">
      <div className="eyebrow"><span className="eyebrow-dot"/>{bridge?.isPrivate() ? "PRIVATE SESSION" : "YOUR SPACE"}</div>
      <div className="hero-line">
        <div><h1>Good to see you<span>.</span></h1><p>A calmer place for everything you do online.</p></div>
        <button className="icon-button theme-toggle" onClick={toggleTheme} aria-label="Toggle theme">{dark ? <Sun/> : <Moon/>}</button>
      </div>
    </header>

    <section className="search-card glass">
      <div className="search-symbol"><Search size={19}/></div>
      <input value={query} onChange={e=>setQuery(e.target.value)} onKeyDown={e=>e.key==="Enter"&&submit()} placeholder="Search anything or enter a website"/>
      <button className="search-go" onClick={submit}><ArrowUpRight size={18}/></button>
    </section>

    <section className="section">
      <div className="section-heading"><div><span className="eyebrow">YOUR SERVICES</span><p>Your everyday apps, one tap away</p></div>
        <button className="text-button" onClick={()=>bridge?.manageServices()}>Customize <Settings2 size={15}/></button>
      </div>
      <div className="service-grid">
        {visible.map(item => {
          const Icon = item.icon;
          return <button className="service-tile glass" key={item.name} onClick={()=>open(item.url)}>
            <span className="service-icon" style={{"--service-color":item.color}}><Icon size={21}/></span>
            <span className="service-name">{item.name}</span><ArrowUpRight className="tile-arrow" size={16}/>
          </button>;
        })}
        {visible.length===0 && <div className="empty-state glass">No shortcuts selected. Use Customize to add your apps.</div>}
      </div>
    </section>

    <section className="section quick-section">
      <div className="section-heading"><div><span className="eyebrow">QUICK ACCESS</span><p>Pick up where you left off</p></div></div>
      <div className="quick-grid">
        <button className="quick-tile glass" onClick={()=>bridge?.openBookmarks()}><span className="quick-icon"><Bookmark/></span><span><b>Bookmarks</b><small>Your saved pages</small></span><ChevronRight/></button>
        <button className="quick-tile glass" onClick={()=>bridge?.openHistory()}><span className="quick-icon"><Clock3/></span><span><b>History</b><small>Recently visited</small></span><ChevronRight/></button>
        <button className="quick-tile glass" onClick={()=>bridge?.openDownloads()}><span className="quick-icon"><Download/></span><span><b>Downloads</b><small>Files and documents</small></span><ChevronRight/></button>
      </div>
    </section>

    <footer className="footer"><span className="footer-mark">I</span><span>IMBOSS</span><i/> <span>Browse your way</span><Sparkles size={13}/></footer>
  </main>;
}
createRoot(document.getElementById("root")).render(<App/>);
