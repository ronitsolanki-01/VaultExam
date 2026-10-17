"use client";
import { useState } from "react";

const API = process.env.NEXT_PUBLIC_API ?? "http://localhost:8080";
const L = ["A", "B", "C", "D"];

async function post(path: string, body: any) {
  const r = await fetch(API + path, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
  if (!r.ok) throw new Error((await r.json().catch(() => ({}))).message ?? r.statusText);
  return r.json();
}

export default function Home() {
  const [doc, setDoc] = useState<any>(null);
  const [busy, setBusy] = useState("");
  const [err, setErr] = useState("");
  const [q, setQ] = useState("");
  const [ans, setAns] = useState<any>(null);
  const [exam, setExam] = useState<any>(null);
  const [key, setKey] = useState(true);
  const [f, setF] = useState({ instituteName: "Apex Coaching Institute", examType: "Coaching Mock Test", difficulty: "APPLICATION", mcqCount: 5, shortAnswerCount: 3, longAnswerCount: 2, sets: 1 });

  const run = async (label: string, fn: () => Promise<void>) => {
    setBusy(label); setErr("");
    try { await fn(); } catch (e: any) { setErr(e.message); } finally { setBusy(""); }
  };

  const upload = (file: File) => run("Ingesting PDF locally…", async () => {
    const fd = new FormData(); fd.append("file", file);
    const r = await fetch(API + "/api/documents/upload", { method: "POST", body: fd });
    if (!r.ok) throw new Error((await r.json().catch(() => ({}))).message ?? r.statusText);
    setDoc(await r.json()); setExam(null); setAns(null);
  });

  const num = (k: string) => (e: any) => setF({ ...f, [k]: +e.target.value });
  const inp = "border rounded px-2 py-1 w-full";
  const btn = "bg-black text-white rounded px-4 py-2 disabled:opacity-40";

  return (
    <main className="max-w-4xl mx-auto p-6 space-y-6">
      <div className="print:hidden space-y-6">
        <h1 className="text-2xl font-bold">🔒 VaultExam AI <span className="text-sm font-normal text-gray-500">100% local · nothing leaves this machine</span></h1>
        {err && <p className="bg-red-100 text-red-800 p-2 rounded">{err}</p>}
        {busy && <p className="bg-yellow-100 p-2 rounded animate-pulse">{busy}</p>}

        <section className="border rounded p-4 space-y-2">
          <h2 className="font-semibold">1. Upload PDF</h2>
          <input type="file" accept="application/pdf" onChange={(e) => e.target.files?.[0] && upload(e.target.files[0])} />
          {doc && <p className="text-sm text-green-700">{doc.fileName} · {doc.totalPages} pages · {doc.chunksStored} chunks</p>}
        </section>

        {doc && <>
          <section className="border rounded p-4 space-y-2">
            <h2 className="font-semibold">2. Ask the document</h2>
            <div className="flex gap-2">
              <input className={inp} value={q} onChange={(e) => setQ(e.target.value)} placeholder="Ask a question…" />
              <button className={btn} disabled={!q || !!busy} onClick={() => run("Thinking…", async () => setAns(await post("/api/chat/ask", { documentId: doc.documentId, question: q })))}>Ask</button>
            </div>
            {ans && <div className="bg-gray-50 p-3 rounded"><p className="whitespace-pre-wrap">{ans.answer}</p><p className="text-sm text-gray-500 mt-2">Pages: {ans.sourcePages.join(", ")}</p></div>}
          </section>

          <section className="border rounded p-4 space-y-3">
            <h2 className="font-semibold">3. Generate exam</h2>
            <div className="grid grid-cols-2 gap-3 text-sm">
              <label>Institute<input className={inp} value={f.instituteName} onChange={(e) => setF({ ...f, instituteName: e.target.value })} /></label>
              <label>Exam type<select className={inp} value={f.examType} onChange={(e) => setF({ ...f, examType: e.target.value })}>
                {["Coaching Mock Test", "University Mid-Sem", "Corporate SOP Quiz"].map((x) => <option key={x}>{x}</option>)}</select></label>
              <label>Difficulty<select className={inp} value={f.difficulty} onChange={(e) => setF({ ...f, difficulty: e.target.value })}>
                {["RECALL", "APPLICATION", "ANALYTICAL_EDGE_CASES"].map((x) => <option key={x}>{x}</option>)}</select></label>
              <label>Sets<select className={inp} value={f.sets} onChange={num("sets")}><option value={1}>1 (Set A)</option><option value={2}>2 (A/B)</option><option value={3}>3 (A/B/C)</option></select></label>
              <label>MCQs<input type="number" min={0} className={inp} value={f.mcqCount} onChange={num("mcqCount")} /></label>
              <label>Short answers<input type="number" min={0} className={inp} value={f.shortAnswerCount} onChange={num("shortAnswerCount")} /></label>
              <label>Long answers<input type="number" min={0} className={inp} value={f.longAnswerCount} onChange={num("longAnswerCount")} /></label>
            </div>
            <div className="flex items-center gap-4">
              <button className={btn} disabled={!!busy} onClick={() => run("Generating exam (can take a minute on local hardware)…", async () => setExam(await post("/api/exams/generate", { documentId: doc.documentId, ...f })))}>Generate</button>
              {exam && <>
                <label className="text-sm"><input type="checkbox" checked={key} onChange={(e) => setKey(e.target.checked)} /> Include answer key</label>
                <button className={btn} onClick={() => window.print()}>Print / Save PDF</button>
              </>}
            </div>
          </section>
        </>}
      </div>

      {exam && exam.papers.map((p: any) => (
        <article key={p.setLabel} className="space-y-4 break-after-page border rounded p-6 print:border-0">
          <header className="text-center border-b pb-2">
            <h2 className="text-xl font-bold">{exam.instituteName}</h2>
            <p>{exam.examType} — Set {p.setLabel}</p>
            <p className="text-sm">Time: {exam.durationMinutes} min · Max marks: {exam.totalMarks}</p>
          </header>
          {p.mcqs.length > 0 && <section><h3 className="font-semibold">Section A — MCQ ({exam.mcqMarks} mark each)</h3>
            <ol className="list-decimal ml-6 space-y-2">{p.mcqs.map((m: any, i: number) => <li key={i}>{m.question}
              <ul className="ml-4">{m.options.map((o: string, j: number) => <li key={j}>({L[j]}) {o}</li>)}</ul></li>)}</ol></section>}
          {p.shortAnswers.length > 0 && <section><h3 className="font-semibold">Section B — Short Answer ({exam.shortMarks} marks each)</h3>
            <ol className="list-decimal ml-6 space-y-2">{p.shortAnswers.map((s: any, i: number) => <li key={i}>{s.question}</li>)}</ol></section>}
          {p.longAnswers.length > 0 && <section><h3 className="font-semibold">Section C — Long Answer ({exam.longMarks} marks each)</h3>
            <ol className="list-decimal ml-6 space-y-2">{p.longAnswers.map((s: any, i: number) => <li key={i}>{s.question}</li>)}</ol></section>}

          {key && <section className="break-before-page border-t pt-4 space-y-3">
            <h3 className="font-bold">Answer Key — Set {p.setLabel}</h3>
            {p.mcqs.length > 0 && <p><b>MCQ:</b> {p.mcqs.map((m: any, i: number) => `${i + 1}-${L[m.correctIndex]}`).join(", ")}</p>}
            {p.mcqs.map((m: any, i: number) => <p key={i} className="text-sm">Q{i + 1}: {m.explanation} <i>(p.{m.sourcePage})</i></p>)}
            {p.shortAnswers.map((s: any, i: number) => <p key={i} className="text-sm"><b>B{i + 1}:</b> {s.modelAnswer} <i>(p.{s.sourcePage})</i></p>)}
            {p.longAnswers.map((s: any, i: number) => <div key={i} className="text-sm"><b>C{i + 1}:</b> {s.modelAnswer}
              <ul className="list-disc ml-6">{s.keyPoints?.map((k: string, j: number) => <li key={j}>{k}</li>)}</ul></div>)}
          </section>}
        </article>
      ))}
    </main>
  );
}
