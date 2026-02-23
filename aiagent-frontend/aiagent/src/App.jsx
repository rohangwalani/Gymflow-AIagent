import { useState, useEffect, useRef } from "react";
import LandingPage from "./LandingPage";
import About from "./About";
import { Send, Play, Activity, RefreshCw, Mic, MicOff, Volume2, VolumeX } from 'lucide-react'; 
import "./App.css";

// --- API CONFIGURATION ---
// Updated to your live Render Production URL for the standard workout pool
const API_BASE_URL = import.meta.env.VITE_API_URL || "https://gymflow-backend.onrender.com";

// Updated to your live Render AI Agent endpoint (Spring Boot /api/v1/agent/chat)
const AGENT_API_URL = import.meta.env.VITE_AGENT_API_URL || "https://gymflow-aiagent.onrender.com/api/v1/agent/chat";

// --- STANDARD GYMFLOW THEME & DATA ---
const ANATOMY_THEME = {
  'Upper Chest': '#ff00d4', 'Middle Chest': '#00f3ff', 'Lower Chest': '#00ff8c',
  'Lats': '#008cff', 'Upper Back': '#ff8c00', 'Lower Back': '#ffcf00',
  'Front Delts': '#ff5e00', 'Side Delts': '#ae00ff', 'Rear Delts': '#4dff88', 'Traps': '#ffffff',
  'Long Head': '#00ffd0', 'Short Head': '#ff0055', 'Brachialis': '#ffd000',
  'Lateral Head': '#00ffcc', 'Medial Head': '#80ff00',
  'Quads': '#ff3e3e', 'Hamstrings': '#3effa2', 'Calves': '#ffaa3e',
  'Flexors': '#ff7700', 'Extensors': '#ffcc00', 'Grip Strength': '#00ffaa',
  'Upper Abs': '#00f3ff', 'Lower Abs': '#ff00ff', 'Obliques': '#ae00ff', 'Core': '#ffffff',
  'Endurance': '#00ff8c', 'Overall': '#555555'
};

const SPLIT_MUSCLES = {
  PPL: { PUSH: ["CHEST", "SHOULDERS", "TRICEPS"], PULL: ["BACK", "BICEPS"], LEGS: ["LEGS"], ABS: ["ABS"], FOREARMS: ["FOREARMS"], CARDIO: ["CARDIO"] },
  BRO: { CHEST_TRI: ["CHEST", "TRICEPS"], BACK_BI: ["BACK", "BICEPS"], SHOULDERS: ["SHOULDERS"], ARMS: ["BICEPS", "TRICEPS"], LEGS: ["LEGS"], ABS: ["ABS"], FOREARMS: ["FOREARMS"], CARDIO: ["CARDIO"] },
  PPLUL: { PUSH: ["CHEST", "SHOULDERS", "TRICEPS"], PULL: ["BACK", "BICEPS"], LEGS: ["LEGS"], UPPER: ["CHEST", "BACK", "SHOULDERS", "BICEPS", "TRICEPS"], LOWER: ["LEGS", "ABS"], ABS: ["ABS"], FOREARMS: ["FOREARMS"], CARDIO: ["CARDIO"] },
  SINGLE: { DEFAULT: [] } 
};

// --- ADVANCED AI AGENT THEME ---
const ADVANCED_ANATOMY_THEME = {
  'Chest': '#ff00d4', 'Back': '#008cff', 'Shoulders': '#ff5e00', 'Biceps': '#00ffd0', 'Triceps': '#00ffcc',
  'Quads': '#ff3e3e', 'Hamstrings': '#ffaa00', 'Calves': '#aaff00', 'Glutes': '#d400ff',
  'Abs': '#00f3ff', 'Cardio': '#00ff8c', 'Rest': '#555555'
};

