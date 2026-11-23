import { useEffect, useRef, useState, type FormEvent, type RefObject } from 'react';

type Status =
  | 'REFINING'
  | 'WAITING_FOR_ANSWER'
  | 'AGENT_WORKING'
  | 'REVIEW_RUNNING'
  | 'READY_FOR_DEMO'
  | 'WAITING_FOR_HUMAN';
type Message = { author: 'me' | 'agent'; text: string };
type Scenario = { title: string; steps: string[] };
type Idea = {
  number: number;
  title: string;
  description: string;
  status: Status;
  previewUrl: string | null;
  previewReady: boolean;
};

const statusLabels: Record<Status, string> = {
  REFINING: 'Refine-Agent arbeitet',
  WAITING_FOR_ANSWER: 'Rückfrage an dich',
  AGENT_WORKING: 'Agent arbeitet',
  REVIEW_RUNNING: 'Review läuft',
  READY_FOR_DEMO: 'Bereit für die Demo',
  WAITING_FOR_HUMAN: 'Wartet auf dich',
};

// One input at the bottom of a chat: Enter sends, Shift+Enter adds a line. The text stays if
// sending fails.
function MessageForm({
  placeholder,
  onSend,
}: {
  placeholder: string;
  onSend: (text: string) => Promise<boolean>;
}) {
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const trimmed = text.trim();
    if (busy || trimmed === '') return;
    setBusy(true);
    if (await onSend(trimmed)) setText('');
    setBusy(false);
  }

  return (
    <form className="chat-form" onSubmit={(event) => void submit(event)}>
      <textarea
        value={text}
        onChange={(event) => setText(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault();
            event.currentTarget.form?.requestSubmit();
          }
        }}
        placeholder={placeholder}
        rows={2}
      />
      <button type="submit" disabled={busy || text.trim() === ''}>
        Senden
      </button>
    </form>
  );
}

// The old versions of the scenarios that the last proposal changes or removes.
function PreviousScenarios({ scenarios }: { scenarios: Scenario[] }) {
  if (scenarios.length === 0) return null;
  return (
    <details className="previous" open>
      <summary>Bisher (aus dem Report von main)</summary>
      {scenarios.map((scenario) => (
        <section key={scenario.title}>
          <h3>{scenario.title}</h3>
          <ul>
            {scenario.steps.map((step, index) => (
              <li key={index}>{step}</li>
            ))}
          </ul>
        </section>
      ))}
    </details>
  );
}

// Whether the agent's message offers scenarios to release.
function proposes(message: Message | undefined) {
  return message?.author === 'agent' && message.text.includes('```gherkin');
}

function PreviewReady({ current }: { current: Idea }) {
  if (!current.previewUrl || !current.previewReady) return null;
  return (
    <li>
      <p className="bubble agent">
        Die Preview ist bereit:{' '}
        <a href={current.previewUrl} target="_blank" rel="noreferrer">
          {current.previewUrl}
        </a>
      </p>
    </li>
  );
}

// The chat of one idea: its messages, the scenarios of the last proposal, the answer form.
function IdeaChat({
  current,
  messages,
  previous,
  error,
  chat,
  onReply,
  onApprove,
}: {
  current: Idea;
  messages: Message[];
  previous: Scenario[];
  error: string | null;
  chat: RefObject<HTMLUListElement | null>;
  onReply: (text: string) => Promise<boolean>;
  onApprove: () => void;
}) {
  return (
    <>
      <h2 className="idea-title">
        #{current.number} {current.title}
      </h2>
      <p className="idea-status">
        <span className={`status ${current.status.toLowerCase()}`}>
          {statusLabels[current.status]}
        </span>
        {current.previewUrl && !current.previewReady && (
          <span className="status"> · Preview wird vorbereitet</span>
        )}
      </p>
      <ul className="chat" ref={chat}>
        <li>
          <p className="bubble me">{current.description || current.title}</p>
        </li>
        {messages.map((message, index) => (
          <li key={index}>
            <p className={`bubble ${message.author}`}>{message.text}</p>
          </li>
        ))}
        <PreviewReady current={current} />
      </ul>
      <PreviousScenarios scenarios={previous} />
      {error && <p className="error">{error}</p>}
      {current.status === 'WAITING_FOR_ANSWER' && proposes(messages.at(-1)) && (
        <button type="button" className="approve" onClick={onApprove}>
          Freigeben
        </button>
      )}
      {(current.status === 'REFINING' || current.status === 'WAITING_FOR_ANSWER') && (
        <MessageForm placeholder="Antworte dem Refine-Agent …" onSend={onReply} />
      )}
    </>
  );
}

