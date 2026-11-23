import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Tour, type DemoStep } from './Tour';

type CheckState = 'PENDING' | 'SUCCESS' | 'FAILURE';
type Preview = {
  number: number;
  title: string;
  state: string;
  deployedSha: string | null;
  build: CheckState;
  review: CheckState;
  acceptance: CheckState;
  mergeable: boolean;
  demo: DemoStep[];
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
  // The commit that served this page. Once another one is deployed, the bar offers a reload.
  const loadedSha = useRef<string | null>(null);
  const feedbackField = useRef<HTMLTextAreaElement>(null);

  async function load() {
    const response = await fetch('/api/preview');
    if (!response.ok) return;
    const current = (await response.json()) as Preview;
    loadedSha.current ??= current.deployedSha;
    setPreview(current);
  }

  useEffect(() => {
    load().catch(() => {});
    const timer = setInterval(() => load().catch(() => {}), 10000);
    return () => clearInterval(timer);
  }, []);

  if (!preview) return null;

  const newVersion = preview.deployedSha !== loadedSha.current;

  // "Passt nicht" in the tour: start the feedback with the step, the human adds what happened.
  function prefillFeedback(prefill: string) {
    setText((current) => (current.trim() === '' ? prefill : `${current.trimEnd()}\n\n${prefill}`));
    feedbackField.current?.focus();
  }

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
      setMessage('Merge abgelehnt. Sind build, review und acceptance grün?');
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
        <span className={`check ${preview.acceptance.toLowerCase()}`}>
          acceptance {checkLabels[preview.acceptance]}
        </span>
        {preview.state !== 'open' && <span>{preview.state === 'merged' ? 'gemergt' : 'geschlossen'}</span>}
      </span>
      {newVersion && (
        <p>
          Neue Version bereit.{' '}
          <button type="button" onClick={() => location.reload()}>
            Neu laden
          </button>
        </p>
      )}
      {preview.state === 'open' && loadedSha.current && (
        <Tour steps={preview.demo} sha={loadedSha.current} onMismatch={prefillFeedback} />
      )}
      {preview.state === 'open' && (
        <form onSubmit={sendFeedback}>
          <textarea
            ref={feedbackField}
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