const getThemeColor = (exerciseName) => {
  if (!exerciseName) return ADVANCED_ANATOMY_THEME.Rest;
  const name = exerciseName.toLowerCase();
  if (name.includes("squat") || name.includes("leg press") || name.includes("extension") || name.includes("lunge") || name.includes("step-up") || name.includes("sissy")) return ADVANCED_ANATOMY_THEME.Quads;
  if (name.includes("deadlift") || name.includes("curl") || name.includes("rdl") || name.includes("good morning") || name.includes("glute") || name.includes("hip thrust") || name.includes("swing") || name.includes("pull-through")) return ADVANCED_ANATOMY_THEME.Hamstrings;
  if (name.includes("calf") || name.includes("calves") || name.includes("raise") || name.includes("donkey")) return ADVANCED_ANATOMY_THEME.Calves;
  if (name.includes("bench") || name.includes("chest") || name.includes("fly") || name.includes("pushup") || name.includes("pec") || name.includes("dip") || name.includes("svend") || name.includes("hex")) return ADVANCED_ANATOMY_THEME.Chest;
  if (name.includes("row") || name.includes("pull") || name.includes("lat") || name.includes("chin") || name.includes("hyper") || name.includes("rack pull")) return ADVANCED_ANATOMY_THEME.Back;
  if (name.includes("curl") || name.includes("bicep") || name.includes("hammer") || name.includes("waiter") || name.includes("drag")) return ADVANCED_ANATOMY_THEME.Biceps;
  if (name.includes("tricep") || name.includes("skull") || name.includes("kickback") || name.includes("jm press") || name.includes("tate") || name.includes("french") || name.includes("close grip")) return ADVANCED_ANATOMY_THEME.Triceps;
  if (name.includes("shoulder") || name.includes("overhead") || name.includes("military") || name.includes("arnold") || name.includes("raise") || name.includes("face pull") || name.includes("shrug") || name.includes("landmine")) return ADVANCED_ANATOMY_THEME.Shoulders;
  if (name.includes("crunch") || name.includes("plank") || name.includes("russian") || name.includes("leg raise") || name.includes("woodchop") || name.includes("pallof") || name.includes("vacuum") || name.includes("flag") || name.includes("sit")) return ADVANCED_ANATOMY_THEME.Abs;
  if (name.includes("run") || name.includes("sprint") || name.includes("jump") || name.includes("cardio")) return ADVANCED_ANATOMY_THEME.Cardio;
  return '#00f3ff';
};