// All ideas, and the form for a new one.
function IdeaList({
  ideas,
  error,
  onSelect,
  onSubmit,
}: {
  ideas: Idea[];
  error: string | null;
  onSelect: (number: number) => void;
  onSubmit: (text: string) => Promise<boolean>;
}) {
  return (
    <>
      <>
        <p className="hint">
          Schreib deine Idee als Nachricht: Sie wird ein Issue, ein Agent setzt sie um.
        </p>
        <ul className="idea-list">
          {ideas.map((idea) => (
            <li key={idea.number}>
              <button type="button" onClick={() => onSelect(idea.number)}>
                <span>
                  #{idea.number} {idea.title}
                </span>
                <span className={`status ${idea.status.toLowerCase()}`}>
                  {statusLabels[idea.status]}
                </span>
              </button>
            </li>
          ))}
        </ul>
        {error && <p className="error">{error}</p>}
        <MessageForm placeholder="Neue Idee …" onSend={onSubmit} />
      </>
    </>
  );
}

// Each idea is a GitHub issue with the label `refine` or `agent`. Hidden when GitHub is not configured (503)
// and in a preview app (/api/preview answers there): ideas are submitted in production only.
// A button opens them in a dialog, so they stay apart from the notes.
export function Ideas() {
  const [ideas, setIdeas] = useState<Idea[] | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [previous, setPrevious] = useState<Scenario[]>([]);
  const [error, setError] = useState<string | null>(null);
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

  // Loaded once for the count on the button, polled only while the dialog is open. `?idee=<n>`
  // (the link in the preview bar) opens the chat of that idea right away.
  useEffect(() => {
    fetch('/api/preview')
      .then((preview) => {
        if (preview.status === 200) return;
        load().catch(() => setError('Ideen konnten nicht geladen werden.'));
        const linked = Number(new URLSearchParams(location.search).get('idee'));
        if (Number.isInteger(linked) && linked > 0) {
          dialog.current?.showModal();
          setOpen(true);
          setSelected(linked);
        }
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    if (!open) return;
    load().catch(() => {});
    const timer = setInterval(() => load().catch(() => {}), 10000);
    return () => clearInterval(timer);
  }, [open]);

  // The messages of the chat that is open, polled like the ideas.
  useEffect(() => {
    setMessages([]);
    setPrevious([]);
    if (!open || selected === null) return;
    async function loadMessages() {
      const response = await fetch(`/api/ideas/${selected}/messages`);
      if (!response.ok) return;
      const loaded = (await response.json()) as Message[];
      setMessages(loaded);
      // The old versions of the scenarios that the last proposal changes or removes.
      const old = proposes(loaded.at(-1))
        ? await fetch(`/api/ideas/${selected}/previous-scenarios`)
        : null;
      setPrevious(old?.ok ? ((await old.json()) as Scenario[]) : []);
    }
    loadMessages().catch(() => {});
    const timer = setInterval(() => loadMessages().catch(() => {}), 10000);
    return () => clearInterval(timer);
  }, [open, selected]);

  // The newest message is at the bottom, like in a chat. Only a new message (or another chat)
  // scrolls: a poll that brings the same messages must not pull the reader down again.
  const ideasLoaded = ideas !== null;
  useEffect(() => {
    chat.current?.scrollTo({ top: chat.current.scrollHeight });
  }, [ideasLoaded, open, selected, messages.length]);

  if (ideas === null && error === null) return null;

  async function submit(text: string) {
    setError(null);
    const response = await fetch('/api/ideas', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title: text.split('\n')[0].slice(0, 80), description: text }),
    });
    if (!response.ok) {
      setError('Idee konnte nicht eingereicht werden.');
      return false;
    }
    const idea = (await response.json()) as Idea;
    setIdeas((current) => [idea, ...(current ?? [])]);
    setSelected(idea.number);
    return true;
  }

  async function reply(text: string) {
    setError(null);
    const response = await fetch(`/api/ideas/${selected}/messages`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text }),
    });
    if (!response.ok) {
      setError('Nachricht konnte nicht gesendet werden.');
      return false;
    }
    const message = (await response.json()) as Message;
    setMessages((current) => [...current, message]);
    // The agent starts now; show it right away instead of waiting for the next poll.
    setIdeas((current) =>
      (current ?? []).map((idea) =>
        idea.number === selected ? { ...idea, status: 'REFINING' } : idea,
      ),
    );
    return true;
  }

  // The button releases the idea with the scenarios of the agent's last message, if it has any.
  async function approve() {
    setError(null);
    const response = await fetch(`/api/ideas/${selected}/approval`, { method: 'POST' });
    if (!response.ok) {
      setError('Idee konnte nicht freigegeben werden.');
      return;
    }
    setIdeas((current) =>
      (current ?? []).map((idea) =>
        idea.number === selected ? { ...idea, status: 'AGENT_WORKING' } : idea,
      ),
    );
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
          <IdeaChat
            current={current}
            messages={messages}
            previous={previous}
            error={error}
            chat={chat}
            onReply={reply}
            onApprove={() =>
              void approve().catch(() => setError('Idee konnte nicht freigegeben werden.'))
            }
          />
        ) : (
          <IdeaList ideas={ideas ?? []} error={error} onSelect={setSelected} onSubmit={submit} />
        )}
      </dialog>
    </>
  );
}
