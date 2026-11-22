import { useEffect, useRef, useState, type FormEvent } from 'react';

type Status = 'AGENT_WORKING' | 'REVIEW_RUNNING' | 'READY_FOR_DEMO' | 'WAITING_FOR_HUMAN';
type Idea = {
  number: number;
  title: string;
  description: string;
  status: Status;
  previewUrl: string | null;
  previewReady: boolean;
};

const statusLabels: Record<Status, string> = {
  AGENT_WORKING: 'Agent arbeitet',
  REVIEW_RUNNING: 'Review läuft',
  READY_FOR_DEMO: 'Bereit für die Demo',
  WAITING_FOR_HUMAN: 'Wartet auf dich',
};

// Each idea is a GitHub issue with the label `agent`. Hidden when GitHub is not configured (503)
// and in a preview app (/api/preview answers there): ideas are submitted in production only.
// A button opens them in a dialog, so they stay apart from the notes.
export function Ideas() {
  const [ideas, setIdeas] = useState<Idea[] | null>(null);
  const [message, setMessage] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [open, setOpen] = useState(false);
  // The idea whose chat is shown; null shows the list of all ideas.
  const [selected, setSelected] = useState<number | null>(null);
  const dialog = useRef<HTMLDialogElement>(null);
  const chat = useRef<HTMLUListElement>(null);

  async function load() {
    const response = await fetch('/api/ideas');
    if (response.status === 503) return;
    if (!response.ok) {
      setError('Ideen konnten nicht geladen werden.');
      return;
    }
    setError(null);
    setIdeas((await response.json()) as Idea[]);
  }

  // Loaded once for the count on the button, polled only while the dialog is open.
  useEffect(() => {
    fetch('/api/preview')
      .then((preview) => {
        if (preview.ok) return;
        load().catch(() => setError('Ideen konnten nicht geladen werden.'));
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    if (!open) return;
    load().catch(() => {});
    const timer = setInterval(() => load().catch(() => {}), 10000);
    return () => clearInterval(timer);
  }, [open]);

  // The newest message is at the bottom, like in a chat.
  useEffect(() => {
    chat.current?.scrollTo({ top: chat.current.scrollHeight });
  }, [ideas, open, selected]);

  if (ideas === null && error === null) return null;

  async function submit(event: FormEvent) {
    event.preventDefault();
    const text = message.trim();
    if (busy || text === '') return;
    setBusy(true);
    setError(null);
    const response = await fetch('/api/ideas', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title: text.split('\n')[0].slice(0, 80), description: text }),
    });
    setBusy(false);
    if (!response.ok) {
      setError('Idee konnte nicht eingereicht werden.');
      return;
    }
    const idea = (await response.json()) as Idea;
    setIdeas((current) => [idea, ...(current ?? [])]);
    setMessage('');
    setSelected(idea.number);
  }

  // An idea that is closed meanwhile (merged) leaves the list; its chat then shows the list again.
  const current = ideas?.find((idea) => idea.number === selected) ?? null;

  return (
    <>
      <button
        type="button"
        className="ideas-button"
        onClick={() => {
          dialog.current?.showModal();
          setOpen(true);
        }}
      >
        Ideen{ideas && ideas.length > 0 && ` (${ideas.length})`}
      </button>
      <dialog ref={dialog} className="ideas" closedby="any" onClose={() => setOpen(false)}>
        <div className="ideas-header">
          {current ? (
            <button type="button" className="icon-button back" onClick={() => setSelected(null)}>
              ← Ideen
            </button>
          ) : (
            <h2>Ideen</h2>
          )}
          <button type="button" className="icon-button" onClick={() => dialog.current?.close()}>
            Schließen
          </button>
        </div>
        {current ? (
          <>
            <h2 className="idea-title">
              #{current.number} {current.title}
            </h2>
            <p className="idea-status">
              <span className={`status ${current.status.toLowerCase()}`}>{statusLabels[current.status]}</span>
              {current.previewUrl && !current.previewReady && (
                <span className="status"> · Preview wird vorbereitet</span>
              )}
            </p>
            <ul className="chat" ref={chat}>
              <li>
                <p className="bubble me">{current.description || current.title}</p>
              </li>
              {current.previewUrl && current.previewReady && (
                <li>
                  <p className="bubble agent">
                    Die Preview ist bereit:{' '}
                    <a href={current.previewUrl} target="_blank" rel="noreferrer">
                      {current.previewUrl}
                    </a>
                  </p>
                </li>
              )}
            </ul>
          </>
        ) : (
          <>
            <p className="hint">Schreib deine Idee als Nachricht: Sie wird ein Issue, ein Agent setzt sie um.</p>
            <ul className="idea-list">
              {(ideas ?? []).map((idea) => (
                <li key={idea.number}>
                  <button type="button" onClick={() => setSelected(idea.number)}>
                    <span>
                      #{idea.number} {idea.title}
                    </span>
                    <span className={`status ${idea.status.toLowerCase()}`}>{statusLabels[idea.status]}</span>
                  </button>
                </li>
              ))}
            </ul>
            {error && <p className="error">{error}</p>}
            <form className="chat-form" onSubmit={submit}>
              <textarea
                value={message}
                onChange={(event) => setMessage(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' && !event.shiftKey) {
                    event.preventDefault();
                    event.currentTarget.form?.requestSubmit();
                  }
                }}
                placeholder="Neue Idee …"
                rows={2}
              />
              <button type="submit" disabled={busy || message.trim() === ''}>
                Senden
              </button>
            </form>
          </>
        )}
      </dialog>
    </>
  );
}