function App() {
  // Standard GymFlow States
  const [isStarted, setIsStarted] = useState(false);
  const [isReady, setIsReady] = useState(false); 
  const [isExiting, setIsExiting] = useState(false);
  const [selectedSplit, setSelectedSplit] = useState("SINGLE"); 
  const [sessionType, setSessionType] = useState("CHEST");
  const [count, setCount] = useState(6);
  const [volumes, setVolumes] = useState({}); 
  const [exercises, setExercises] = useState([]);
  const [loading, setLoading] = useState(false);
  const [modal, setModal] = useState({ show: false, message: "" });
  const [copied, setCopied] = useState(false);
  const [showAbout, setShowAbout] = useState(false);

  // AI Agent States
  const [showAgent, setShowAgent] = useState(false);
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState("");
  const [isAgentThinking, setIsAgentThinking] = useState(false);
  const [isListening, setIsListening] = useState(false);
  const [isMuted, setIsMuted] = useState(false);
  const chatEndRef = useRef(null);

  const [userId] = useState(() => {
    const existing = localStorage.getItem("gymflow_user_id");
    if (existing) return existing;
    const newId = "user_" + Math.random().toString(36).substr(2, 9);
    localStorage.setItem("gymflow_user_id", newId);
    return newId;
  });

  useEffect(() => {
    if (showAgent) {
      chatEndRef.current?.scrollIntoView({ behavior: "smooth" });
    }
  }, [messages, showAgent, isAgentThinking]);

  // --- VOICE INPUT LOGIC ---
  const startListening = () => {
    if (!('webkitSpeechRecognition' in window)) {
      alert("Browser does not support voice. Try Chrome.");
      return;
    }
    const recognition = new window.webkitSpeechRecognition();
    recognition.continuous = false;
    recognition.lang = 'en-US';
    recognition.interimResults = false;

    recognition.onstart = () => setIsListening(true);
    recognition.onend = () => setIsListening(false);
    
    recognition.onresult = (event) => {
      const transcript = event.results[0][0].transcript;
      setInput(transcript);
    };
    recognition.start();
  };

  // --- VOICE OUTPUT LOGIC (TTS) ---
  const speakText = (text) => {
    if (isMuted || !text) return;
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = 'en-US';
    utterance.rate = 1.0; 
    utterance.pitch = 1.0;
    window.speechSynthesis.speak(utterance);
  };

  // --- AI CLOUD UPLINK (MERGED LOGIC) ---
  const processAgentMessage = async (messageText) => {
    const userMsg = { role: 'user', content: messageText };
    setMessages(prev => [...prev, userMsg]);
    setIsAgentThinking(true);

    try {
      // Security: matches internalKey in AgentController
      const response = await fetch(AGENT_API_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          userId: userId,
          message: messageText, 
          apiKey: "gymflow-secret-connect-2026" 
        })
      });

      if (!response.ok) throw new Error("Agent Offline");
      const data = await response.json();

      setMessages(prev => [...prev, { 
        role: 'assistant', 
        content: data.coach_message,
        routine: data.routine || [],
        warning_level: data.warning_level 
      }]);

      speakText(data.coach_message);

    } catch (error) {
      console.error("Uplink Error:", error);
      setMessages(prev => [...prev, { 
        role: 'system', 
        content: "CRITICAL FAILURE: Cloud Uplink Severed.",
        warning_level: "RED" 
      }]);
    } finally {
      setIsAgentThinking(false);
    }
  };

  const handleAgentFormSubmit = (e) => {
    e.preventDefault();
    if (!input.trim()) return;
    processAgentMessage(input);
    setInput("");
  };

  const handleAiReplace = (exerciseName) => {
     processAgentMessage(`Replace "${exerciseName}"`);
  };

  // --- STANDARD GYMFLOW LOGIC ---
  useEffect(() => {
    const saved = localStorage.getItem("gymflow_active_session");
    if (saved) {
      const parsed = JSON.parse(saved);
      if (Date.now() - parsed.timestamp < 10800000) {
        setExercises(parsed.exercises);
        setSessionType(parsed.sessionType);
        setSelectedSplit(parsed.selectedSplit);
        setIsStarted(true);
      }
    }
    setIsReady(true); 
  }, []);

  useEffect(() => {
    if (exercises.length > 0) {
      localStorage.setItem("gymflow_active_session", JSON.stringify({
        exercises, sessionType, selectedSplit, timestamp: Date.now()
      }));
    }
  }, [exercises, sessionType, selectedSplit]);

  const getAnatomyColor = (name) => ANATOMY_THEME[name] || '#00f3ff';
  const cleanAnatomyName = (sub) => {
    if (!sub) return "";
    let name = sub.replace(/Isolation|Fly|Row|Pull|Press|Overall/gi, '').trim();
    if (name.toLowerCase() === 'chest') return 'Middle Chest';
    if (name.toLowerCase() === 'back') return 'Upper Back';
    return name;
  };

  const handleStart = (splitType) => {
    setIsExiting(true);
    setSelectedSplit(splitType);
    if (splitType === "PPL" || splitType === "PPLUL") setSessionType("PUSH");
    else if (splitType === "BRO") setSessionType("CHEST_TRI");
    else setSessionType("CHEST");
    setTimeout(() => { setIsStarted(true); setIsExiting(false); }, 600); 
  };

  const handleNavigateHome = () => {
    setIsExiting(true);
    setTimeout(() => {
      localStorage.removeItem("gymflow_active_session");
      setExercises([]);
      setIsStarted(false);
      setIsExiting(false);
    }, 600);
  };

  const updateVolume = (m, val) => setVolumes(prev => ({ ...prev, [m]: parseInt(val) || 0 }));
  const handleSubmit = (e) => { e.preventDefault(); fetchWorkout(); };

  const fetchWorkout = async () => {
    setLoading(true);
    setExercises([]); 
    let masterWorkout = [];
    let targets = selectedSplit === "SINGLE" ? [sessionType] : [...(SPLIT_MUSCLES[selectedSplit][sessionType] || [])];

    try {
      for (const target of targets) {
        let vol = 0;
        const stateVal = selectedSplit === "SINGLE" ? count : volumes[target];
        if (stateVal === "0" || stateVal === 0) continue; 
        if (selectedSplit === "SINGLE") vol = parseInt(count) || 6; 
        else {
           if (parseInt(stateVal) > 0) vol = parseInt(stateVal);
           else {
             if (["CHEST", "BACK"].includes(target) && ["CHEST_TRI", "BACK_BI"].includes(sessionType)) vol = 5;
             else if (sessionType === "LEGS") vol = 6;
             else if (sessionType === "ABS") vol = 4;
             else vol = 3;
           }
        }
        const idList = masterWorkout.map(ex => ex.id);
        const params = new URLSearchParams({ userId: userId, muscle: target.toUpperCase(), count: "50" });
        if (idList.length > 0) params.append("excludedIds", idList.join(','));
        const response = await fetch(`${API_BASE_URL}/api/workout/muscle-exercises?${params.toString()}`);
        if (!response.ok) { setModal({ show: true, message: `POOL_ERROR: ${target} pool unavailable.` }); setLoading(false); return; }
        let data = await response.json();
        
        // Filtering logic preserved for specific groups
        if (target === "CHEST") { let lowerFound = false; data = data.filter(ex => { if (ex.muscleSubGroup?.toLowerCase() === "lower chest") { if (vol <= 6) { if (lowerFound) return false; lowerFound = true; return true; } } return true; }); }
        if (target === "BACK") { let lowerBackFound = false; data = data.filter(ex => { if (ex.muscleSubGroup?.toLowerCase() === "lower back") { if (vol <= 8) { if (lowerBackFound) return false; lowerBackFound = true; return true; } } return true; }); }
        if (target === "LEGS") { let calfFound = false; data = data.filter(ex => { if (ex.muscleSubGroup?.toLowerCase() === "calves") { if (calfFound) return false; calfFound = true; return true; } return true; }); }
        
        masterWorkout = [...masterWorkout, ...data.slice(0, vol)];
      }
      setExercises(masterWorkout);
    } catch (e) { setModal({ show: true, message: "CRITICAL_SYSTEM_OFFLINE" }); } finally { setLoading(false); }
  };

  const handleStandardReplace = async (ex, index) => {
    const idList = exercises.map(item => item.id);
    try {
        const params = new URLSearchParams({ userId: userId, excludedIds: idList.join(','), isIsolation: (ex.isIsolation === true).toString() });
        const response = await fetch(`${API_BASE_URL}/api/workout/replace/${ex.id}?${params.toString()}`);
        if (response.ok) { const newEx = await response.json(); const updated = [...exercises]; updated[index] = newEx; setExercises(updated); }
    } catch (e) { setModal({ show: true, message: "REPLACEMENT_ENGINE_ERROR" }); }
  };

  const copyToClipboard = () => {
    const displayed = exercises.filter(ex => !(ex.muscleSubGroup?.toLowerCase() === "overall" && exercises.length < 5));
    const text = displayed.map((ex, i) => `${i + 1}. ${ex.name} [Target: ${ex.muscleSubGroup}]`).join('\n');
    navigator.clipboard.writeText(`GYMFLOW PROTOCOL - ${sessionType}:\n\n${text}`);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  if (!isReady) return <div className="min-h-screen bg-[#050505]" />; 

  // --- AI AGENT OVERLAY ---
  if (showAgent) {
    return (
      <div className="fixed inset-0 z-[99999] bg-[#050505] text-white flex flex-col font-mono animate-enter overflow-hidden">
         <div className="bg-glow opacity-50" />
         <div className="grid-overlay opacity-30" />

         <header className="relative z-50 flex flex-col sm:flex-row justify-between items-center py-6 sm:py-8 px-6 sm:px-10 border-b border-white/10 gap-4 bg-[#050505]/95 backdrop-blur-xl">
           <div className="flex items-center gap-6">
             <button onClick={() => setShowAgent(false)} className="group relative flex items-center justify-center w-11 h-11 rounded-full border border-white/10 bg-white/5 hover:border-[#00f3ff] transition-all duration-300">
               <svg viewBox="0 0 24 24" className="w-5 h-5 stroke-gray-400 group-hover:stroke-[#00f3ff] transition-all" fill="none" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M15 18l-6-6 6-6" /></svg>
             </button>
             <h1 className="text-base sm:text-lg font-black tracking-[4px] uppercase cursor-pointer">
               GYM<span className="text-[#00f3ff]">FLOW</span> <span className="text-gray-700">VOICE</span>
             </h1>
           </div>
           
           <div className="flex items-center gap-6">
             <button onClick={() => setIsMuted(!isMuted)} className="text-gray-500 hover:text-white transition-colors" title={isMuted ? "Unmute AI" : "Mute AI"}>
                {isMuted ? <VolumeX className="w-5 h-5"/> : <Volume2 className="w-5 h-5 text-[#00f3ff]"/>}
             </button>
             <div className="flex flex-col items-end border-l border-white/10 pl-4 sm:pl-6">
               <span className="text-[0.5rem] font-black uppercase tracking-[2px] text-gray-600">System Status</span>
               <span className="text-[0.7rem] font-black text-[#00f3ff] uppercase tracking-[1px] flex items-center gap-2">
                 {isAgentThinking ? 'CALCULATING...' : 'ONLINE'} 
                 <span className={`w-1.5 h-1.5 rounded-full ${isAgentThinking ? 'bg-yellow-400 animate-pulse' : 'bg-[#00f3ff]'}`}></span>
               </span>
             </div>
           </div>
         </header>

         <main className="flex-1 overflow-y-auto p-6 sm:p-10 flex flex-col gap-10 pb-40 relative z-10 scroll-smooth">
            {messages.length === 0 && (
                <div className="flex flex-col items-center justify-center py-20 opacity-30 mt-20">
                    <Activity className="w-20 h-20 text-[#00f3ff] mb-6" strokeWidth={1} />
                    <p className="text-[0.6rem] font-black uppercase tracking-[4px]">SAY "GIVE ME A CHEST WORKOUT"</p>
                </div>
            )}
            {messages.map((msg, idx) => (
                <div key={idx} className={`flex flex-col ${msg.role === 'user' ? 'items-end' : 'items-start'} animate-enter w-full`}>
                    {msg.role === 'user' ? (
                        <div className="max-w-[80%] bg-white/5 border border-white/10 px-6 py-4 rounded-2xl rounded-tr-sm text-sm font-medium text-gray-200">
                            {msg.content}
                        </div>
                    ) : (
                        <div className="w-full flex flex-col gap-8">
                            <div className={`self-start max-w-[90%] md:max-w-[70%] bg-[#0a0a0a] border ${msg.warning_level === 'RED' ? 'border-red-500/50' : 'border-[#00f3ff]/30'} p-6 rounded-[24px] rounded-tl-sm relative overflow-hidden shadow-2xl`}>
                                <div className={`absolute top-0 left-0 w-1 h-full ${msg.warning_level === 'RED' ? 'bg-red-500' : 'bg-[#00f3ff]'}`}></div>
                                <div className="text-[0.5rem] font-black uppercase tracking-[2px] text-gray-500 mb-2">AI COACH</div>
                                <p className="text-gray-300 text-sm leading-relaxed">{msg.content}</p>
                            </div>
                            {msg.routine && msg.routine.length > 0 && (
                                <div className="grid grid-cols-1 md:grid-cols-2 gap-6 w-full">
                                    {msg.routine.map((ex, i) => {
                                        const themeColor = getThemeColor(ex.exercise);
                                        return (
                                            <div key={i} className="workout-card group/card" style={{"--subgroup-color": themeColor}}>
                                                <div className="card-accent"></div>
                                                <div className="flex-1 z-10 flex flex-col justify-center">
                                                    <div className="flex items-center gap-3 mb-2">
                                                        <span className="text-[0.5rem] font-black uppercase tracking-[2px]" style={{ color: themeColor }}>
                                                            {ex.sets} SETS X {ex.reps} REPS
                                                        </span>
                                                    </div>
                                                    <h3 className="text-2xl font-black text-white mb-2 leading-tight">
                                                        {ex.exercise}
                                                    </h3>
                                                    <p className="text-gray-500 text-xs font-medium leading-relaxed border-l border-white/10 pl-3">
                                                        "{ex.notes}"
                                                    </p>
                                                </div>
                                                <div className="flex flex-col gap-3 justify-center opacity-40 group-hover/card:opacity-100 transition-opacity">
                                                    <button onClick={() => handleAiReplace(ex.exercise)} title="Replace" className="w-10 h-10 rounded-full border border-white/10 flex items-center justify-center hover:bg-white hover:text-black transition-all bg-[#050505] group/btn">
                                                      <RefreshCw className="w-4 h-4 group-hover/btn:rotate-180 transition-transform duration-500" />
                                                    </button>
                                                </div>
                                            </div>
                                        )
                                    })}
                                </div>
                            )}
                        </div>
                    )}
                </div>
            ))}
            <div ref={chatEndRef} />
         </main>

         <div className="fixed bottom-0 left-0 w-full input-glass p-4 sm:p-6 z-[100]">
             <div className="max-w-[1200px] mx-auto">
                 <form onSubmit={handleAgentFormSubmit} className="flex gap-4 items-center">
                     <div className="flex-1 relative group flex items-center gap-2">
                         <button 
                             type="button" 
                             onClick={startListening}
                             className={`h-14 w-14 rounded-xl flex items-center justify-center border transition-all ${isListening ? 'bg-red-500/20 border-red-500 text-red-500 animate-pulse' : 'bg-[#0a0a0a] border-white/10 text-gray-400 hover:text-white hover:border-[#00f3ff]'}`}
                         >
                             {isListening ? <MicOff className="w-5 h-5" /> : <Mic className="w-5 h-5" />}
                         </button>
                         <div className="flex-1 relative">
                              <div className="absolute -inset-0.5 bg-gradient-to-r from-[#00f3ff] to-[#ff00d4] rounded-xl opacity-20 group-hover:opacity-40 blur transition duration-500"></div>
                              <input 
                                  value={input}
                                  onChange={(e) => setInput(e.target.value)}
                                  placeholder={isListening ? "Listening..." : "TYPE OR SPEAK..."}
                                  className="relative w-full bg-[#0a0a0a] border border-white/10 text-white text-sm font-bold uppercase tracking-wider py-4 px-6 rounded-xl outline-none focus:border-[#00f3ff]/50 placeholder-gray-700"
                                  disabled={isAgentThinking}
                               />
                         </div>
                     </div>
                     <button type="submit" disabled={isAgentThinking || !input.trim()} className="bg-[#00f3ff] hover:bg-white text-black h-14 w-14 sm:w-auto sm:px-8 rounded-xl font-black uppercase transition-all flex items-center justify-center gap-2 disabled:opacity-50">
                         {isAgentThinking ? <Activity className="w-5 h-5 animate-spin" /> : <Send className="w-4 h-4" strokeWidth={3} />}
                     </button>
                 </form>
             </div>
         </div>
      </div>
    );
  }

  // --- STANDARD GENERATOR UI ---
  return (
    <div className={`min-h-screen bg-[#050505] text-white selection:bg-cyan-500 relative ${isExiting ? "fade-out-exit" : ""}`}>
      <div className="bg-glow" />
      <div className="grid-overlay" />

      {/* AI Agent Button */}
      <div className="fixed bottom-6 left-6 z-[9999] animate-enter">
          <button 
            onClick={() => setShowAgent(true)} 
            className="group relative px-12 py-4 bg-transparent border border-[#00f3ff]/20 hover:border-[#00f3ff] rounded-full transition-all duration-500 shadow-xl flex items-center gap-3"
          >
              <div className="absolute inset-0 bg-[#00f3ff]/5 rounded-full blur-xl opacity-0 group-hover:opacity-100 transition-opacity"></div>
              <Mic className="w-4 h-4 text-[#00f3ff] relative z-10" />
              <span className="relative text-[0.8rem] font-black uppercase tracking-[4px] text-[#00f3ff] group-hover:text-white transition-colors">
                  TALK WITH AGENT
              </span>
          </button>
      </div>

      {(showAbout || modal.show) && (
        <div className="fixed inset-0 z-[10000] flex items-center justify-center bg-black/90 backdrop-blur-xl p-4 animate-enter">
          <div className="relative max-w-6xl w-full max-h-[90vh] overflow-y-auto bg-[#0a0a0a] border border-white/10 rounded-[32px] p-8 sm:p-14 shadow-[0_0_50px_rgba(0,0,0,1)]">
            <button onClick={() => { setShowAbout(false); setModal({ show: false, message: "" }); }} className="absolute top-8 right-8 w-10 h-10 rounded-full bg-white/5 border border-white/10 flex items-center justify-center hover:bg-[#ff0055] transition-all z-50 text-white">✕</button>
            {showAbout ? <About /> : (
              <div className="text-center py-10">
                <div className="text-[#ff0055] text-5xl mb-6">⚠</div>
                <h3 className="text-white font-black tracking-widest uppercase mb-4 text-xs">Engine Warning</h3>
                <p className="text-gray-400 text-sm font-bold uppercase tracking-wider mb-8">{modal.message}</p>
                <button className="bg-[#00f3ff] text-black px-10 py-3 rounded-xl font-black" onClick={() => setModal({ show: false, message: "" })}>Modify Protocol</button>
              </div>
            )}
          </div>
        </div>
      )}

      {!isStarted ? (
        <div className={isExiting ? "fade-out-exit" : ""}>
          <LandingPage onStart={handleStart} />
        </div>
      ) : (
        <div className="relative z-10 max-w-[1200px] mx-auto px-4 sm:px-8 pb-10 animate-enter">
          <header className="flex flex-col sm:flex-row justify-between items-center py-6 sm:py-10 border-b border-white/5 mb-10 gap-4">
            <div className="flex items-center gap-6">
             <button onClick={handleNavigateHome} className="group relative flex items-center justify-center w-11 h-11 rounded-full border border-white/10 bg-white/5 hover:border-[#00f3ff] transition-all duration-300">
               <svg viewBox="0 0 24 24" className="w-5 h-5 stroke-gray-400 hover:stroke-[#00f3ff] transition-all" fill="none" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M15 18l-6-6 6-6" /></svg>
             </button>
             <h1 className="logo text-base sm:text-lg font-black tracking-[4px] uppercase cursor-pointer" onClick={handleNavigateHome}>
               GYM<span className="text-[#00f3ff]">FLOW</span>
             </h1>
            </div>
            <div className="flex items-center gap-6">
              <div className="flex flex-col items-end border-l border-white/10 pl-4 sm:pl-6">
                <span className="text-[0.5rem] font-black uppercase tracking-[2px] text-gray-600">Active Workout</span>
                <span className="text-[0.7rem] font-black text-[#00f3ff] uppercase tracking-[1px]">{selectedSplit} // {sessionType}</span>
              </div>
            </div>
          </header>

          <section className="bg-[#0a0a0a] border border-white/5 p-6 sm:p-12 rounded-[24px] shadow-2xl relative mb-12">
            <form onSubmit={handleSubmit} className="flex flex-col gap-8">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
                <div className="flex flex-col gap-3">
                  <label className="text-[0.6rem] font-black text-[#00f3ff] uppercase tracking-widest">Muscle Group</label>
                  <div className="relative">
                     <select className="w-full bg-transparent border-b-2 border-[#222] text-white text-xl font-black py-2 outline-none appearance-none" 
                             value={sessionType} onChange={(e) => setSessionType(e.target.value)}>
                       {selectedSplit === "SINGLE" && ["CHEST", "BACK", "SHOULDERS", "LEGS", "BICEPS", "TRICEPS", "FOREARMS", "ABS", "CARDIO"].map(m => <option key={m} value={m} className="bg-[#111]">{m}</option>)}
                       {selectedSplit === "PPL" && ["PUSH", "PULL", "LEGS", "ABS", "FOREARMS", "CARDIO"].map(m => <option key={m} value={m} className="bg-[#111]">{m}</option>)}
                       {selectedSplit === "BRO" && ["CHEST_TRI", "BACK_BI", "SHOULDERS", "ARMS", "LEGS", "ABS", "FOREARMS", "CARDIO"].map(m => <option key={m} value={m} className="bg-[#111]">{m.replace('_', ' & ')}</option>)}
                       {selectedSplit === "PPLUL" && ["PUSH", "PULL", "LEGS", "UPPER", "LOWER", "ABS", "FOREARMS", "CARDIO"].map(m => <option key={m} value={m} className="bg-[#111]">{m}</option>)}
                     </select>
                  </div>
                </div>
                <button type="submit" className="bg-[#00f3ff] text-black h-14 sm:h-16 rounded-xl font-black uppercase hover:bg-white transition-all md:mt-auto" disabled={loading}>{loading ? "CALCULATING..." : "EXECUTE"}</button>
              </div>
            </form>
          </section>

          {exercises.length > 0 && !loading && (
            <div className="flex flex-col gap-6">
              {exercises.map((ex, i) => (
                <div key={i} className="workout-card flex-col sm:flex-row" style={{"--delay": i, "--subgroup-color": getAnatomyColor(cleanAnatomyName(ex.muscleSubGroup))}}>
                  <div className="card-accent" style={{background: "var(--subgroup-color)"}}></div>
                  <div className="flex-1">
                    <div className="flex items-center gap-3 mb-2">
                      <span className="text-[0.5rem] font-black uppercase tracking-[2px]" style={{color: "var(--subgroup-color)"}}>
                        {cleanAnatomyName(ex.muscleSubGroup)}
                      </span>
                    </div>
                    <h3 className="text-xl sm:text-3xl font-black mt-1 mb-2">{ex.name}</h3>
                    <p className="text-gray-500 text-xs sm:text-sm leading-relaxed line-clamp-2">{ex.description}</p>
                  </div>
                  <div className="flex items-center gap-4 mt-4 sm:mt-0">
                    <a href={`https://www.youtube.com/results?search_query=${ex.name}+form`} target="_blank" rel="noreferrer" 
                         className="w-10 h-10 rounded-full border border-white/10 flex items-center justify-center hover:bg-white hover:text-black transition-all">
                      <span className="text-[10px]">▶</span>
                    </a>
                    <button onClick={() => handleStandardReplace(ex, i)} className="p-2 bg-white/5 rounded-full hover:bg-white/10 transition-all rotate-0 hover:rotate-180">
                      🔄
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export default App;