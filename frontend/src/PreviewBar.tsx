import { useEffect, useState, type FormEvent } from 'react';

type CheckState = 'PENDING' | 'SUCCESS' | 'FAILURE';
type Preview = {
  number: number;
  title: string;
  state: string;
  build: CheckState;
  review: CheckState;
  mergeable: boolean;
};

const checkLabels: Record<CheckState, string> = {
  PENDING: 'läuft',
  SUCCESS: 'grün',
  FAILURE: 'rot',
};

// Only shown in a preview app: there /api/preview knows its pull request, elsewhere it is 404.
export function PreviewBar() {
  const [preview, setPreview] = useState<Preview | null>(null);
  const [text, setText] = useState('');
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function load() {
    const response = await fetch('/api/preview');
    if (response.ok) setPreview((await response.json()) as Preview);
  }

  useEffect(() => {
    load().catch(() => {});
    const timer = setInterval(() => load().catch(() => {}), 15000);
    return () => clearInterval(timer);
  }, []);

  if (!preview) return null;

  async function sendFeedback(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    const response = await fetch('/api/preview/feedback', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text }),
    });
    setBusy(false);
    if (!response.ok) {
      setMessage('Feedback konnte nicht gesendet werden.');
      return;
    }
    setText('');
    setMessage('Feedback gesendet, der Agent arbeitet daran.');
  }

  async function merge() {
    if (!confirm(`Pull Request #${preview?.number} mergen?`)) return;
    setBusy(true);
    const response = await fetch('/api/preview/merge', { method: 'POST' });
    setBusy(false);
    if (!response.ok) {
      setMessage('Merge abgelehnt. Sind build und review grün?');
      await load();
      return;
    }
    setMessage('Gemergt. Die Änderung geht jetzt in Produktion, diese Preview wird entfernt.');
    setPreview({ ...preview!, state: 'merged', mergeable: false });
  }

  return (
    <aside className="preview-bar">
      <strong>
        Preview #{preview.number}: {preview.title}
      </strong>
      <span className="checks">
        <span className={`check ${preview.build.toLowerCase()}`}>
          build {checkLabels[preview.build]}
        </span>
        <span className={`check ${preview.review.toLowerCase()}`}>
          review {checkLabels[preview.review]}
        </span>
        {preview.state !== 'open' && <span>{preview.state === 'merged' ? 'gemergt' : 'geschlossen'}</span>}
      </span>
      {preview.state === 'open' && (
        <form onSubmit={sendFeedback}>
          <textarea
            value={text}
            onChange={(event) => setText(event.target.value)}
            placeholder="Feedback an den Agenten"
            rows={2}
          />
          <div className="actions">
            <button type="submit" disabled={busy || text.trim() === ''}>
              Feedback senden
            </button>
            <button type="button" onClick={merge} disabled={busy || !preview.mergeable}>
              Mergen
            </button>
          </div>
        </form>
      )}
      {message && <p>{message}</p>}
    </aside>
  );
}
