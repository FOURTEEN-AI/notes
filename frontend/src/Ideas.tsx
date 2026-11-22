import { useEffect, useState, type FormEvent } from 'react';

type Status = 'AGENT_WORKING' | 'REVIEW_RUNNING' | 'READY_FOR_DEMO' | 'WAITING_FOR_HUMAN';
type Idea = {
  number: number;
  title: string;
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
export function Ideas() {
  const [ideas, setIdeas] = useState<Idea[] | null>(null);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

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

  useEffect(() => {
    let timer: ReturnType<typeof setInterval> | undefined;
    fetch('/api/preview')
      .then((preview) => {
        if (preview.ok) return;
        load().catch(() => setError('Ideen konnten nicht geladen werden.'));
        timer = setInterval(() => load().catch(() => {}), 30000);
      })
      .catch(() => {});
    return () => clearInterval(timer);
  }, []);

  if (ideas === null && error === null) return null;

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    const response = await fetch('/api/ideas', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title, description }),
    });
    setBusy(false);
    if (!response.ok) {
      setError('Idee konnte nicht eingereicht werden.');
      return;
    }
    const idea = (await response.json()) as Idea;
    setIdeas((current) => [idea, ...(current ?? [])]);
    setTitle('');
    setDescription('');
  }

  return (
    <section className="ideas">
      <h2>Ideen</h2>
      <p className="hint">Eine Idee wird ein Issue, ein Agent setzt sie um.</p>
      <form onSubmit={submit}>
        <input
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          placeholder="Titel"
        />
        <textarea
          value={description}
          onChange={(event) => setDescription(event.target.value)}
          placeholder="Beschreibung"
          rows={4}
        />
        <button type="submit" disabled={busy || title.trim() === ''}>
          Idee einreichen
        </button>
      </form>
      {error && <p className="error">{error}</p>}
      <ul>
        {(ideas ?? []).map((idea) => (
          <li key={idea.number}>
            <p>
              #{idea.number} {idea.title}
            </p>
            <span className={`status ${idea.status.toLowerCase()}`}>{statusLabels[idea.status]}</span>
            {idea.previewUrl && (
              <>
                {' · '}
                {idea.previewReady ? (
                  <a href={idea.previewUrl} target="_blank" rel="noreferrer">
                    Preview
                  </a>
                ) : (
                  <span className="status">Preview wird vorbereitet</span>
                )}
              </>
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}
