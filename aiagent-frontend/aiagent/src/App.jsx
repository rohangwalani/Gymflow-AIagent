import { useState, useEffect, useRef, useCallback, memo } from "react";
import { Send, Activity, Mic, MicOff, Volume2, VolumeX, RefreshCw } from 'lucide-react'; 
import "./App.css";

// --- API CONFIGURATION ---
const AGENT_API_URL = import.meta.env.VITE_AGENT_API_URL || "https://gymflow-aiagent.onrender.com/api/v1/agent/chat";

// --- ADVANCED AI AGENT THEME ---
const ADVANCED_ANATOMY_THEME = {
  'Chest': '#ff00d4', 'Back': '#008cff', 'Shoulders': '#ff5e00', 'Biceps': '#00ffd0', 'Triceps': '#00ffcc',
  'Quads': '#ff3e3e', 'Hamstrings': '#ffaa00', 'Calves': '#aaff00', 'Glutes': '#d400ff',
  'Abs': '#00f3ff', 'Cardio': '#00ff8c', 'Rest': '#555555'
};

const getThemeColor = (exerciseName) => {
  if (!exerciseName) return ADVANCED_ANATOMY_THEME.Rest;
  const name = exerciseName.toLowerCase();
  if (name.includes("squat") || name.includes("leg press") || name.includes("extension")) return ADVANCED_ANATOMY_THEME.Quads;
  if (name.includes("deadlift") || name.includes("rdl") || name.includes("curl")) return ADVANCED_ANATOMY_THEME.Hamstrings;
  if (name.includes("bench") || name.includes("chest") || name.includes("pushup")) return ADVANCED_ANATOMY_THEME.Chest;
  if (name.includes("row") || name.includes("pull") || name.includes("lat")) return ADVANCED_ANATOMY_THEME.Back;
  if (name.includes("shoulder") || name.includes("overhead")) return ADVANCED_ANATOMY_THEME.Shoulders;
  return '#00f3ff';
};

const AgentWorkoutCard = memo(({ ex, handleAiReplace }) => {
  const themeColor = getThemeColor(ex.exercise);
  return (
    <div className="workout-card p-6 bg-[#111] border border-white/5 rounded-2xl relative overflow-hidden" style={{"--subgroup-color": themeColor}}>
      <div className="card-accent" style={{background: "var(--subgroup-color)"}}></div>
      <div className="flex justify-between items-start">
        <div className="z-10">
          <h3 className="text-xl font-black text-white">{ex.exercise}</h3>
          <p className="text-[#00f3ff] text-xs font-bold mt-1 uppercase">{ex.sets} X {ex.reps}</p>
          <p className="text-gray-500 text-xs mt-2 italic">"{ex.notes}"</p>
        </div>
        <button onClick={() => handleAiReplace(ex.exercise)} className="p-2 bg-white/5 rounded-full hover:bg-white/10 transition-all">
          <RefreshCw className="w-4 h-4 text-gray-400" />
        </button>
      </div>
    </div>
  );
});

function App() {
  const [messages, setMessages] = useState([{ role: 'assistant', content: "SYSTEM ONLINE. I am the GymFlow Agent. How can I assist with your training protocol today?" }]);
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
    chatEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, isAgentThinking]);

  const speakText = (text) => {
    if (isMuted || !text) return;
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    window.speechSynthesis.speak(utterance);
  };

  const processAgentMessage = async (messageText) => {
    setMessages(prev => [...prev, { role: 'user', content: messageText }]);
    setIsAgentThinking(true);
    try {
      const response = await fetch(AGENT_API_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId, message: messageText, apiKey: "gymflow-secret-connect-2026" })
      });
      const data = await response.json();
      setMessages(prev => [...prev, { role: 'assistant', content: data.coach_message, routine: data.routine || [] }]);
      speakText(data.coach_message);
    } catch (error) {
      setMessages(prev => [...prev, { role: 'system', content: "CRITICAL FAILURE: Cloud Uplink Severed." }]);
    } finally {
      setIsAgentThinking(false);
    }
  };

  const startListening = () => {
    if (!('webkitSpeechRecognition' in window)) return alert("Voice not supported.");
    const recognition = new window.webkitSpeechRecognition();
    recognition.onstart = () => setIsListening(true);
    recognition.onend = () => setIsListening(false);
    recognition.onresult = (event) => setInput(event.results[0][0].transcript);
    recognition.start();
  };

  const handleAiReplace = (name) => processAgentMessage(`Replace "${name}"`);

  return (
    <div className="fixed inset-0 bg-[#050505] text-white flex flex-col font-mono overflow-hidden">
      <div className="bg-glow opacity-50" /><div className="grid-overlay opacity-30" />
      
      <header className="relative z-50 flex justify-between items-center py-6 px-10 border-b border-white/10 bg-[#050505]/95 backdrop-blur-xl">
        <h1 className="text-lg font-black tracking-[4px] uppercase">GYM<span className="text-[#00f3ff]">FLOW</span> <span className="text-gray-700">AGENT</span></h1>
        <div className="flex gap-4">
           <button onClick={() => setIsMuted(!isMuted)}>{isMuted ? <VolumeX className="text-gray-500" /> : <Volume2 className="text-[#00f3ff]" />}</button>
        </div>
      </header>

      <main className="flex-1 overflow-y-auto p-10 flex flex-col gap-10 pb-40 relative z-10">
        {messages.map((msg, idx) => (
          <div key={idx} className={`flex flex-col ${msg.role === 'user' ? 'items-end' : 'items-start'} w-full animate-enter`}>
            <div className={`p-6 rounded-[24px] max-w-[75%] ${msg.role === 'user' ? 'bg-white/5 border border-white/10' : 'bg-[#0a0a0a] border border-[#00f3ff]/30'}`}>
              <p className="text-gray-300 text-sm leading-relaxed">{msg.content}</p>
            </div>
            {msg.routine && (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6 w-full mt-8">
                {msg.routine.map((ex, i) => <AgentWorkoutCard key={i} ex={ex} handleAiReplace={handleAiReplace} />)}
              </div>
            )}
          </div>
        ))}
        {isAgentThinking && <div className="text-[#00f3ff] text-xs animate-pulse font-black uppercase tracking-widest">Analyzing Protocol...</div>}
        <div ref={chatEndRef} />
      </main>

      <div className="fixed bottom-0 left-0 w-full p-6 bg-[#050505]/95 border-t border-white/5 z-[100]">
        <form onSubmit={(e) => { e.preventDefault(); if(input.trim()) { processAgentMessage(input); setInput(""); }}} className="max-w-[1200px] mx-auto flex gap-4">
          <button type="button" onClick={startListening} className={`h-14 w-14 rounded-xl border flex items-center justify-center ${isListening ? 'border-red-500 animate-pulse' : 'border-white/10 text-gray-400'}`}>{isListening ? <MicOff /> : <Mic />}</button>
          <input value={input} onChange={(e) => setInput(e.target.value)} placeholder="TYPE OR SPEAK..." className="flex-1 bg-[#0a0a0a] border border-white/10 text-white px-6 rounded-xl outline-none" />
          <button type="submit" className="bg-[#00f3ff] text-black px-8 rounded-xl font-black uppercase hover:bg-white transition-all"><Send className="w-4 h-4" /></button>
        </form>
      </div>
    </div>
  );
}

export default App;