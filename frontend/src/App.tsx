import { useEffect, useState, type FormEvent } from 'react';
import { Ideas } from './Ideas';
import { PreviewBar } from './PreviewBar';

type Note = { id: number; text: string; createdAt: string };

export function App() {
  const [notes, setNotes] = useState<Note[]>([]);
  const [text, setText] = useState('');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetch('/api/notes')
      .then((response) => {
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return response.json() as Promise<Note[]>;
      })
      .then(setNotes)
      .catch(() => setError('Notizen konnten nicht geladen werden.'));
  }, []);

  async function save(event: FormEvent) {
    event.preventDefault();
    setError(null);
    const response = await fetch('/api/notes', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text }),
    });
    if (!response.ok) {
      setError('Notiz konnte nicht gespeichert werden.');
      return;
    }
    const note = (await response.json()) as Note;
    setNotes([note, ...notes]);
    setText('');
  }

  async function remove(id: number) {
    setError(null);
    const response = await fetch(`/api/notes/${id}`, { method: 'DELETE' });
    if (!response.ok && response.status !== 404) {
      setError('Notiz konnte nicht gelöscht werden.');
      return;
    }
    setNotes((current) => current.filter((note) => note.id !== id));
  }

  return (
    <>
      <PreviewBar />
      <main>
        <header>
          <h1>Notizen</h1>
          <Ideas />
        </header>
        <form onSubmit={(event) => void save(event)}>
          <textarea
            value={text}
            onChange={(event) => setText(event.target.value)}
            placeholder="Neue Notiz"
            rows={4}
          />
          <button type="submit" disabled={text.trim() === ''}>
            Speichern
          </button>
        </form>
        {error && <p className="error">{error}</p>}
        <ul>
          {notes.map((note) => (
            <li key={note.id}>
              <time dateTime={note.createdAt}>
                {new Date(note.createdAt).toLocaleString('de-DE')}
              </time>
              <p>{note.text}</p>
              <button type="button" className="icon-button" onClick={() => void remove(note.id)}>
                <svg
                  width="18"
                  height="18"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  aria-hidden="true"
                >
                  <path d="M3 6h18" />
                  <path d="M8 6V4h8v2" />
                  <path d="M19 6l-1 14H6L5 6" />
                  <path d="M10 11v6M14 11v6" />
                </svg>
                Löschen
              </button>
            </li>
          ))}
        </ul>
      </main>
    </>
  );
}
